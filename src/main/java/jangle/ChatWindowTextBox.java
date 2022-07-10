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
    public synchronized Result handleKeyStroke(KeyStroke keyStroke) {
        KeyType keyType = keyStroke.getKeyType();
        Result result;
        TerminalPosition caretPosition = getCaretPosition();
        int screenHeight = getSize().getRows();
        int screenWidth = getSize().getColumns();

        switch (keyType){
            //ignore input
            case Backspace:
            case Character:
            case Delete:
            case Enter:
                result = Result.UNHANDLED;
                break;

            case ArrowUp:
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

            case ArrowLeft:
                setCaretPosition(caretPosition.getColumn() - screenWidth / 2);
                result = Result.HANDLED;
                break;

            case ArrowRight:
                int maxLength = Integer.MIN_VALUE;
                int maxLengthLineIndex = -1;

                for (int i = caretPosition.getRow() - screenHeight / 2; i < caretPosition.getRow() + screenHeight / 2; i++){
                    try {
                        String line = getLine(i);
                        if (line.length() > maxLength){
                            maxLength = line.length();
                            maxLengthLineIndex = i;
                        }
                    }
                    catch (IndexOutOfBoundsException iobe){}
                }

                setCaretPosition(maxLengthLineIndex, caretPosition.getColumn() + screenWidth / 2);
                result = Result.HANDLED;
                break;

            default:
                result = super.handleKeyStroke(keyStroke);
        }

        return result;
    }

    public synchronized ChatWindowTextBox removeExcessLines(boolean fromTop){
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

    public synchronized ChatWindowTextBox scrollToTop(){
        setCaretPosition(0, 0);
        return this;
    }

    public synchronized ChatWindowTextBox scrollToBottom(){
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
