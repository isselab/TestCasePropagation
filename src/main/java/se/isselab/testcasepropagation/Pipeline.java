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

package se.isselab.testcasepropagation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import se.isselab.testcasepropagation.codeCollection.GitHub;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import se.isselab.testcasepropagation.codeDuplication.ClassFileFinder;
import se.isselab.testcasepropagation.codeDuplication.CodeDuplicationDetector;
import se.isselab.testcasepropagation.codeDuplication.TestedFunctionExtractor;
import se.isselab.testcasepropagation.helper.PropagationElement;
import se.isselab.testcasepropagation.intelliJ.FileFinder;
import se.isselab.testcasepropagation.intelliJ.FileHandler;
import se.isselab.testcasepropagation.intelliJ.ForkSelectionDialog;
import se.isselab.testcasepropagation.intelliJ.settings.ProjectSettings;
import se.isselab.testcasepropagation.intelliJ.settings.SettingsViewFactory;
import se.isselab.testcasepropagation.intelliJ.settings.TestCasePropagationSettings;
import se.isselab.testcasepropagation.intelliJ.visualize.CodeDifferenceViewer;

import javax.swing.*;

public class Pipeline {
    private final GitHub gh;

    private Project project;
    private ArrayList<PropagationElement> propagationQueue;
    private int possibleTests;
    private CodeDifferenceViewer codeDifferenceViewer;


    public Pipeline(Project project) {
        TestCasePropagationSettings settings = TestCasePropagationSettings.getInstance();
        gh = new GitHub(settings.getGithubApiKey());
        this.project = project;
        propagationQueue = new ArrayList<PropagationElement>();
        codeDifferenceViewer = new CodeDifferenceViewer(this);
    }

