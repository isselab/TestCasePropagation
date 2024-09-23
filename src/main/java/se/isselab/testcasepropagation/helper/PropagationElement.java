package se.isselab.testcasepropagation.helper;

import com.intellij.openapi.vfs.VirtualFile;

public class PropagationElement {
    private VirtualFile projectFile;
    private String testCase;
    private String testedClass;


    public PropagationElement(VirtualFile projectFile, String testCase, String testedClass){
        this.projectFile = projectFile;
        this.testCase = testCase;
        this.testedClass = testedClass;
    }

    public VirtualFile getProjectFile(){
        return projectFile;
    }

    public String getTestCase(){
        return testCase;
    }

    public String getTestedClass(){
        return testedClass;
    }
}
