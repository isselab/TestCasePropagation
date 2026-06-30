package se.isselab.testcasepropagation.codeCollection;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SrcMLRunner {

    public static File convertToXml(File javaFile, File outputDir) throws IOException, InterruptedException {
        File outputXml = new File(outputDir, javaFile.getName() + ".xml");

        List<String> command = List.of(
                "srcml", javaFile.getAbsolutePath(), "-o", outputXml.getAbsolutePath()
        );

        ExternalToolRunner.runCommand(command, javaFile.getParentFile());

        return outputXml;
    }
}
