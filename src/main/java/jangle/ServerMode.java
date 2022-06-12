package jangle;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerMode {
    private static final String SERVER_LOG_FILENAME = "jangle_server.log";
    private static final String CHAT_lOG_FILENAME = "jangle_chat.log";
    public static final int NUM_MESSAGES_PER_CHUNK = 50;

    public static void startServer(int port) {
        ExecutorService pool = new ThreadPoolExecutor(2, 100, 1, TimeUnit.HOURS, new LinkedBlockingQueue<>());
        Set<UserHandle> activeUsers = ConcurrentHashMap.newKeySet(100);
        Set<UserHandle> inactiveUsers = ConcurrentHashMap.newKeySet(100);

        try (
                ServerSocket ss = new ServerSocket(port);
                PrintWriter upw = new PrintWriter(new FileOutputStream(SERVER_LOG_FILENAME), true);
                PrintWriter cpw = new PrintWriter(new FileOutputStream(CHAT_lOG_FILENAME), true);) {
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
            PrintWriter serverLog, PrintWriter chatlog) {
        AtomicInteger totMessages = new AtomicInteger(0);
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MM/dd/yy HH:mm");

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
                            new Date() + ": Closing duplicate connection from " + ip.toString() + " port "
                                    + s.getPort());
                } catch (IOException ioe) {}
                continue;
            } else {
                serverLog.println(new Date() + ": User from " + ip.toString() + " is new - id set to " + user.getID());
            }

            user.activate(s, serverLog);
            activeUsers.add(user);

            try {
                UserMessage usernameMsg = (UserMessage) user.getObjectInputStream().readObject();
                user.setName((String) usernameMsg.getPayload());
            } catch (IOException | ClassNotFoundException e) {
                serverLog.println(new Date() + ": Could not update name for user " + user.getID());
            }

            try {
                BufferedReader chatLogReader = new BufferedReader(new FileReader(CHAT_lOG_FILENAME));
                pool.execute(() -> sendChatLogChunk(user, chatLogReader, totMessages, totMessages.get(), 50));
            } catch (FileNotFoundException fnfe) {}

            pool.execute(() -> listen(user, totMessages, dateFormat, pool, activeUsers, inactiveUsers, serverLog, chatlog));
        }
    }

    private static void sendChatLogChunk(
            UserHandle user,
            BufferedReader chatLogReader,
            AtomicInteger totMessages,
            int curMessageNum,
            int numMessages) {
        // continue here
    }

    private static void listen(
            UserHandle user,
            AtomicInteger totMessages,
            DateTimeFormatter dateFormat,
            ExecutorService pool,
            Set<UserHandle> activeUsers, Set<UserHandle> inactiveUsers,
            PrintWriter serverLog, PrintWriter chatLog) {
        String username = user.getName() + " #" + user.getID();
        ObjectInputStream in = user.getObjectInputStream();

        while (true) {
            UserMessage serializedMessage;

            try {
                serializedMessage = (UserMessage) in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                serverLog.println(new Date() + ": User " + user.getID()
                        + " has disconnected - closing connection");
                user.deactivate();
                activeUsers.remove(user);
                inactiveUsers.add(user);
                return;
            }

            if (serializedMessage.getType() == UserMessage.MessageType.Chat) {
                serverLog.println(new Date() + ": Received message from user " + user.getID());
                String msg = (String) serializedMessage.getPayload();
                String timestampedMsg = LocalDateTime.now().format(dateFormat) + " | " + msg;
                String signedMsg = username + "\n" + timestampedMsg + "\n";
                pool.execute(() -> disseminate(signedMsg, activeUsers));
                chatLog.println(signedMsg);
                totMessages.incrementAndGet();
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
