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
import java.util.stream.Collectors;

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

        int forkTestFilesCount = 0;
        int forkTestMethodsCount = 0;
        int possibleTestCount = 0;


        // Step 1: Fetch all forks
        List<String[]> forks = gatherAllForks(repositoryInput);
        updateForkLabel(forks.size());

        for(String[] fork : forks){
            // Step 2: Get all file paths in the fork
            List<String> forkFilePaths = github.fetchAllFilePaths(fork[0]);
            List<String> forkTestFilePaths = forkFilePaths.stream()
                    .filter(p -> p.endsWith(".java") && p.toLowerCase().contains("test"))
                    .collect(Collectors.toList());

            for (String forkTestFilePath : forkTestFilePaths){
                forkTestFilesCount++;
                if (settingsViewFactory != null) settingsViewFactory.updateTestFilesLabel(forkTestFilesCount);

                // Step 3: Fetch content of test files
                String forkTestFileContent = github.fetchFileContent(fork[0], forkTestFilePath);
                // Step 4: Extract tested code
                Set<String> usedClassesInForkTestFile = new ClassFileFinder().extractUsedClassesFromString(forkTestFileContent);

                for (String usedClassInForkTestFile : usedClassesInForkTestFile){
                    // Find the file path of the class being tested
                    if (!classExistsLocally(fileFinder, usedClassInForkTestFile)) continue; // Skip this class if it doesn't exist locally
                    String forkUUTPath = findForkUUTPath(forkFilePaths, usedClassInForkTestFile);
                    if (forkUUTPath == null) continue;

                    // Fetch content of the tested file
                    //String testedFileContent = gh.fetchFileContent(fork[0], testedFilePath);

                    // Extract tested functions
                    TestedFunctionExtractor extractor = new TestedFunctionExtractor();
                    Map<String, String> forkTestFileMethods = extractor.extractFunctions(forkTestFileContent); // ToDo: What is extracted? Methods of test file or tested functions?

                    for (Map.Entry<String, String> entry : forkTestFileMethods.entrySet()){
                        forkTestMethodsCount++;
                        if (settingsViewFactory != null) settingsViewFactory.updateTestsLabel(forkTestMethodsCount);

                        String forkTestFileMethod = entry.getValue();

                        // Step 5: Check if the test case exists in the current project using FileFinder
                        if (testCaseExistsLocally(fileFinder, usedClassInForkTestFile, forkTestFileMethod)) continue;

                        String usedForkUUTMethodName = extractor.extractTestedFunctionName(forkTestFileMethod);
                        // Step 6: Check if tested function exists in the project using FileFinder
                        if (functionExistsLocally(fileFinder, forkUUTPath, usedForkUUTMethodName)) { // ToDo: why the path and not simply UUT name? !NEVER REACHED IN TEST RUN!
                            possibleTestCount++;
                            if (settingsViewFactory != null) settingsViewFactory.updateTestPropagationLabel(possibleTestCount);
                            addPropagationElement(fileFinder, usedClassInForkTestFile, forkTestFileMethod, usedClassInForkTestFile); // ToDo: Why twice usedClass??
                        }
                    }
                }
            }
        }

        updateUIAfterFetch();
    }

    public void testPropagation(boolean viewer){
        if(propagationQueue.isEmpty()){
            if (settingsViewFactory != null) settingsViewFactory.enablePropagationButton(false);
            return;
        }

        PropagationElement element = propagationQueue.get(0);
        try {
            codeDifferenceViewer.showDiff(project, element.getProjectFile(), element.getTestCase(), element.getTestedClass(), viewer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to display code diff", e);
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
        if (!propagationQueue.isEmpty()) propagationQueue.remove(0); // Remove the element at position 0
    }

    public void updateLabel() {
        if (settingsViewFactory != null) settingsViewFactory.updateTestPropagationLabel(propagationQueue.size());
    }

    private String findForkUUTPath(List<String> filePaths, String className) {
        // Iterate through file paths to find the one that matches the class name
        return filePaths.stream().filter(p -> p.contains(className)).findFirst().orElse(null);
    }

    private boolean classExistsLocally(FileFinder fileFinder, String className) {
        VirtualFile file = fileFinder.findFileRecursively(className);
        return file != null && file.exists();
    }

    private boolean testCaseExistsLocally(FileFinder fileFinder, String testedClass, String forkTestMehtod) {
        String localTestFileContent = fileFinder.getTestFileContent(testedClass); // ToDo: at least it is meant to be
        if (localTestFileContent == null) return false;

        CodeDuplicationDetector detector = new CodeDuplicationDetector();
        List<String> localTestMethods = detector.splitIntoFunctions(localTestFileContent);
        if(localTestMethods == null) return false;

        return localTestMethods.stream()
                .anyMatch(f -> detector.calculateDuplicationMetric(f, forkTestMehtod) > 0.9);
    }

    private boolean functionExistsLocally(FileFinder fileFinder, String testedFilePath, String functionName) {
        int dotIndex = testedFilePath.lastIndexOf('.');
        String content = fileFinder.getFileContent(testedFilePath.substring(0, dotIndex)); // ToDo: full path instead of just file name?
        return content != null && content.contains(functionName);
    }

    private void addPropagationElement(FileFinder fileFinder, String usedClass, String potentialTestMethod, String testedClass){
        VirtualFile testedFile_localUUT_localUUTTest = fileFinder.findTestFileRecursively(usedClass); // ToDo: What is intended? Local UUT or local test of local UUT? E.g., Example.java or ExampleTest.java?
        propagationQueue.add(new PropagationElement(testedFile_localUUT_localUUTTest, potentialTestMethod, testedClass)); // TODO: Local UUT file, Potential Test Case (from fork), Tested Class (=usedClass in fork) ?
    }

    private void updateUIAfterFetch() {
        if (settingsViewFactory != null) {
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
