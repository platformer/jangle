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
import org.javatuples.Triplet;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.gui2.BasicWindow;
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
        Screen screen = null;
        Thread listenThread = null;
        int id;
        
        try {
            s = new Socket(host, port);
            s.setTcpNoDelay(true);
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
            ServerMessage idMessage = (ServerMessage) in.readObject();
            id = (int) idMessage.getPayload();
        }
        catch (IOException | ClassNotFoundException | ClassCastException e){
            System.err.println("ERROR: could not complete handshake with server at " + host);
            try {
                in.close();
                out.close();
                s.close();
            } catch (IOException ioe){}
            return;
        }

        try {
            screen = new DefaultTerminalFactory().createScreen();
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
            ChatWindowTextBox readBox = new ChatWindowTextBox(baseWindow, out, new TerminalSize(1, 1), TextBox.Style.MULTI_LINE);
            MessageEditorTextBox writeBox = new MessageEditorTextBox(out, new TerminalSize(1, 2), TextBox.Style.SINGLE_LINE);

            mainPanel.addComponent(
                readBox,
                GridLayout.createLayoutData(
                    GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true, true, 50, 15
                )
            );

            mainPanel.addComponent(
                writeBox,
                GridLayout.createLayoutData(
                    GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true, false, 50, 3
                )
            );

            readBox.setVerticalFocusSwitching(false);
            writeBox.setVerticalFocusSwitching(false);
            baseWindow.setComponent(mainPanel);
            writeBox.takeFocus();
            listenThread = new Thread(() -> listen(id, in, readBox, baseWindow));
            listenThread.start();
            textGUI.addWindowAndWait(baseWindow);
        } catch (IOException ioe) {
            System.err.println("ERROR: Failed to start application");
        } finally {
            try {
                screen.stopScreen();
                listenThread.interrupt();
                in.close();
                out.close();
                s.close();
            } catch (IOException | NullPointerException e) {}
        }
    }

    @SuppressWarnings("unchecked")
    private static void listen(int id, ObjectInputStream in, ChatWindowTextBox readBox, Window baseWindow) {
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MM/dd/YY HH:mm");

        while (true) {
            ServerMessage incomingMsg;

            try {
                incomingMsg = (ServerMessage) in.readObject();
            }
            catch (IOException | ClassNotFoundException e){
                baseWindow.close();
                System.err.println("ERROR: disconnected from server - remote server may have shut down");
                return;
            }

            if (incomingMsg.getType() == ServerMessage.ServerMessageType.Chat) {
                Quartet<Instant, String, Integer, String> payload =
                    (Quartet<Instant, String, Integer, String>) incomingMsg.getPayload();
                String timeStamp = payload.getValue0().atZone(ZoneId.systemDefault()).format(dateFormat);
                String username = payload.getValue1();
                int userID = payload.getValue2();
                String messageBody = payload.getValue3();
                String signedMsg = username + " #" + userID + "\n" + timeStamp + " | " + messageBody + "\n";
                readBox.addLine(signedMsg);
                readBox.incrementLastMessageNum();
                readBox.removeExcessLines(true);

                if (userID == id ||
                    readBox.getCaretPosition().getRow() > readBox.getLineCount() - readBox.getSize().getRows()
                ){
                    readBox.scrollToBottom();
                }
            }
            else if (incomingMsg.getType() == ServerMessage.ServerMessageType.RecentChatChunk) {
                Triplet<Integer, Integer, String> chunkMessageInfo = buildChunkMessageInfo(incomingMsg, dateFormat);
                readBox.setFirstMessageNum(chunkMessageInfo.getValue0());
                readBox.setLastMessageNum(chunkMessageInfo.getValue1());
                readBox.setText(chunkMessageInfo.getValue2());
                readBox.addLine("");
                readBox.scrollToBottom();
            }
            else if (incomingMsg.getType() == ServerMessage.ServerMessageType.ChatChunk) {
                Triplet<Integer, Integer, String> chunkMessageInfo = buildChunkMessageInfo(incomingMsg, dateFormat);
                int firstMessageNum = chunkMessageInfo.getValue0();
                int lastMessageNum = chunkMessageInfo.getValue1();
                String chunkMessage = chunkMessageInfo.getValue2();

                if (lastMessageNum < readBox.getFirstMessageNum()){
                    readBox.setText(chunkMessage + readBox.getText().trim());
                    readBox.addLine("");
                    readBox.setFirstMessageNum(firstMessageNum);
                    readBox.removeExcessLines(false);
                    readBox.setCaretPosition((lastMessageNum - firstMessageNum) * 3 + 2, 0);
                }
                else if (firstMessageNum > readBox.getLastMessageNum()){
                    readBox.setText(readBox.getText().trim() + "\n\n" + chunkMessage);
                    readBox.addLine("");
                    readBox.setLastMessageNum(lastMessageNum);
                    readBox.removeExcessLines(true);
                    readBox.setCaretPosition((firstMessageNum - readBox.getFirstMessageNum()) * 3 - 1, 0);
                }
            }
            else if (incomingMsg.getType() == ServerMessage.ServerMessageType.IdleTimeout){
                baseWindow.close();
                System.err.println("ERROR: disconnected from server - you were idle for too long");
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Triplet<Integer, Integer, String> buildChunkMessageInfo(ServerMessage incomingMsg, DateTimeFormatter dateFormat){
        Triplet<Integer, Integer, List<Quartet<Instant, String, Integer, String>>> payload =
            (Triplet<Integer, Integer, List<Quartet<Instant, String, Integer, String>>>) incomingMsg.getPayload();
        int firstMessageNum = payload.getValue0();
        int lastMessageNum = payload.getValue1();
        List<Quartet<Instant, String, Integer, String>> messages = payload.getValue2();
        String chunkMessage = "";

        for (int i = 0; i < messages.size(); i++){
            Quartet<Instant, String, Integer, String> message = messages.get(i);
            String timeStamp = message.getValue0().atZone(ZoneId.systemDefault()).format(dateFormat);
            String username = message.getValue1();
            int userID = message.getValue2();
            String messageBody = message.getValue3();
            chunkMessage += username + " #" + userID + "\n" + timeStamp + " | " + messageBody + "\n\n";
        }

        return new Triplet<Integer,Integer,String>(firstMessageNum, lastMessageNum, chunkMessage);
    }
}