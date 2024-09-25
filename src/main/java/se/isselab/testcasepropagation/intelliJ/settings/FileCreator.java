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

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.openapi.fileTypes.FileTypeManager;

public class FileCreator {
    private final Project project;

    public FileCreator(Project project) {
        this.project = project;
    }

    public void createTestFileInProjectRoot(String testedClass) {
        // Get the base directory (root directory) of the project
        VirtualFile projectBaseDir = project.getBaseDir();

        // Get the PsiDirectory from the VirtualFile
        PsiDirectory rootDirectory = PsiManager.getInstance(project).findDirectory(projectBaseDir);
        if (rootDirectory == null) {
            System.out.println("Could not find the root directory.");
            return;
        }

        // Define the file name and content
        String fileName = testedClass + "_Test.java";
        String content = "import org.junit.Test;\n" +
                "import static org.junit.Assert.assertEquals;\n\n" +
                "public class " + testedClass + "_Test {\n\n" +
                "}";

        // Ensure the file doesn't already exist in the root directory
        PsiFile existingFile = rootDirectory.findFile(fileName);
        if (existingFile != null) {
            System.out.println("File " + fileName + " already exists in the project root.");
            return;
        }

        // Create the test file in the IntelliJ project root directory
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiFileFactory fileFactory = PsiFileFactory.getInstance(project);
            // Use FileTypeManager to identify the correct file type for a .java file
            PsiFile testFile = fileFactory.createFileFromText(fileName,
                    FileTypeManager.getInstance().getFileTypeByExtension("java"),
                    content);

            // Add the file to the root directory
            rootDirectory.add(testFile);
            System.out.println("Test file " + fileName + " created successfully in the project root.");
        });
    }
}