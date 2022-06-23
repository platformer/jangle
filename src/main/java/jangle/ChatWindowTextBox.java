package jangle;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.time.Instant;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

public class ChatWindowTextBox extends TextBox{
    private final Window baseWindow;
    private final ObjectOutputStream out;
    private Instant lastScrollTime;
    private int firstMessageNum;
    private int lastMessageNum;

    public ChatWindowTextBox(Window baseWindow, ObjectOutputStream out, TerminalSize preferredSize, Style style){
        super(preferredSize, style);
        this.baseWindow = baseWindow;
        this.out = out;
        lastScrollTime = Instant.EPOCH;
        firstMessageNum = -1;
        lastMessageNum = -1;
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
                    Instant currScrollTime = Instant.now();

                    if (Duration.between(lastScrollTime, currScrollTime).getSeconds() > App.SECONDS_BETWEEN_CHUNK_REQUESTS){
                        lastScrollTime = currScrollTime;
                        UserMessage serializedMessage =
                            new UserMessage(UserMessage.UserMessageType.RequestOldMessages, firstMessageNum);

                        try {
                            synchronized (out){
                                out.writeObject(serializedMessage);
                                out.flush();
                            }
                        } catch (IOException ioe) {
                            baseWindow.close();
                        }
                    }
                }

                setCaretPosition(caretPosition.getRow() - 3, caretPosition.getColumn());
                result = Result.HANDLED;
                break;

            case ArrowDown:
                caretPosition = getCaretPosition();

                if (getCaretPosition().getRow() == getLineCount() - 1){
                    Instant currScrollTime = Instant.now();

                    if (Duration.between(lastScrollTime, currScrollTime).getSeconds() > App.SECONDS_BETWEEN_CHUNK_REQUESTS){
                        lastScrollTime = currScrollTime;
                        UserMessage serializedMessage =
                            new UserMessage(UserMessage.UserMessageType.RequestNewMessages, lastMessageNum);

                        try {
                            synchronized (out){
                                out.writeObject(serializedMessage);
                                out.flush();
                            }
                        } catch (IOException ioe) {
                            baseWindow.close();
                        }
                    }
                }

                setCaretPosition(caretPosition.getRow() + 3, caretPosition.getColumn());
                result = Result.HANDLED;
                break;

            default:
                result = super.handleKeyStroke(keyStroke);
        }

        return result;
    }

    public ChatWindowTextBox removeExcessLines(boolean fromTop){
        if (lastMessageNum - firstMessageNum + 1 > App.MAX_DISPLAY_MESSAGES){
            String[] lines = (getText().trim() + "\n").split("\n", -1);
            String newText = "";

            if (fromTop){
                int i = 0;
                int firstMessageNumIncrement = 0;
                for (; firstMessageNumIncrement < lastMessageNum - firstMessageNum + 1 - App.MAX_DISPLAY_MESSAGES; i++){
                    if (lines[i].equals("")){
                        firstMessageNumIncrement++;
                    }
                }

                for (; i < lines.length; i++){
                    newText += lines[i] + "\n";
                }

                firstMessageNum += firstMessageNumIncrement;
                setText(newText);
                addLine("");
            }
            else {
                int i = 0;
                for (int messagesTraversed = 0; messagesTraversed < App.MAX_DISPLAY_MESSAGES; i++){
                    newText += lines[i] + "\n";
                    if (lines[i].equals("")){
                        messagesTraversed++;
                    }
                }

                for (; i < lines.length; i++){
                    if (lines[i].equals("")){
                        lastMessageNum--;
                    }
                }

                setText(newText);
                addLine("");
            }
        }

        return this;
    }

    public ChatWindowTextBox scrollToTop(){
        setCaretPosition(0, 0);
        return this;
    }

    public ChatWindowTextBox scrollToBottom(){
        setCaretPosition(Integer.MAX_VALUE, 0);
        return this;
    }

    public int getFirstMessageNum(){
        return firstMessageNum;
    }

    public void setFirstMessageNum(int firstMessageNum){
        this.firstMessageNum = firstMessageNum;
    }

    public int getLastMessageNum(){
        return lastMessageNum;
    }

    public void setLastMessageNum(int lastMessageNum){
        this.lastMessageNum = lastMessageNum;
    }

    public void incrementLastMessageNum(){
        lastMessageNum++;
    }
}
