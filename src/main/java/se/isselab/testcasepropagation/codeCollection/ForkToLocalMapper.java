package se.isselab.testcasepropagation.codeCollection;

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ForkToLocalMapper {

    public record Mapping(String forkTest, String forkUUT, String localTest, String localUUT) {}

    public static List<Mapping> generateMappings(File forkXml, File simianReport) throws ParserConfigurationException, IOException, SAXException {
        List<UUTExtractor.TestMethodUUTs> forkUUTs = UUTExtractor.extractUUTsFromSrcML(forkXml);
        List<SimianCloneMapper.CloneMatch> cloneMatches = SimianCloneMapper.parseSimianOutput(simianReport);
        Map<String, String> forkToLocalMap = SimianCloneMapper.mapForkToLocalFromClones(cloneMatches);

        List<Mapping> mappings = new ArrayList<>();
        for (UUTExtractor.TestMethodUUTs method : forkUUTs) {
            String forkTest = forkXml.getName().replace(".xml", "");
            String localTest = forkToLocalMap.getOrDefault(forkTest, "UNKOWN");

            for (String uut : method.uutCandidates()) {
                String localUUT = forkToLocalMap.getOrDefault(uut, "UNKOWN");
                mappings.add(new Mapping(forkTest, uut, localTest, localUUT));
            }
        }
        return mappings;
    }
}
