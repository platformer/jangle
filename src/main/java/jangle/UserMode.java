package jangle;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import org.javatuples.Quartet;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;

public class UserMode {
    public static void startChat(String host, int port, String username) {
        Socket s;
        ObjectOutputStream out;
        ObjectInputStream in;
        
        try {
            s = new Socket(host, port);
            out = new ObjectOutputStream(s.getOutputStream());
            in = new ObjectInputStream(s.getInputStream());
        } catch (IOException ioe) {
            System.err.println("ERROR: could not connect to server at " + host);
            return;
        }

        try {
            ServerMessage rejectMessage = (ServerMessage) in.readObject();
            if (rejectMessage.getType() == ServerMessage.ServerMessageType.DisconnectDuplicate
                && (Boolean) rejectMessage.getPayload()) {
                in.close();
                out.close();
                s.close();
                System.err.println("ERROR: disconnected from server - are you already connected to this server?");
                return;
            }
        }
        catch (IOException | ClassNotFoundException e){
            System.err.println("ERROR: disconnected from server");
            return;
        }

        try {
            UserMessage usernameMsg = new UserMessage(UserMessage.UserMessageType.Username, username);
            out.writeObject(usernameMsg);
            out.flush();
        }
        catch (IOException ioe){
            System.err.println("ERROR: could not complete handshake with server at " + host);
            try {
                in.close();
                out.close();
                s.close();
            }
            catch (IOException ioe2){}
            return;
        }

        try {
            Screen screen = new DefaultTerminalFactory().createScreen();
            screen.startScreen();

            MultiWindowTextGUI textGUI = new MultiWindowTextGUI(screen);
            Window baseWindow = new BasicWindow();
            baseWindow.setHints(
                Arrays.asList(
                    Window.Hint.CENTERED,
                    Window.Hint.FULL_SCREEN,
                    Window.Hint.MODAL,
                    Window.Hint.NO_DECORATIONS,
                    Window.Hint.NO_POST_RENDERING
                )
            );
            baseWindow.setTheme(new SimpleTheme(TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));

            Panel mainPanel = new Panel(new GridLayout(1));

            mainPanel.addComponent(
                new ChatWindowTextBox(new TerminalSize(1, 1), TextBox.Style.MULTI_LINE),
                GridLayout.createLayoutData(
                    GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true, true, 50, 15
                )
            );

            mainPanel.addComponent(
                new MessageEditorTextBox(username, out, new TerminalSize(1, 2), TextBox.Style.SINGLE_LINE),
                GridLayout.createLayoutData(
                    GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true, false, 50, 3
                )
            );

            List<Component> panelComponents = mainPanel.getChildrenList();
            ChatWindowTextBox readBox = (ChatWindowTextBox) panelComponents.get(0);
            MessageEditorTextBox writeBox = (MessageEditorTextBox) panelComponents.get(1);

            baseWindow.setComponent(mainPanel);
            writeBox.takeFocus();

            Thread receiveThread = new Thread(() -> listen(in, readBox, screen));
            receiveThread.start();
            textGUI.addWindowAndWait(baseWindow);
            receiveThread.interrupt();
        } catch (IOException ioe) {
            System.err.println("ERROR: Failed to start application");
        } finally {
            try {
                in.close();
                out.close();
                s.close();
            } catch (IOException e) {}
        }
    }

    @SuppressWarnings("unchecked")
    private static void listen(ObjectInputStream in, ChatWindowTextBox readBox, Screen screen) {
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MM/dd/YY HH:mm");

        while (true) {
            ServerMessage incomingMsg;

            try {
                incomingMsg = (ServerMessage) in.readObject();
            }
            catch (IOException | ClassNotFoundException e){
                try {
                    screen.stopScreen();
                } catch (IOException ioe) {}
                System.err.println("Error: disconnected from server - remote server may have shut down");
                return;
            }

            switch (incomingMsg.getType()){
                case Chat:
                    Quartet<Instant, String, Integer, String> payload =
                        (Quartet<Instant, String, Integer, String>) incomingMsg.getPayload();
                    String timeStamp = payload.getValue0().atZone(ZoneId.systemDefault()).format(dateFormat);
                    String username = payload.getValue1();
                    int userID = payload.getValue2();
                    String messageBody = payload.getValue3();
                    String signedMsg = username + " #" + userID + "\n" + timeStamp + " | " + messageBody + "\n";

                    if (readBox.getCaretPosition().getRow() > readBox.getLineCount() - readBox.getSize().getRows()) {
                        readBox.addLineAndScrollDown(signedMsg);
                    }
                    else {
                        readBox.addLine(signedMsg);
                    }

                default:
            }
        }
    }
}
