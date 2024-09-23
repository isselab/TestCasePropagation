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
package se.isselab.testcasepropagation.codeDuplication;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassFileFinder {
    /**
     * Extracts all class names used in the code by looking for the 'new' keyword.
     *
     * @param code the full code as a string
     * @return a set of class names used in the code
     */
    public Set<String> extractUsedClassesFromString(String code) {
        Set<String> usedClasses = new HashSet<>();

        // Enhanced regex to match 'new <ClassName>' with any amount of whitespace or line breaks
        Pattern pattern = Pattern.compile("\\bnew\\s+([A-Z][A-Za-z0-9_]*)\\s*(?=\\(|<)");

        // Use a matcher directly on the input string
        Matcher matcher = pattern.matcher(code);
        while (matcher.find()) {
            String className = matcher.group(1);
            usedClasses.add(className);
        }

        return usedClasses;
    }
}
