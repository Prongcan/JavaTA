package com.example.aiplugin.action;

import com.example.aiplugin.ui.ChatPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

public class AskAboutCodeAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }

        // 1. Get selected code
        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedCode = selectionModel.getSelectedText();
        if (selectedCode == null || selectedCode.isEmpty()) {
            return;
        }

        // 2. Activate the tool window
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("AI-TA");
        if (toolWindow != null) {
            toolWindow.activate(() -> {
                // 3. Find the ChatPanel and pass the code and editor context
                Content content = toolWindow.getContentManager().getContent(0);
                if (content != null && content.getComponent() instanceof ChatPanel) {
                    ChatPanel chatPanel = (ChatPanel) content.getComponent();
                    chatPanel.setSelectedCode(selectedCode, editor);
                }
            });
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // The action should only be visible and enabled when there is a text selection
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(editor != null && editor.getSelectionModel().hasSelection());
    }
}
