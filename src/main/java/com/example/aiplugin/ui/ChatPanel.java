package com.example.aiplugin.ui;

import com.example.aiplugin.service.AskQuestion;
import com.example.aiplugin.service.TaService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.DiffManager;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import org.apache.commons.text.StringEscapeUtils;
import com.intellij.ide.BrowserUtil;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.ext.tables.TablesExtension;

public class ChatPanel extends JPanel {

    private static final String PDF_FOLDER_PROPERTY_KEY = "ai_plugin_pdf_folder_path";

    private static class ChatMessage {
        String sender;
        String message;
        String code;
        boolean isCodeVisible = true;

        ChatMessage(String sender, String message, String code) {
            this.sender = sender;
            this.message = message;
            this.code = code;
        }
    }

    private final JEditorPane chatHistory;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JButton settingsButton;
    private final TaService taService;
    private String selectedCode;
    private final List<ChatMessage> messages = new ArrayList<>();

    private final AskQuestion aq = new AskQuestion();
    private final Gson gson = new Gson();
    private final Project project;

    private final Parser mdParser;
    private final HtmlRenderer mdRenderer;

    // Variables to store editor context for code modification
    private Editor lastActiveEditor;
    private int selectionStart;
    private int selectionEnd;

    public ChatPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.taService = TaService.getInstance(project);

        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()));
        mdParser = Parser.builder(options).build();
        mdRenderer = HtmlRenderer.builder(options).build();

        chatHistory = new JEditorPane();
        chatHistory.setEditable(false);
        chatHistory.setContentType("text/html");
        chatHistory.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        chatHistory.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                String desc = e.getDescription();
                if (desc != null && desc.startsWith("toggle:")) {
                    try {
                        int messageIndex = Integer.parseInt(desc.substring("toggle:".length()));
                        if (messageIndex >= 0 && messageIndex < messages.size()) {
                            ChatMessage msg = messages.get(messageIndex);
                            msg.isCodeVisible = !msg.isCodeVisible;
                            rerenderMessages();
                        }
                    } catch (NumberFormatException ex) {
                        // Ignore
                    }
                    return;
                }
                if (e.getURL() != null) {
                    BrowserUtil.browse(e.getURL());
                }
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(chatHistory);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        sendButton = new JButton("Send");
        settingsButton = new JButton("Settings");

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonPanel.add(settingsButton);
        buttonPanel.add(sendButton);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        add(scrollPane, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);

        sendButton.addActionListener(e -> sendMessage());
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessage();
                }
            }
        });
        settingsButton.addActionListener(e -> showSettingsDialog());

        rerenderMessages();
    }

    private void showSettingsDialog() {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(
                false, true, false, false, false, false)
                .withTitle("Select PDF Folder")
                .withDescription("Select the folder containing your course material PDFs.");
        VirtualFile file = FileChooser.chooseFile(descriptor, project, null);
        if (file != null) {
            String path = file.getPath();
            PropertiesComponent.getInstance(project).setValue(PDF_FOLDER_PROPERTY_KEY, path);
            Messages.showMessageDialog(project, "PDF folder set to:\n" + path, "Settings Saved", Messages.getInformationIcon());
        }
    }

    public void setSelectedCode(String code, Editor editor) {
        this.selectedCode = code;
        this.lastActiveEditor = editor;
        if (code != null && !code.isEmpty() && editor != null) {
            this.selectionStart = editor.getSelectionModel().getSelectionStart();
            this.selectionEnd = editor.getSelectionModel().getSelectionEnd();
        }
    }

    private void sendMessage() {
        String question = inputField.getText().trim();
        if (question.isEmpty()) {
            return;
        }

        String pdfPath = PropertiesComponent.getInstance(project).getValue(PDF_FOLDER_PROPERTY_KEY, "");
        if (pdfPath.isEmpty()) {
            Messages.showMessageDialog(project,
                    "The PDF folder for course materials has not been configured.\nPlease use the 'Settings' button to select a folder.",
                    "Configuration Required",
                    Messages.getWarningIcon());
            return;
        }

        addMessage(new ChatMessage("You", question, null));
        inputField.setText("");
        setUiLoading(true);

        final String currentSelectedCode = selectedCode;
        final Editor editorContext = lastActiveEditor; // Capture the editor context
        this.selectedCode = null;
        this.lastActiveEditor = null; // Clear member variable immediately

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                JsonArray historyArray = new JsonArray();
                for (ChatMessage msg : messages) {
                    if (msg.sender.equals("AI-TA") && msg.message.equals("Thinking...")) {
                        continue;
                    }
                    JsonObject historyMsg = new JsonObject();
                    historyMsg.addProperty("role", msg.sender.equals("You") ? "user" : "assistant");
                    historyMsg.addProperty("content", msg.message);
                    historyArray.add(historyMsg);
                }
                String chatHistory = gson.toJson(historyArray);
                return aq.askQuestion(project, question, chatHistory, currentSelectedCode);
            }

            @Override
            protected void done() {
                try {
                    String jsonResponse = get();
                    JsonObject responseObj = gson.fromJson(jsonResponse, JsonObject.class);

                    String answer = responseObj.get("answer").getAsString();
                    boolean cited = responseObj.get("cited").getAsBoolean();
                    String retrievalResult = cited ? responseObj.get("retrieval_result").getAsString() : null;
                    String modifiedCode = responseObj.has("code") ? responseObj.get("code").getAsString() : "";

                    messages.remove(messages.size() - 1);

                    String finalMessage = answer;
                    if (cited && retrievalResult != null && !retrievalResult.isEmpty()) {
                        finalMessage += "\n\n--- \n**References:**\n" + retrievalResult;
                    } else {
                        finalMessage += "\n\n--- \n**No References**\n";
                    }

                    addMessage(new ChatMessage("AI-TA", finalMessage, currentSelectedCode));

                    // F1 Feature: Show diff if code modification is suggested
                    if (modifiedCode != null && !modifiedCode.isEmpty() && currentSelectedCode != null) {
                        showCodeModificationDiff(currentSelectedCode, modifiedCode, editorContext);
                    }

                } catch (Exception e) {
                    messages.remove(messages.size() - 1);
                    addMessage(new ChatMessage("AI-TA", "Error: " + e.getMessage(), null));
                    e.printStackTrace();
                } finally {
                    setUiLoading(false);
                }
            }
        }.execute();
    }

    private void showCodeModificationDiff(String originalCode, String modifiedCode, Editor editor) {
        // Ensure this runs on the UI thread
        EventQueue.invokeLater(() -> {
            DiffContentFactory contentFactory = DiffContentFactory.getInstance();
            SimpleDiffRequest request = new SimpleDiffRequest(
                    "AI-TA Code Modification Suggestion",
                    contentFactory.create(originalCode, (FileType) null),
                    contentFactory.create(modifiedCode, (FileType) null),
                    "Original Code",
                    "AI Suggested Code"
            );

            DiffManager.getInstance().showDiff(project, request);

            int choice = Messages.showYesNoDialog(
                    project,
                    "Do you want to apply the AI-generated changes to your code?",
                    "Apply Changes",
                    Messages.getQuestionIcon()
            );

            if (choice == Messages.YES) {
                applyCodeChanges(modifiedCode, editor);
            }
        });
    }

    private void applyCodeChanges(String modifiedCode, Editor editor) {
        if (editor == null) {
            Messages.showErrorDialog(project, "Could not find the original editor to apply changes.", "Error");
            return;
        }
        // All document modifications must be performed in a write action
        WriteCommandAction.runWriteCommandAction(project, () -> {
            editor.getDocument().replaceString(selectionStart, selectionEnd, modifiedCode);
        });
        // Clear selection
        editor.getSelectionModel().removeSelection();
    }

    private void addMessage(ChatMessage message) {
        messages.add(message);
        rerenderMessages();
    }

    private String renderMarkdown(String md) {
        if (md == null) return "";
        return mdRenderer.render(mdParser.parse(md));
    }

    private void rerenderMessages() {
        StringBuilder html = new StringBuilder(
                "<html><head><style type='text/css'>" +
                "body { font-family: sans-serif; font-size: 12pt; padding: 5px; }" +
                "div.msg { margin-bottom: 14px; }" +
                "b.sender { color: #007bff; }" +
                "pre, code { font-family: Consolas, 'Courier New', monospace; }" +
                "pre { padding: 8px; border: 1px solid #ccc; background:rgb(61, 49, 49); overflow-x: auto; }" +
                "a.toggle { text-decoration: none; color: #888; font-weight: bold; }" +
                "ul, ol { margin-top: 6px; }" +
                "hr { border: none; border-top: 1px solid #e0e0e0; margin: 10px 0; }" +
                "table { border-collapse: collapse; } td, th { border: 1px solid #ddd; padding: 4px 6px; }" +
                "</style></head><body>");

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            html.append("<div class='msg'>");
            html.append("<b class='sender'>").append(StringEscapeUtils.escapeHtml4(msg.sender)).append(":</b> ");

            if ("AI-TA".equals(msg.sender)) {
                html.append(renderMarkdown(msg.message));
            } else {
                html.append(StringEscapeUtils.escapeHtml4(msg.message).replace("\n", "<br>"));
            }

            if (msg.code != null && !msg.code.isEmpty()) {
                String toggleText = msg.isCodeVisible ? "Hide Code" : "Show Code";
                html.append("<div><a class='toggle' href='toggle:").append(i).append("'>").append(toggleText).append("</a></div>");
                if (msg.isCodeVisible) {
                    html.append("<pre><code>").append(StringEscapeUtils.escapeHtml4(msg.code)).append("</code></pre>");
                }
            }
            html.append("</div>");
        }

        html.append("</body></html>");
        chatHistory.setText(html.toString());
        chatHistory.setCaretPosition(chatHistory.getDocument().getLength());
    }

    private void setUiLoading(boolean isLoading) {
        inputField.setEnabled(!isLoading);
        sendButton.setEnabled(!isLoading);
        settingsButton.setEnabled(!isLoading);
        if (isLoading) {
            addMessage(new ChatMessage("AI-TA", "Thinking...", null));
        }
    }
}

