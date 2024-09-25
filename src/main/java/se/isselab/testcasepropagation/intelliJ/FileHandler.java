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
package se.isselab.testcasepropagation.intelliJ;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileHandler {
    public FileHandler(){

    }

    public File getFile() {
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0) {
            return null;
        }

        Project currentProject = openProjects[0];
        String projectPath = currentProject.getBasePath();
        Path sourceRootPath = Paths.get(projectPath);
        return new File(sourceRootPath.toFile(), ".testCasePropagation");
    }
}
