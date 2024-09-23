/*
Copyright [2024] [Luca Kramer]

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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TestCasePropagationSettingsConfigurable implements Configurable {
    private JTextField githubApiKeyField;
    private JPanel myMainPanel;
    private JPanel githubPanel;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Test Case Propagation Settings";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        myMainPanel = new JPanel();
        myMainPanel.setLayout(new BoxLayout(myMainPanel, BoxLayout.Y_AXIS));

        githubPanel = new JPanel();
        githubPanel.setLayout(new BoxLayout(githubPanel, BoxLayout.Y_AXIS));
        githubPanel.setBorder(BorderFactory.createTitledBorder("GitHub"));

        JLabel label = new JLabel("GitHub API Key:");
        githubApiKeyField = new JTextField();
        githubApiKeyField.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, githubApiKeyField.getPreferredSize().height)); // Ensuring single line without limiting character count

        githubPanel.add(label);
        githubPanel.add(githubApiKeyField);

        myMainPanel.add(githubPanel);

        return myMainPanel;
    }

    @Override
    public boolean isModified() {
        TestCasePropagationSettings settings = TestCasePropagationSettings.getInstance();
        return !githubApiKeyField.getText().equals(settings.getGithubApiKey());
    }

    @Override
    public void apply() throws ConfigurationException {
        TestCasePropagationSettings settings = TestCasePropagationSettings.getInstance();
        settings.setGithubApiKey(githubApiKeyField.getText());
    }

    @Override
    public void reset() {
        TestCasePropagationSettings settings = TestCasePropagationSettings.getInstance();
        githubApiKeyField.setText(settings.getGithubApiKey());
    }

    @Override
    public void disposeUIResources() {
        myMainPanel = null;
        githubApiKeyField = null;
        githubPanel = null;
    }
}