    public void fetchPipeline(String repository_input, boolean test){
        String repository = repository_input;
        SettingsViewFactory settingsViewFactory = SettingsViewFactory.getInstance();

        if (settingsViewFactory != null) {
            settingsViewFactory.enableFetchButton(false);
        }

        // TODO: Implement simple UI test
        System.out.println("\nIMPLEMENTATION OF INTEREST\n");

        List<String> availableForks = null;
        try {
            availableForks = gh.fetchForkSelection(repository_input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        //List<String> availableForks = Arrays.asList("one/fork1", "one/fork3", "twelve/fork22", "hundred/fork9", "one/fork10", "one/fork30", "twelve/fork220", "hundred/fork90", "one/fork100", "one/fork300", "twelve/fork2200", "hundred/fork900", "one/fork1000", "one/fork3000", "twelve/fork22000", "hundred/fork9000", "one/fork10000", "one/fork30000", "twelve/fork220000", "hundred/fork90000" );
        System.out.println("Available forks: " + availableForks);

        List<String> selectedForks = selectForksDialog(availableForks);
        if (selectedForks == null) System.out.println("selectedForks == null");
        if (selectedForks == null) {
            if (settingsViewFactory != null) {
                settingsViewFactory.enableFetchButton(true);
            }
            return; // user canceled
        }

        System.out.println("\nPIPELINE CONTINUES\n");

        // TODO: Testing code, remove later
        if (test) {
            if (settingsViewFactory != null) {
                settingsViewFactory.enableFetchButton(true);
            }
            return;
        }

        FileFinder fileFinder = new FileFinder();
        int testFileCounter = 0;
        int testFunctionCounter = 0;
        possibleTests = 0;


        // Step 1: Fetch all forks

        /*
        List<String[]> parentForks = null;
        List<String[]> forks = gh.fetchForks(repository);
        String parent[] = gh.fetchForkedOff(repository);
        if(parent != null){
            parentForks = gh.fetchForks(parent[0]);
            forks.add(parent);
        }
        if(parentForks != null) {
            forks.addAll(parentForks);
        }


        List<String> forks = null;
        try {
            forks = gh.fetchAllForks(repository);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

         */
        if (settingsViewFactory != null && selectedForks != null) {
            settingsViewFactory.updateForkLabel(selectedForks.size());
        }



        // TODO: Move UI implementation here

        for(String fork : selectedForks){
            // Step 2: Get all file paths in the fork
            List<String> filePaths = gh.fetchAllFilePaths(fork);

            for (String filePath : filePaths) {
                if (filePath.endsWith(".java") && filePath.toLowerCase().contains("test")) {
                    // Step 3: Fetch content of test files
                    testFileCounter++;
                    String testFileContent = gh.fetchFileContent(fork, filePath);

                    // Step 4: Extract tested code
                    ClassFileFinder classFileFinder = new ClassFileFinder();
                    Set<String> usedClasses = classFileFinder.extractUsedClassesFromString(testFileContent);

                    for (String usedClass : usedClasses) {
                        // Find the file path of the class being tested
                        if (!doesClassExist(fileFinder, usedClass)) {
                            System.out.println("Tested class " + usedClass + " does not exist in the project.");
                            continue;  // Skip this class if it doesn't exist
                        }

                        String testedFilePath = findTestedFilePath(filePaths, usedClass);

                        if (testedFilePath != null) {
                            // Fetch content of the tested file
                            //String testedFileContent = gh.fetchFileContent(fork[0], testedFilePath);

                            // Extract tested functions
                            TestedFunctionExtractor testedFunctionExtractor = new TestedFunctionExtractor();
                            Map<String, String> testFunctions = testedFunctionExtractor.extractFunctions(testFileContent);

                            for (Map.Entry<String, String> entry : testFunctions.entrySet()) {
                                String testCase = entry.getKey();
                                String testFunction = entry.getValue();
                                testFunctionCounter++;
                                // Step 5: Check if the test case exists in the current project using FileFinder
                                if (!doesTestCaseExist(fileFinder, usedClass, testFunction)) {
                                    String testedFunctionName = testedFunctionExtractor.extractTestedFunctionName(testFunction);
                                    // Step 6: Check if tested function exists in the project using FileFinder
                                    if (doesFunctionExist(fileFinder, testedFilePath, testedFunctionName)) {
                                        possibleTests++;
                                        addPropagationElement(fileFinder, usedClass, testFunction, usedClass);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (settingsViewFactory != null) {
            settingsViewFactory.updateTestFilesLabel(testFileCounter);
        }
        if (settingsViewFactory != null) {
            settingsViewFactory.updateTestsLabel(testFunctionCounter);
        }
        if (settingsViewFactory != null) {
            settingsViewFactory.updateTestPropagationLabel(possibleTests);
        }
        if (settingsViewFactory != null) {
            settingsViewFactory.enablePropagationButton(true);
        }
        if (settingsViewFactory != null) {
            settingsViewFactory.enableFetchButton(true);
        }
    }

    public void testPropagation(boolean viewer){
        if(propagationQueue.isEmpty()){
            SettingsViewFactory settingsViewFactory = SettingsViewFactory.getInstance();
            if (settingsViewFactory != null) {
                settingsViewFactory.enablePropagationButton(false);
            }
            return;
        }
        PropagationElement propagationElement = propagationQueue.get(0);
        VirtualFile virtualFile = propagationElement.getProjectFile();
        String testFunction = propagationElement.getTestCase();
        String testedClass = propagationElement.getTestedClass();
        try {
            codeDifferenceViewer.showDiff(project, virtualFile, testFunction, testedClass, viewer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void removePropagationElement() {
        if (!propagationQueue.isEmpty()) {
            propagationQueue.remove(0); // Remove the element at position 0
        }
    }

    public void updateLabel() {
        SettingsViewFactory settingsViewFactory = SettingsViewFactory.getInstance();
        if (settingsViewFactory != null) {
            settingsViewFactory.updateTestPropagationLabel(propagationQueue.size());
        }
    }

    private String findTestedFilePath(List<String> filePaths, String usedClass) {
        // Iterate through file paths to find the one that matches the class name
        for (String filePath : filePaths) {
            if (filePath.contains(usedClass)) {
                return filePath;
            }
        }
        return null;
    }

    private boolean doesClassExist(FileFinder fileFinder, String className) {
        VirtualFile classFile = fileFinder.findFileRecursively(className);
        return classFile != null && classFile.exists();
    }

    private boolean doesTestCaseExist(FileFinder fileFinder, String testedClass, String testCase) {
        String fileContent = fileFinder.getTestFileContent(testedClass);
        CodeDuplicationDetector codeDuplicationDetector = new CodeDuplicationDetector();
        List<String> functions = codeDuplicationDetector.splitIntoFunctions(fileContent);
        if(functions == null){
            return false;
        }
        for(String function : functions){
            double metric = codeDuplicationDetector.calculateDuplicationMetric(function, testCase);
            if(metric > 0.9) {
                return true;
            }
        }
        return false;
    }

    private boolean doesFunctionExist(FileFinder fileFinder, String testedFilePath, String functionName) {
        int firstDotIndex = testedFilePath.indexOf('.');

        String fileContent = fileFinder.getFileContent(testedFilePath.substring(0, firstDotIndex));
        if(fileContent == null) {
            return false;
        }
        return fileContent.contains(functionName);
    }

    private void addPropagationElement(FileFinder fileFinder, String usedClass, String testFunction, String testedClass){
        VirtualFile testedFile = fileFinder.findTestFileRecursively(usedClass);
        PropagationElement propagationElement = new PropagationElement(testedFile, testFunction, testedClass);
        propagationQueue.add(propagationElement);
    }

    @Nullable
    private List<String> selectForksDialog(List<String> availableForks) {
        final List<String>[] selectedForksWrapper = new List[1];
        final boolean[] canceledWrapper = new boolean[] {false};

        ApplicationManager.getApplication().invokeAndWait(() -> {
            List<String> savedForks = ProjectSettings.getInstance(project).getSelectedForks();

            while (true) {
                ForkSelectionDialog dialog = new ForkSelectionDialog(availableForks, savedForks);
                if (dialog.showAndGet()) {
                    selectedForksWrapper[0] = dialog.getSelectedForks();

                    if (selectedForksWrapper[0].isEmpty()) {
                        int result = Messages.showYesNoDialog(
                                project,
                                "No forks selected. Pipeline will cancel.",
                                "Cancel Pipeline?",
                                Messages.getQuestionIcon()
                        );
                        if (result == Messages.YES) {
                            canceledWrapper[0] = true;
                            return;
                        }
                        // else: retry
                    } else {
                        ProjectSettings.getInstance(project).setSelectedForks(selectedForksWrapper[0]);
                        System.out.println("Selected forks from UI: " + selectedForksWrapper[0]);
                        return;
                    }
                } else {
                    int result = Messages.showYesNoDialog(
                            project,
                            "Fork selection canceled. Pipeline will also cancel.",
                            "Cancel Pipeline?",
                            Messages.getQuestionIcon()
                    );
                    if (result == Messages.YES) {
                        canceledWrapper[0] = true;
                        return;
                    }
                    // else: retry
                }
            }
        });

        if (canceledWrapper[0]) return null;
        return selectedForksWrapper[0];
    }
}
