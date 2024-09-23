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
package se.isselab.testcasepropagation.intelliJ;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FileFinder {
    private Project project;
    private VirtualFile baseDir;

    public FileFinder() {
        project = ProjectManager.getInstance().getOpenProjects()[0]; // Assumes there is at least one open project
        if (project == null) {
            System.out.println("No open project found.");
        }

        // Get the base directory of the project
        baseDir = project.getBaseDir();
        if (baseDir == null) {
            System.out.println("Project base directory not found.");
        }
    }

    public VirtualFile findFileRecursively(String fileName) {
        return getVirtualFile(fileName, baseDir);
    }

    public VirtualFile findTestFileRecursively(String fileName) {
        return getVirtualFileTest(fileName, baseDir);
    }

    @Nullable
    private VirtualFile getVirtualFile(String fileName, VirtualFile baseDir) {
        for (VirtualFile file : baseDir.getChildren()) {
            if (file.isDirectory()) {
                // Recursively search in subdirectories
                VirtualFile foundFile = findFileRecursively(file, fileName);
                if (foundFile != null) {
                    return foundFile;
                }
            } else if (getFileNameWithoutExtension(file).equalsIgnoreCase(fileName)) {
                // File with the matching name found, ignoring the extension
                return file;
            }
        }
        return null; // File not found in this directory
    }

    @Nullable
    private VirtualFile getVirtualFileTest(String fileName, VirtualFile baseDir) {
        for (VirtualFile file : baseDir.getChildren()) {
            if (file.isDirectory()) {
                // Recursively search in subdirectories
                VirtualFile foundFile = findFileRecursively(file, fileName);
                if (foundFile != null) {
                    return foundFile;
                }
            } else {
                // Check if the file name contains both fileName and "test" (case-insensitive)
                String fileNameWithoutExtension = getFileNameWithoutExtension(file).toLowerCase();
                if (fileNameWithoutExtension.contains(fileName.toLowerCase()) && fileNameWithoutExtension.contains("test")) {
                    return file;
                }
            }
        }
        return null; // File not found in this directory
    }

    public VirtualFile findFileRecursively(VirtualFile directory, String fileName) {
        return getVirtualFile(fileName, directory);
    }

    private String getFileNameWithoutExtension(VirtualFile file) {
        // Extracts the file name without its extension
        String name = file.getName();
        int lastDotIndex = name.lastIndexOf('.');
        if (lastDotIndex > 0) { // Ensure there's at least one character before the dot
            return name.substring(0, lastDotIndex);
        }
        return name; // Return the name as is if there's no extension
    }

    public String getFileContent(String fileName) {
        VirtualFile file = findFileRecursively(fileName);
        if (file != null && file.isValid()) {
            try {
                return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("Error reading file content: " + e.getMessage());
            }
        }
        return null; // File not found or invalid
    }

    public String getTestFileContent(String fileName) {
        VirtualFile file = findTestFileRecursively(fileName);
        if (file != null && file.isValid()) {
            try {
                return new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.out.println("Error reading file content: " + e.getMessage());
            }
        }
        return null; // File not found or invalid
    }
}
