package se.isselab.testcasepropagation.codeCollection;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class UUTExtractor {

    public record TestMethodUUTs(String testMethodName, Set<String> uutCandidates) {}

    public static List<TestMethodUUTs> extractUUTsFromSrcML(File srcmlXmlFile) throws ParserConfigurationException, IOException, SAXException {
        List<TestMethodUUTs> result = new ArrayList<>();

        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = db.parse(srcmlXmlFile);

        NodeList functions = doc.getElementsByTagName("function");

        for (int i = 0; i < functions.getLength(); i++) {
            Element function = (Element) functions.item(i);

            String methodName = getChildText(function, "name");

            Set<String> uuts = new HashSet<>();
            Map<String, String> variableToClass = new HashMap<>();

            // Variable declarations
            NodeList declStmts = function.getElementsByTagName("decl_stmt");
            for (int j = 0; j < declStmts.getLength(); j++) {
                Element declStmt = (Element) declStmts.item(j);

                NodeList decls = declStmt.getElementsByTagName("decl");
                for (int k = 0; k < decls.getLength(); k++) {
                    Element decl = (Element) decls.item(k);

                    NodeList nameNodes = decl.getElementsByTagName("name");
                    if (nameNodes.getLength() >= 2) {
                        String className = nameNodes.item(0).getTextContent().trim();
                        String varName = nameNodes.item(1).getTextContent().trim();

                        variableToClass.put(varName, className);
                    };
                }
            }

            // Method calls
            NodeList calls = function.getElementsByTagName("call");
            for (int j = 0; j < calls.getLength(); j++) {
                Element call = (Element) calls.item(j);
                String callText = call.getTextContent().trim();
                String[] parts = callText.split("\\.");
                if (parts.length > 1) {
                    String prefix = parts[0]; // TODO: good to find class file but bad for: StaticOuter.StaticInner.someMethod()
                    if (variableToClass.containsKey(prefix)) {
                        uuts.add(variableToClass.get(prefix));
                    } else {
                        uuts.add(prefix);
                    }
                }
            }

            // Method references
            NodeList exprs = function.getElementsByTagName("expr");
            for (int j = 0; j < exprs.getLength(); j++) {
                Element expr = (Element) exprs.item(j);

                NodeList ops = expr.getElementsByTagName("operator");
                for (int k = 0; k < ops.getLength(); k++) {
                    if ("::".equals(ops.item(k).getTextContent().trim())) {
                        NodeList names = expr.getElementsByTagName("name");
                        if (names.getLength() >= 1) {
                            String nameText = names.item(0).getTextContent().trim();

                            String prefix;
                            if (nameText.contains(".")) {
                                prefix = nameText.split("\\.")[0];
                            } else {
                                prefix = nameText;
                            }

                            if (variableToClass.containsKey(prefix)) {
                                uuts.add(variableToClass.get(prefix));
                            } else {
                                uuts.add(prefix);
                            }
                        }
                    }
                }
            }

            result.add(new TestMethodUUTs(methodName, uuts));
        }

        return result;
    }

    private static String getChildText(Element element, String tagName) {
        NodeList nl = element.getElementsByTagName(tagName);
        if (nl.getLength() == 0) return null;
        return nl.item(0).getTextContent().trim();
    }
}
