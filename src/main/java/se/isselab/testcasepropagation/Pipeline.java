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

import com.intellij.openapi.vfs.VirtualFile;
import se.isselab.testcasepropagation.codeCollection.GitHub;

import java.io.*;
import java.util.*;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import se.isselab.testcasepropagation.codeDuplication.ClassFileFinder;
import se.isselab.testcasepropagation.codeDuplication.CodeDuplicationDetector;
import se.isselab.testcasepropagation.codeDuplication.TestedFunctionExtractor;
import se.isselab.testcasepropagation.helper.PropagationElement;
import se.isselab.testcasepropagation.intelliJ.FileFinder;
import se.isselab.testcasepropagation.intelliJ.settings.SettingsViewFactory;
import se.isselab.testcasepropagation.intelliJ.settings.TestCasePropagationSettings;
import se.isselab.testcasepropagation.intelliJ.visualize.CodeDifferenceViewer;

public class Pipeline {
    private final GitHub github;
    private final Project project;
    private final List<PropagationElement> propagationQueue = new ArrayList<>();
    private final CodeDifferenceViewer codeDifferenceViewer;
    private final SettingsViewFactory settingsViewFactory;


    public Pipeline(){
        TestCasePropagationSettings settings = TestCasePropagationSettings.getInstance();
        this.github = new GitHub(settings.getGithubApiKey());
        this.project = ProjectManager.getInstance().getOpenProjects()[0];
        this.codeDifferenceViewer = new CodeDifferenceViewer(this);
        this.settingsViewFactory = SettingsViewFactory.getInstance();
    }

    public void fetchPipeline(String repositoryInput){

        disableFetchButton();

        FileFinder fileFinder = new FileFinder();

        int testFileCounter = 0;
        int testFunctionCounter = 0;
        int possibleTests = 0;


        // Step 1: Fetch all forks
        List<String[]> forks = gatherAllForks(repositoryInput);
        updateForkLabel(forks.size());

        for(String[] fork : forks){
            // Step 2: Get all file paths in the fork
            List<String> filePaths = github.fetchAllFilePaths(fork[0]);

            for (String filePath : filePaths) {
                if (filePath.endsWith(".java") && filePath.toLowerCase().contains("test")) {
                    // Step 3: Fetch content of test files
                    testFileCounter++;
                    String testFileContent = github.fetchFileContent(fork[0], filePath);

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

        updateUIAfterFetch(testFileCounter, testFunctionCounter, possibleTests);
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

    private List<String[]> gatherAllForks(String repository) {
        List<String[]> forks = github.fetchForks(repository);
        String[] parent = github.fetchForkedOff(repository);
        if (parent != null) {
            forks.add(parent);
            List<String[]> parentForks = github.fetchForks(parent[0]);
            if (!parentForks.isEmpty()) forks.addAll(parentForks);
        }
        return forks;
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

    private void updateUIAfterFetch(int testFileCounter, int testFunctionCounter, int possibleTests) {
        if (settingsViewFactory != null) {
            settingsViewFactory.updateTestFilesLabel(testFileCounter);
            settingsViewFactory.updateTestsLabel(testFunctionCounter);
            settingsViewFactory.updateTestPropagationLabel(possibleTests);
            settingsViewFactory.enablePropagationButton(true);
            settingsViewFactory.enableFetchButton(true);
        }
    }

    private void disableFetchButton(){
        if (settingsViewFactory != null) {
            settingsViewFactory.enableFetchButton(false);
        }
    }

    private void updateForkLabel(int count){
        if (settingsViewFactory != null) settingsViewFactory.updateForkLabel(count);
    }
}
