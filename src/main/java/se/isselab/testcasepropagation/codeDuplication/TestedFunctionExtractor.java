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
