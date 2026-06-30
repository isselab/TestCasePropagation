package se.isselab.testcasepropagation.intelliJ.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
        name = "ProjectSettings",
        storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class ProjectSettings implements PersistentStateComponent<ProjectSettings.State> {

    public static class State {
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

    public static ProjectSettings getInstance(@NotNull Project project) {
        return project.getService(ProjectSettings.class);
    }

    public List<String> getSelectedForks() {
        return new ArrayList<>(myState.selectedForks);
    }

    public void setSelectedForks(List<String> selectedForks) {
        myState.selectedForks = new ArrayList<>(selectedForks);
    }
}