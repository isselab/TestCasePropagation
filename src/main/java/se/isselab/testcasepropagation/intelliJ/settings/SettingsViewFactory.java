/*
Copyright 2024 Luca Kramer

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package se.isselab.testcasepropagation.intelliJ.settings;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import se.isselab.testcasepropagation.Pipeline;
import se.isselab.testcasepropagation.codeCollection.GitHub;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

public class SettingsViewFactory implements ToolWindowFactory {
    private JLabel forkLabel;
    private JLabel testFiles;
    private JLabel testFunctions;
    private JLabel testPropagation;
    private static SettingsViewFactory instance;
    private Project currentProject;
    private JButton startFetch;
    private JButton startPropagate;
    private Pipeline pipeline;


    public SettingsViewFactory() {
        instance = this;
        pipeline = new Pipeline();
    }

    public static SettingsViewFactory getInstance() {
        return instance;
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        currentProject = project;
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel inputLabel = new JLabel("Enter Repository in format 'User/Project':");
        inputLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(inputLabel);

        // Add a text field
        JTextField repositoryUrlField = new JTextField();
        repositoryUrlField.setMaximumSize(new Dimension(300, 30)); // Set the size of the text field
        repositoryUrlField.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(repositoryUrlField);

        startFetch = new JButton("Start fetching");
        startFetch.setAlignmentX(Component.CENTER_ALIGNMENT);
        startFetch.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String repositoryUrl = repositoryUrlField.getText();
                if (repositoryUrl != null && repositoryUrl.contains("/") && repositoryUrl.indexOf("/") == repositoryUrl.lastIndexOf("/")) {
                    // If the format is correct, call the fetch action
                    fetchButtonAction(repositoryUrl);
                } else {
                    // Show dialog if the format is incorrect
                    JOptionPane.showMessageDialog(panel, "Repository in wrong format", "Invalid Repository", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        panel.add(startFetch);

        forkLabel = new JLabel("Found forks: 0");
        forkLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(forkLabel);

        testFiles = new JLabel("Found test files: 0");
        testFiles.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(testFiles);

        testFunctions = new JLabel("Found tests for existing classes: 0");
        testFunctions.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(testFunctions);

        testPropagation = new JLabel("Possible tests for propagation: 0");
        testPropagation.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(testPropagation);

        startPropagate = new JButton("Start Propagation"); // New button
        startPropagate.setAlignmentX(Component.CENTER_ALIGNMENT);
        startPropagate.setEnabled(false); // Initially inactive
        startPropagate.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Action for the new button
                startPropagation();
            }
        });
        panel.add(startPropagate);

        toolWindow.getComponent().add(panel);
    }

    private void startPropagation() {
        pipeline.testPropagation(true);
    }

    public void updateForkLabel(int forkCount) {
        forkLabel.setText("Found forks: " + forkCount);
    }

    public void updateTestFilesLabel(int testFile) {
        testFiles.setText("Found test files: " + testFile);
    }

    public void updateTestsLabel(int tests) {
        testFunctions.setText("Found tests for existing classes: " + tests);
    }

    public void updateTestPropagationLabel(int tests) {
        testPropagation.setText("Possible tests for propagation: " + tests);
    }

    public void enableFetchButton(boolean enable) {
        if (startFetch != null) {
            startFetch.setEnabled(enable);
        }
    }

    public void enablePropagationButton(boolean enable) {
        if (startPropagate != null) {
            startPropagate.setEnabled(enable);
        }
    }

    private void fetchButtonAction(String repository) {
        ProgressManager.getInstance().run(new Task.Backgroundable(currentProject, "Running Test Button Action") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                pipeline.fetchPipeline(repository);
                SwingUtilities.invokeLater(() -> {

                });
            }
        });
    }

    public void fetchTestFiles(String fork) {
        TestCasePropagationSettings settings = TestCasePropagationSettings.getInstance();
        String accessToken = settings.getGithubApiKey();

        // Construct the search query
        String searchQuery = "test+in:path+repo:" + fork;

        // GitHub API endpoint for searching repository contents
        String apiUrl = "https://api.github.com/search/code?q=" + searchQuery;

        // Create HttpClient instance
        HttpClient httpClient = HttpClients.createDefault();
        HttpGet request = new HttpGet(apiUrl);
        request.addHeader("Authorization", "Bearer " + accessToken);
        request.addHeader("Accept", "application/vnd.github.v3+json");

        try {
            // Execute request
            HttpResponse response = httpClient.execute(request);

            // Get response entity
            HttpEntity entity = response.getEntity();
            String responseBody = EntityUtils.toString(entity);

            System.out.println(responseBody);

            // Parse JSON response
            JSONObject jsonResponse = new JSONObject(responseBody);
            JSONArray itemsArray = jsonResponse.getJSONArray("items");

            System.out.println(itemsArray);
//            for (int i = 0; i < itemsArray.length(); i++) {
//                JSONObject item = itemsArray.getJSONObject(i);
//                String fileName = item.getString("name");
//                System.out.println("Test file found: " + fileName);
//                // You can further process the test file information here
//            }
        } catch (IOException | org.json.JSONException e) {
            e.printStackTrace();
            // Handle exceptions appropriately
        }
    }

}

