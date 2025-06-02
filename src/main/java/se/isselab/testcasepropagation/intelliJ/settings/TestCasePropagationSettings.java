/*
Copyright [2024] [Luca Kramer]

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
package se.isselab.testcasepropagation.intelliJ.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
        name = "se.isselab.testcasepropagation.MyPluginSettings",
        storages = {@Storage("MyPluginSettings.xml")}
)
public class TestCasePropagationSettings implements PersistentStateComponent<TestCasePropagationSettings.State> {

    public static class State {
        public String githubApiKey = "";
        public List<String> selectedForks = new ArrayList<>();
    }

    private State myState = new State();

    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    public static TestCasePropagationSettings getInstance() {
        return ServiceManager.getService(TestCasePropagationSettings.class);
    }

    public String getGithubApiKey() {
        return myState.githubApiKey;
    }

    public void setGithubApiKey(String githubApiKey) {
        myState.githubApiKey = githubApiKey;
    }

    public List<String> getSelectedForks() {
        return myState.selectedForks;
    }

    public void setSelectedForks(List<String> selectedForks) {
        myState.selectedForks = new ArrayList<>(selectedForks);
    }
}
