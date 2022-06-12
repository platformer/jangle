package jangle;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

public class ChatWindowTextBox extends TextBox{
    public ChatWindowTextBox(TerminalSize preferredSize, Style style){
        super(preferredSize, style);
    }

    @Override
    public Result handleKeyStroke(KeyStroke keyStroke) {
        KeyType keyType = keyStroke.getKeyType();
        Result result;
        TerminalPosition caretPosition;

        switch (keyType){
            //ignore input
            case Backspace:
            case Character:
            case Delete:
            case Enter:
                result = Result.UNHANDLED;
                break;

            case ArrowUp:
                caretPosition = getCaretPosition();
                if (caretPosition.getRow() == 0){
                    //request messages
                }

                setCaretPosition(caretPosition.getRow() - 3, caretPosition.getColumn());
                result = Result.HANDLED;
                break;

            case ArrowDown:
                caretPosition = getCaretPosition();
                if (getCaretPosition().getRow() == getLineCount() - 1){
                    //request messages
                }

                setCaretPosition(caretPosition.getRow() + 3, caretPosition.getColumn());

                if (caretPosition.getRow() >= getLineCount() - 3){
                    result = Result.MOVE_FOCUS_DOWN;
                }
                else {
                    result = super.handleKeyStroke(keyStroke);
                }
                break;

            default:
                result = super.handleKeyStroke(keyStroke);
        }

        return result;
    }

    public ChatWindowTextBox addLineAndScrollDown(String line){
        addLine(line);
        setCaretPosition(Integer.MAX_VALUE, 0);
        return this;
    }

    public ChatWindowTextBox setTextAndScrollDown(String text){
        setText(text);
        setCaretPosition(Integer.MAX_VALUE, 0);
        return this;
    }
}
