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

import java.util.*;
import java.util.regex.*;

public class CodeDuplicationDetector {
    public List<String> splitIntoFunctions(String code) {
        List<String> functions = new ArrayList<>();
        if(code == null){
            return null;
        }
        String functionPattern = "(public|protected|private|static|\\s) +[\\w<>\\[\\]]+ +\\w+ *\\([^)]*\\) *\\{(?:[^{}]*|\\{[^{}]*\\})*\\}";
        Pattern pattern = Pattern.compile(functionPattern);
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            functions.add(matcher.group());
        }

        return functions;
    }

    public double calculateDuplicationMetric(String code1, String code2) {
        List<String> tokens1 = tokenize(code1);
        List<String> tokens2 = tokenize(code2);

        int commonTokens = countCommonTokens(tokens1, tokens2);
        int totalTokens = Math.max(tokens1.size(), tokens2.size());

        return (double) commonTokens / totalTokens;
    }

    private List<String> tokenize(String code) {
        String delimiters = "\\s+|(?=[{}()<>\\[\\];,])|(?<=[{}()<>\\[\\];,])";
        List<String> tokens = Arrays.asList(code.split(delimiters));

        List<String> normalizedTokens = new ArrayList<>();
        List<String> seenNames = new ArrayList<>();
        int varCounter = 0;

        for (String token : tokens) {
            if (token.matches("\\w+")) { // Check if the token is a word (likely a variable or function name)
                if (!seenNames.contains(token)) {
                    seenNames.add(token);
                    normalizedTokens.add("var" + varCounter++);
                } else {
                    normalizedTokens.add("var" + seenNames.indexOf(token));
                }
            } else {
                normalizedTokens.add(token);
            }
        }

        return normalizedTokens;
    }

    private int countCommonTokens(List<String> tokens1, List<String> tokens2) {
        Set<String> set1 = new HashSet<>(tokens1);
        Set<String> set2 = new HashSet<>(tokens2);

        set1.retainAll(set2);

        int commonCount = 0;
        for (String token : set1) {
            commonCount += Math.min(Collections.frequency(tokens1, token), Collections.frequency(tokens2, token));
        }

        return commonCount;
    }
}
