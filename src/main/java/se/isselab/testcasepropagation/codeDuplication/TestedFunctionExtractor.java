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
package se.isselab.testcasepropagation.codeDuplication;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestedFunctionExtractor {
    public String extractTestedFunctionName(String testMethod) {
        String[] lines = testMethod.split("\n");
        for (String line : lines) {
            if (line.contains(".")) {
                int start = line.indexOf(".") + 1;
                int end = line.indexOf("(", start);
                if (start >= 0 && end > start) {
                    return line.substring(start, end).trim();
                }
            }
        }
        return null;
    }

    public Map<String, String> extractFunctions(String testFileContent) {
        Map<String, String> testFunctions = new HashMap<>();

        // Regular expression to match method definitions (including test methods)
        String methodPattern = "(public|private|protected)?\\s+void\\s+(\\w+)\\s*\\([^\\)]*\\)\\s*\\{[\\s\\S]*?\\}";
        Pattern pattern = Pattern.compile(methodPattern);
        Matcher matcher = pattern.matcher(testFileContent);

        // Iterate over each method found and store its name and full body
        while (matcher.find()) {
            String fullFunction = matcher.group();  // Full test function
            String functionName = matcher.group(2);  // Extracted test function name

            // Add to map (key: function name, value: full function code)
            testFunctions.put(functionName, fullFunction);
        }

        return testFunctions;
    }
}
