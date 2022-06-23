package jangle;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Timer;
import java.util.TimerTask;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

public class MessageEditorTextBox extends TextBox {
    private final ObjectOutputStream out;
    private volatile boolean isBlocked;

    public MessageEditorTextBox(ObjectOutputStream out, TerminalSize preferredSize, Style style) {
        super(preferredSize, style);
        this.out = out;
        this.isBlocked = false;
    }

    @Override
    public Result handleKeyStroke(KeyStroke keyStroke) {
        if (isBlocked){
            return Result.HANDLED;
        }

        if (keyStroke.getKeyType() == KeyType.Enter) {
            if (!getText().equals("")) {
                String message = getText();

                if (message.length() > App.MAX_CHARS_PER_MESSAGE){
                    Timer timer = new Timer();
                    setText("<Too long! Message is " + message.length() + " characters, " +
                        "max is " + App.MAX_CHARS_PER_MESSAGE + ">");
                    isBlocked = true;
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run(){
                            restoreText(message);
                        }
                    }, App.POPUP_TIMOUT_MILLIS);
                    return Result.HANDLED;
                }

                UserMessage serializedMessage = new UserMessage(UserMessage.UserMessageType.Chat, message);

                try {
                    synchronized (out){
                        out.writeObject(serializedMessage);
                        out.flush();
                    }
                } catch (IOException ioe) {
                    System.out.println("ERROR: Lost connection to server");
                    System.exit(0);
                }
            }

            setText("");
            takeFocus();
            return Result.HANDLED;
        }
        
        if (keyStroke.getKeyType() == KeyType.ArrowUp){
            String message = getText();
            Timer timer = new Timer();
            setText("<Press Tab to switch between text box and chat window>");
            isBlocked = true;
            timer.schedule(new TimerTask() {
                @Override
                public void run(){
                    restoreText(message);
                }
            }, App.POPUP_TIMOUT_MILLIS);
            return Result.HANDLED;
        }
        
        return super.handleKeyStroke(keyStroke);
    }

    private void restoreText(String text){
        setText(text);
        setCaretPosition(Integer.MAX_VALUE);
        isBlocked = false;
        takeFocus();
    }
}
