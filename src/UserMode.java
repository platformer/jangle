import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

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
        PrintWriter out;
        try {
            s = new Socket(host, port);
            out = new PrintWriter(s.getOutputStream(), true);
        } catch (IOException ioe) {
            System.err.println("ERROR: could not connect to server at " + host);
            return;
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e1) {}

        out.println(username);

        // if (out.checkError()) {
        //     try {
        //         out.close();
        //         s.close();
        //     } catch (IOException ioe) {}

        //     return;
        // }

        try {
            s.setSoTimeout(1000);
            if (s.getInputStream().read() == -1){
                s.close();
                System.err.println("ERROR: disconnected from server - are you already connected to this server?");
                return;
            }
        }
        catch (IOException ioe){}
        finally {
            try {
                s.setSoTimeout(0);
            }
            catch (IOException ioe2){}
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
                            Window.Hint.NO_POST_RENDERING));
            baseWindow.setTheme(new SimpleTheme(TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));

            Panel mainPanel = new Panel(new GridLayout(1));

            mainPanel.addComponent(
                    new TextBox(new TerminalSize(1, 1), TextBox.Style.MULTI_LINE),
                    GridLayout.createLayoutData(
                            GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true, true, 50, 15));
            mainPanel.addComponent(
                    new MessageEditorTextBox(username, out, new TerminalSize(1, 2), TextBox.Style.SINGLE_LINE),
                    GridLayout.createLayoutData(
                            GridLayout.Alignment.FILL, GridLayout.Alignment.FILL, true, false, 50, 3));

            List<Component> panelComponents = mainPanel.getChildrenList();
            TextBox readBox = (TextBox) panelComponents.get(0);
            TextBox writeBox = (TextBox) panelComponents.get(1);

            readBox.setReadOnly(true);

            baseWindow.setComponent(mainPanel);
            writeBox.takeFocus();

            Thread receiveThread = new Thread(() -> receive(s, readBox));
            receiveThread.start();
            textGUI.addWindowAndWait(baseWindow);
            receiveThread.interrupt();
        } catch (IOException ioe) {
            System.err.println("ERROR: Failed to start application");
        }

        try {
            s.close();
        } catch (IOException e) {}
    }

    private static void receive(Socket s, TextBox readBox) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));) {
            while (true) {
                String incomingMsg = in.readLine();
                readBox.addLine(incomingMsg);
            }
        } catch (IOException ioe) {
            System.err.println("ERROR: could not open input stream - closing connection");
        }
    }
}
