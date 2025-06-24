package se.isselab.testcasepropagation.codeCollection;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimianCloneMapper {

    public record CloneMatch(String forkFile, int forkStartLine, String localFile, int localStartLine, int lineCount) {}

    private static final Pattern matchPattern = Pattern.compile("Found (\\d+) duplicate lines in:(.*?)\\n\\n", Pattern.DOTALL);

    public static List<CloneMatch> parseSimianOutput(File simianOutput) throws IOException {
        String content = Files.readString(simianOutput.toPath());
        List<CloneMatch> matches = new ArrayList<>();

        Matcher matcher = matchPattern.matcher(content);
        while (matcher.find()) {
            String block = matcher.group(2).trim();
            String[] lines = block.split("\n");
            if (lines.length >= 2) {
                String[] forkInfo = lines[0].split(":");
                String[] localInfo = lines[1].split(":");
                matches.add(new CloneMatch(
                        forkInfo[0].trim(),
                        Integer.parseInt(forkInfo[1].split("-")[0]),
                        localInfo[0].trim(),
                        Integer.parseInt(localInfo[1].split("-")[0]),
                        Integer.parseInt(matcher.group(1))
                ));
            }
        }
        return matches;
    }

    public static Map<String, String> mapForkToLocalFromClones(List<CloneMatch> matches) {
        Map<String, String> map = new HashMap<>();
        for (CloneMatch match : matches) {
            map.putIfAbsent(match.forkFile(), match.localFile());
        }
        return map;
    }
}
