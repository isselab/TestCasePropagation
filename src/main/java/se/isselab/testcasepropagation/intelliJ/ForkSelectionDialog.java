package se.isselab.testcasepropagation.intelliJ;

import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class ForkSelectionDialog extends DialogWrapper {
    private final List<String> forks;
    private final Set<String> preSelectedForks;
    private final Map<String, JCheckBox> checkBoxMap = new LinkedHashMap<>();
    private JCheckBox selectAllBox;
    private JPanel panel;

    public ForkSelectionDialog(List<String> forks, List<String> preSelectedForks) {
        super(true);
        this.forks = forks;
        this.preSelectedForks = new HashSet<>(preSelectedForks);
        setTitle("Select Forks");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        panel = new JPanel(new BorderLayout());

        JPanel checkBoxPanel = new JPanel();
        checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.Y_AXIS));

        selectAllBox = new JCheckBox("Select All");
        selectAllBox.addActionListener(e -> {
            boolean selected = selectAllBox.isSelected();
            for (JCheckBox checkBox : checkBoxMap.values()) {
                checkBox.setSelected(selected);
            }
        });

        checkBoxPanel.add(selectAllBox);

        for (String fork : forks) {
            JCheckBox checkBox = new JCheckBox(fork);
            checkBox.setSelected(preSelectedForks.contains(fork));
            checkBoxMap.put(fork, checkBox);
            checkBoxPanel.add(checkBox);
        }

        panel.add(new JScrollPane(checkBoxPanel), BorderLayout.CENTER);
        return panel;
    }

    public List<String> getSelectedForks() {
        List<String> selected = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> entry : checkBoxMap.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    @Override
    protected Action @NotNull [] createActions() {
        return new Action[]{getOKAction(), getCancelAction()};
    }

    @Override
    protected void doOKAction() {
        super.doOKAction();
    }
}
