package jangle;

import java.io.PrintWriter;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

public class MessageEditorTextBox extends TextBox {
    private PrintWriter out;
    private DateFormat dateFormat;

    public MessageEditorTextBox(String username, PrintWriter out, TerminalSize preferredSize, Style style){
        super(preferredSize, style);
        this.out = out;
        this.dateFormat = new SimpleDateFormat("MM/dd/yy HH:mm");
    }

    @Override
    public Result handleKeyStroke(KeyStroke keyStroke) {
        if (keyStroke.getKeyType() == KeyType.Enter){
            if (!getText().equals("")){
                String timestampedMsg = dateFormat.format(new Date()) + " | " + getText();
                out.println(timestampedMsg);
            }

            setText("");
            takeFocus();

            if (out.checkError()) {
                System.out.println("ERROR: Lost connection to server");
                throw new RuntimeException();
            }
            return Result.HANDLED;
        }
        return super.handleKeyStroke(keyStroke);
    }
}