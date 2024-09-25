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
package se.isselab.testcasepropagation.intelliJ.visualize;

import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.application.ApplicationManager;
import se.isselab.testcasepropagation.Pipeline;
import se.isselab.testcasepropagation.intelliJ.FileFinder;
import se.isselab.testcasepropagation.intelliJ.settings.SettingsViewFactory;
import se.isselab.testcasepropagation.intelliJ.settings.FileCreator;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CodeDifferenceViewer {
    private VirtualFile file1;
    private VirtualFile tempVirtualFile;
    private Pipeline pipeline;
    private JFrame footerFrame;


    public CodeDifferenceViewer(Pipeline pipeline) {
        this.pipeline = pipeline;
    }

    public void showDiff(Project project, VirtualFile virtualFile, String content, String testedClass, boolean viewer) throws IOException {
        // Create a temporary file with the string content
        File tempFile = File.createTempFile("tempFile", ".tmp");
        Files.write(tempFile.toPath(), content.getBytes());

        // Get the virtual file from the temporary file
        tempVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
        file1 = virtualFile;

        if(file1 == null) {
            FileCreator fileCreator = new FileCreator(project);
            fileCreator.createTestFileInProjectRoot(testedClass);
            FileFinder fileFinder = new FileFinder();
            file1 = fileFinder.findTestFileRecursively(testedClass);
        }

        if (tempVirtualFile != null) {
            // Create and show the diff request
            DiffRequest request = DiffRequestFactory.getInstance().createFromFiles(project, file1, tempVirtualFile);

            // Show the diff using DiffManager
            DiffManager.getInstance().showDiff(project, request);
            if(viewer) {
                showFooterActions(); // Display footer actions after showing the diff
            }
        } else {
            // Handle the error if the temporary file could not be found
            throw new IOException("Temporary file could not be created or found.");
        }

        // Clean up the temporary file
        tempFile.deleteOnExit();
    }

    private void showFooterActions() {
        // Create a JFrame to hold buttons for footer actions
        footerFrame = new JFrame("Footer Actions");
        footerFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        footerFrame.setSize(300, 100);

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        JButton propagateButton = new JButton("Propagate");
        propagateButton.addActionListener(e -> onPropagateClicked());
        footerPanel.add(propagateButton);

        JButton nextButton = new JButton("Next");
        nextButton.addActionListener(e -> onNextClicked());
        footerPanel.add(nextButton);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> onCloseClicked());
        footerPanel.add(closeButton);

        footerFrame.add(footerPanel);
        footerFrame.setVisible(true);
    }

    private void onPropagateClicked() {
        if (file1 == null || tempVirtualFile == null) {
            JOptionPane.showMessageDialog(null, "Failed to retrieve files for propagation");
            return;
        }

        try {
            // Read content from the temporary file
            String tempFileContent = new String(tempVirtualFile.contentsToByteArray(), StandardCharsets.UTF_8);

            // Add comment to the content to be inserted
            String comment = "\n\t// Test case propagated by plugin\n\t@Test\n\t";
            String contentToInsert = comment + tempFileContent + "\n";

            // Get the document of the file to modify
            Document document1 = FileDocumentManager.getInstance().getDocument(file1);
            if (document1 != null) {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    String originalContent = document1.getText();
                    int lastBraceIndex = originalContent.lastIndexOf('}');

                    if (lastBraceIndex == -1) {
                        JOptionPane.showMessageDialog(null, "No closing brace found in file: " + file1.getPath());
                        return;
                    }

                    // Insert content before the last closing brace
                    String newContent = new StringBuilder(originalContent)
                            .insert(lastBraceIndex, contentToInsert)
                            .toString();
                    // Update the document with the new content
                    document1.setText(newContent);
                    FileDocumentManager.getInstance().saveDocument(document1);
                });
            } else {
                JOptionPane.showMessageDialog(null, "Failed to load document from file: " + file1.getPath());
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Failed to read content from temporary file: " + e.getMessage());
        }
    }

    private void onNextClicked() {
        // Remove the element from the queue for the Next action
        if (pipeline != null) {
            pipeline.removePropagationElement();
            pipeline.updateLabel();
            pipeline.testPropagation(false);
        }
    }

    private void onCloseClicked() {
        // Close the current diff view
        if (footerFrame != null) {
            footerFrame.dispose(); // Close the footer frame
        }
    }
}
