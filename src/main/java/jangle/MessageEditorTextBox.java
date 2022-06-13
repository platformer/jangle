package jangle;

import java.io.IOException;
import java.io.ObjectOutputStream;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

public class MessageEditorTextBox extends TextBox {
    private ObjectOutputStream out;

    public MessageEditorTextBox(String username, ObjectOutputStream out, TerminalSize preferredSize, Style style) {
        super(preferredSize, style);
        this.out = out;
    }

    @Override
    public Result handleKeyStroke(KeyStroke keyStroke) {
        if (keyStroke.getKeyType() == KeyType.Enter) {
            if (!getText().equals("")) {
                UserMessage serializedMessage = new UserMessage(UserMessage.UserMessageType.Chat, getText());

                try {
                    out.writeObject(serializedMessage);
                    out.flush();
                } catch (IOException ioe) {
                    System.out.println("ERROR: Lost connection to server");
                    throw new RuntimeException();
                }
            }

            setText("");
            takeFocus();
            return Result.HANDLED;
        }
        return super.handleKeyStroke(keyStroke);
    }
}