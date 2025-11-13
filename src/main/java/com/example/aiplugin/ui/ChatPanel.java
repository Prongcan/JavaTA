package com.example.aiplugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.example.aiplugin.service.TaService;
import org.apache.commons.text.StringEscapeUtils;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class ChatPanel extends JPanel {

    // Data model for a single chat message
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
    private final TaService taService;
    private String selectedCode;
    private final List<ChatMessage> messages = new ArrayList<>();

    public ChatPanel(Project project) {
        super(new BorderLayout());
        this.taService = TaService.getInstance(project);

        chatHistory = new JEditorPane();
        chatHistory.setEditable(false);
        chatHistory.setContentType("text/html");
        chatHistory.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE); // Use font from component

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
                        // Ignore malformed links
                    }
                }
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(chatHistory);

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        sendButton = new JButton("Send");

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

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

        rerenderMessages(); // Initial render
    }

    public void setSelectedCode(String code) {
        this.selectedCode = code;
        if (code != null && !code.isEmpty()) {
            inputField.requestFocus();
        }
    }

    private void sendMessage() {
        String question = inputField.getText().trim();
        if (question.isEmpty()) { // Only check for empty, no hint text
            return;
        }

        addMessage(new ChatMessage("You", question, null));
        inputField.setText(""); // Clear input field
        setUiLoading(true);

        final String currentSelectedCode = selectedCode;
        this.selectedCode = null;

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                if (currentSelectedCode != null && !currentSelectedCode.isEmpty()) {
                    return taService.askAboutCode(currentSelectedCode, question);
                } else {
                    return taService.askQuestion(question);
                }
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    messages.remove(messages.size() - 1); // Remove "Thinking..."
                    addMessage(new ChatMessage("AI-TA", response, currentSelectedCode));
                } catch (Exception e) {
                    messages.remove(messages.size() - 1); // Remove "Thinking..."
                    addMessage(new ChatMessage("AI-TA", "Error: " + e.getMessage(), null));
                } finally {
                    setUiLoading(false);
                }
            }
        }.execute();
    }

    private void addMessage(ChatMessage message) {
        messages.add(message);
        rerenderMessages();
    }

    private void rerenderMessages() {
        StringBuilder html = new StringBuilder(
                "<html><head><style type='text/css'>" +
                "body { font-family: sans-serif; font-size: 12pt; padding: 5px; }" +
                "div { margin-bottom: 10px; }" +
                "b { color: #007bff; }" +
                "pre { padding: 10px; border: 1px solid #ccc; white-space: pre-wrap; }" + // Removed background-color
                "a { text-decoration: none; color: #888; font-weight: bold; }" +
                "</style></head><body>");

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            html.append("<div>");
            html.append("<b>").append(StringEscapeUtils.escapeHtml4(msg.sender)).append(":</b> ");
            html.append(StringEscapeUtils.escapeHtml4(msg.message).replace("\n", "<br>"));

            if (msg.code != null && !msg.code.isEmpty()) {
                String toggleText = msg.isCodeVisible ? "Hide Code" : "Show Code";
                html.append("<div><a href='toggle:").append(i).append("'>").append(toggleText).append("</a></div>");
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
        if (isLoading) {
            addMessage(new ChatMessage("AI-TA", "Thinking...", null));
        }
    }
}
