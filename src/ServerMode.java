import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServerMode {
    private static Set<UserHandle> activeUsers;
    private static Set<UserHandle> inactiveUsers;
    private static ServerSocket serverSocket;
    private static PrintWriter serverLog;
    private static PrintWriter chatLog;
    private static ExecutorService pool;

    public static void startServer(int port) {
        activeUsers = new HashSet<>();
        inactiveUsers = new HashSet<>();
        try (ServerSocket ss = new ServerSocket(port);
                PrintWriter upw = new PrintWriter(new FileOutputStream("jangle_server.log"), true);
                PrintWriter cpw = new PrintWriter(new FileOutputStream("jangle_chat.log"), true);) {
            serverSocket = ss;
            serverLog = upw;
            chatLog = cpw;
            pool = new ThreadPoolExecutor(2, 100, 1, TimeUnit.HOURS, new LinkedBlockingQueue<>());
            run();
        } catch (IOException ioe) {
            System.err.println("ERROR: could not establish server");
        } catch (InterruptedException ie) {
            pool.shutdownNow();
        }
    }

    private static void run() throws InterruptedException {
        while (true) {
            Socket s;
            try {
                s = serverSocket.accept();
            } catch (IOException ioe) {
                serverLog.println(new Date() + ": Failed to accept incoming connection");
                continue;
            }

            InetAddress ip = s.getInetAddress();
            serverLog.println(
                    new Date() + ": Received connection from " + ip.toString() + " port " + s.getPort());

            UserHandle user = new UserHandle(ip);

            if (inactiveUsers.contains(user)) {
                inactiveUsers.remove(user);
            } else if (activeUsers.contains(user)) {
                try {
                    s.close();
                    serverLog.println(
                        new Date() + ": Closing duplicate connection from " + ip.toString() + " port " + s.getPort());
                } catch (IOException ioe) {}
                continue;
            }

            user.activate(s, serverLog);
            activeUsers.add(user);

            try {
                user.setName(user.getBufferedReader().readLine());
            } catch (IOException ioe) {
                serverLog.println(new Date() + ": Could not update name for user " + user.getID());
            }

            pool.execute(() -> listen(user));
        }
    }

    private static void listen(UserHandle user) {
        String username = user.getName() + " #" + user.getID();
        BufferedReader in = user.getBufferedReader();

        while (true) {
            try {
                String msg = in.readLine();

                // if msg is null, other user has disconnected
                if (msg == null) {
                    serverLog.println(new Date() + ": User " + user.getID()
                            + " has disconnected - closing connection");
                    Socket s = user.getSocket();
                    PrintWriter out = user.getPrintWriter();
                    s.close();
                    in.close();
                    out.close();
                    activeUsers.remove(user);
                    inactiveUsers.add(user);
                    return;
                }

                String signedMsg = "\n" + username + "\n" + msg;
                chatLog.println(signedMsg);
                pool.execute(() -> disseminate(signedMsg));
            } catch (IOException e) {
                serverLog.println(new Date() + ": ERROR: could not read message from user " + user.getID());
            }
        }
    }

    private static void disseminate(String signedMsg) {
        for (UserHandle user : activeUsers) {
            PrintWriter out = user.getPrintWriter();
            out.println(signedMsg);
        }
    }
}
