package jangle;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.TextBox;

public class ChatWindowTextBox extends TextBox{
    public ChatWindowTextBox(TerminalSize preferredSize, Style style){
        super(preferredSize, style);
    }

    public ChatWindowTextBox addLineAndScrollDown(String line){
        super.addLine(line);
        setReadOnly(false);
        takeFocus();
        setCaretPosition(Integer.MAX_VALUE, Integer.MAX_VALUE);

        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {}

        setReadOnly(true);
        return this;
    }

    public ChatWindowTextBox setTextAndScrollDown(String text){
        super.setText(text);
        setReadOnly(false);
        takeFocus();
        setCaretPosition(Integer.MAX_VALUE, Integer.MAX_VALUE);

        try {
            Thread.sleep(15);
        } catch (InterruptedException e) {}

        setReadOnly(true);
        return this;
    }
}
