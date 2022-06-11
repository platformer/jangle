package jangle;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServerMode {
    private static final String SERVER_LOG_FILENAME = "jangle_server.log";
    private static final String CHAT_lOG_FILENAME = "jangle_chat.log";

    public static void startServer(int port) {
        ExecutorService pool = new ThreadPoolExecutor(2, 100, 1, TimeUnit.HOURS, new LinkedBlockingQueue<>());
        Set<UserHandle> activeUsers = ConcurrentHashMap.newKeySet(100);
        Set<UserHandle> inactiveUsers = ConcurrentHashMap.newKeySet(100);

        try (   
                ServerSocket ss = new ServerSocket(port);
                PrintWriter upw = new PrintWriter(new FileOutputStream(SERVER_LOG_FILENAME), true);
                PrintWriter cpw = new PrintWriter(new FileOutputStream(CHAT_lOG_FILENAME), true);
            ){
            ServerSocket serverSocket = ss;
            PrintWriter serverLog = upw;
            PrintWriter chatLog = cpw;
            run(pool, activeUsers, inactiveUsers, serverSocket, serverLog, chatLog);
        } catch (IOException ioe) {
            System.err.println("ERROR: could not establish server");
        } finally {
            pool.shutdownNow();
        }
    }

    private static void run(
        ExecutorService pool,
        Set<UserHandle> activeUsers, Set<UserHandle> inactiveUsers,
        ServerSocket serverSocket,
        PrintWriter serverLog, PrintWriter chatlog
    ) {
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
            else {
                serverLog.println(new Date() + "User from " + ip.toString() + "is new - id set to " + user.getID());
            }

            user.activate(s, serverLog);
            activeUsers.add(user);

            try {
                user.setName(user.getBufferedReader().readLine());
            } catch (IOException ioe) {
                serverLog.println(new Date() + ": Could not update name for user " + user.getID());
            }

            pool.execute(() -> listen(user, pool, activeUsers, inactiveUsers, serverLog, chatlog));
        }
    }

    private static void listen(
        UserHandle user,
        ExecutorService pool,
        Set<UserHandle> activeUsers, Set<UserHandle> inactiveUsers,
        PrintWriter serverLog, PrintWriter chatLog
    ) {
        String username = user.getName() + " #" + user.getID();
        BufferedReader in = user.getBufferedReader();

        while (true) {
            try {
                String msg = in.readLine();
                serverLog.println(new Date() + "Received message from user " + user.getID());

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

                String signedMsg = username + "\n" + msg + "\n";
                chatLog.println(signedMsg);
                pool.execute(() -> disseminate(signedMsg, activeUsers));
            } catch (IOException e) {
                serverLog.println(new Date() + ": ERROR: could not read message from user " + user.getID());
            }
        }
    }

    private static void disseminate(String signedMsg, Set<UserHandle> activeUsers) {
        for (UserHandle user : activeUsers) {
            PrintWriter out = user.getPrintWriter();
            out.println(signedMsg);
        }
    }
}
