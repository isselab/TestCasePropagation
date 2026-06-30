package se.isselab.testcasepropagation.codeCollection;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class ExternalToolRunner {

    public static void runCommand(List<String> command, File workingDir) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDir);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.lines().forEach(System.out::println); // logging
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command '" + String.join(" ", command) + "' failed with exit code " + exitCode);
        }
    }
}
