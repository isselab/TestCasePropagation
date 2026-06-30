package se.isselab.testcasepropagation.codeCollection;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class SimianRunner {

    public static File runSimian(File codeDir, File outputReport) throws IOException, InterruptedException {
        List<String> command = List.of(
                "java", "-jar", "PATH_TO_SIMIAN.JAR",
                "-includes=**/*.java",
                "-formatter=plain:" + outputReport.getAbsolutePath()
        );

        ExternalToolRunner.runCommand(command, codeDir);

        return outputReport;
    }
}
