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
import se.isselab.testcasepropagation.codeCollection.GitLab;


import java.io.*;
import java.util.*;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import se.isselab.testcasepropagation.codeDuplication.ClassFileFinder;
import se.isselab.testcasepropagation.codeDuplication.CodeDuplicationDetector;
import se.isselab.testcasepropagation.codeDuplication.TestedFunctionExtractor;
import se.isselab.testcasepropagation.helper.PropagationElement;
import se.isselab.testcasepropagation.intelliJ.FileFinder;
import se.isselab.testcasepropagation.intelliJ.FileHandler;
import se.isselab.testcasepropagation.intelliJ.settings.SettingsViewFactory;
import se.isselab.testcasepropagation.intelliJ.settings.TestCasePropagationSettings;
import se.isselab.testcasepropagation.intelliJ.visualize.CodeDifferenceViewer;

public class Pipeline {
    private final GitHub gh;
    private final GitLab gl;

    private Project project;
    private ArrayList<PropagationElement> propagationQueue;
    private int possibleTests;
    private CodeDifferenceViewer codeDifferenceViewer;


    public Pipeline(){
        TestCasePropagationSettings settings = TestCasePropagationSettings.getInstance();
        gh = new GitHub(settings.getGithubApiKey());
        gl = new GitLab("");
        project = ProjectManager.getInstance().getOpenProjects()[0];
        propagationQueue = new ArrayList<PropagationElement>();
        codeDifferenceViewer = new CodeDifferenceViewer(this);
    }

    public void fetchPipeline(String repositoryInput, boolean isGitLab) {
        String repository = repositoryInput;
        SettingsViewFactory settingsViewFactory = SettingsViewFactory.getInstance();
    
        if (settingsViewFactory != null) {
            settingsViewFactory.enableFetchButton(false);
        }
    
        FileFinder fileFinder = new FileFinder();
        int testFileCounter = 0;
        int testFunctionCounter = 0;
        possibleTests = 0;
    
        // Step 1: Fetch all forks
        List<String[]> forks = new ArrayList<>();
        if (isGitLab) {
            // For GitLab
            forks = gl.fetchForks(repository);
            String parentProjectId = gl.fetchForkedFrom(repository);
            if (parentProjectId != null) {
                List<String[]> parentForks = gl.fetchForks(parentProjectId);
                forks.addAll(parentForks);
                forks.add(new String[]{parentProjectId});
            }
        } else {
            // For GitHub
            forks = gh.fetchForks(repository);
            String[] parent = gh.fetchForkedOff(repository);
            if (parent != null) {
                List<String[]> parentForks = gh.fetchForks(parent[0]);
                forks.addAll(parentForks);
                forks.add(parent);
            }
        }
    
        if (settingsViewFactory != null) {
            settingsViewFactory.updateForkLabel(forks.size());
        }
    
        // Process each fork
        for (String[] fork : forks) {
            // Step 2: Get all file paths in the fork
            List<String> filePaths;
            if (isGitLab) {
                filePaths = gl.fetchAllFilePaths(fork[0]);
            } else {
                filePaths = gh.fetchAllFilePaths(fork[0]);
            }
    
            for (String filePath : filePaths) {
                // Step 3: Identify test files
                if (filePath.endsWith(".java") && filePath.toLowerCase().contains("test")) {
                    testFileCounter++;
    
                    // Step 4: Fetch content of test files
                    String testFileContent;
                    if (isGitLab) {
                        testFileContent = gl.fetchFileContent(fork[0], filePath);
                    } else {
                        testFileContent = gh.fetchFileContent(fork[0], filePath);
                    }
    
                    // Step 5: Extract used classes from test content
                    ClassFileFinder classFileFinder = new ClassFileFinder();
                    Set<String> usedClasses = classFileFinder.extractUsedClassesFromString(testFileContent);
    
                    for (String usedClass : usedClasses) {
                        // Step 6: Check if the tested class exists in the local project
                        if (!doesClassExist(fileFinder, usedClass)) {
                            // Skip if class doesn't exist in the local project
                            continue;
                        }
    
                        String testedFilePath = findTestedFilePath(filePaths, usedClass);
    
                        if (testedFilePath != null) {
                            // Step 7: Extract test functions
                            TestedFunctionExtractor testedFunctionExtractor = new TestedFunctionExtractor();
                            Map<String, String> testFunctions = testedFunctionExtractor.extractFunctions(testFileContent);
    
                            for (Map.Entry<String, String> entry : testFunctions.entrySet()) {
                                String testCaseName = entry.getKey();
                                String testFunction = entry.getValue();
                                testFunctionCounter++;
    
                                // Step 8: Check if the test case already exists in the local project
                                if (!doesTestCaseExist(fileFinder, usedClass, testFunction)) {
                                    String testedFunctionName = testedFunctionExtractor.extractTestedFunctionName(testFunction);
    
                                    // Step 9: Check if the tested function exists in the local project
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
    
        // Update UI labels and enable buttons
        if (settingsViewFactory != null) {
            settingsViewFactory.updateTestFilesLabel(testFileCounter);
            settingsViewFactory.updateTestsLabel(testFunctionCounter);
            settingsViewFactory.updateTestPropagationLabel(possibleTests);
            settingsViewFactory.enablePropagationButton(true);
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
}
