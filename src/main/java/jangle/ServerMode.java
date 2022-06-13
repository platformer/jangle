package jangle;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.javatuples.Quartet;

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
        DateTimeFormatter chatDateFormat = DateTimeFormatter.ofPattern("MM/dd/yy HH:mm");

        while (true) {
            Socket s;
            try {
                s = serverSocket.accept();
            } catch (IOException ioe) {
                serverLog.println(Instant.now() + " : Failed to accept incoming connection");
                continue;
            }

            InetAddress ip = s.getInetAddress();
            serverLog.println(Instant.now() + " : Received connection from " + ip.toString() + " port " + s.getPort());

            UserHandle user = new UserHandle(ip);
            user.activate(s, serverLog);

            if (inactiveUsers.contains(user)) {
                inactiveUsers.remove(user);
            } else if (activeUsers.contains(user)) {
                try {
                    ServerMessage rejectConnectionMessage = new ServerMessage(ServerMessage.ServerMessageType.DisconnectDuplicate, true);
                    user.getObjectOutputStream().writeObject(rejectConnectionMessage);
                    user.getObjectOutputStream().flush();
                    user.deactivate();
                } catch (IOException ioe) {}
                serverLog.println(Instant.now() + " : Closing duplicate connection from " + ip.toString() + " port " + s.getPort());
                continue;
            } else {
                serverLog.println(Instant.now() + " : User from " + ip.toString() + " is new - id set to " + user.getID());
            }

            try {
                ServerMessage acceptConnectionMessage = new ServerMessage(ServerMessage.ServerMessageType.DisconnectDuplicate, false);
                user.getObjectOutputStream().writeObject(acceptConnectionMessage);
                user.getObjectOutputStream().flush();
            }
            catch (IOException ioe){
                serverLog.println(Instant.now() + " : Failed to accept connection from user " + user.getID());
                user.deactivate();
                continue;
            }

            try {
                UserMessage usernameMsg = (UserMessage) user.getObjectInputStream().readObject();
                user.setName((String) usernameMsg.getPayload());
            } catch (IOException | ClassNotFoundException e) {
                serverLog.println(Instant.now() + " : Could not get name for user " + user.getID() + " - closing connection");
                user.deactivate();
                continue;
            }

            activeUsers.add(user);

            try {
                BufferedReader chatLogReader = new BufferedReader(new FileReader(CHAT_lOG_FILENAME));
                pool.execute(() -> sendChatLogChunk(user, chatLogReader, totMessages, totMessages.get(), 50));
            } catch (FileNotFoundException fnfe) {
                fnfe.printStackTrace();
                return;
            }

            pool.execute(() -> listen(user, totMessages, chatDateFormat, pool, activeUsers, inactiveUsers, serverLog, chatlog));
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
        ObjectInputStream in = user.getObjectInputStream();

        while (true) {
            UserMessage serializedMessage;

            try {
                serializedMessage = (UserMessage) in.readObject();
            } catch (IOException | ClassNotFoundException e) {
                serverLog.println(Instant.now() + " : User " + user.getID() + " has disconnected - closing connection");
                user.deactivate();
                activeUsers.remove(user);
                inactiveUsers.add(user);
                return;
            }

            switch (serializedMessage.getType()) {
                case Chat:
                    Instant receiveTime = Instant.now();
                    String message = (String) serializedMessage.getPayload();
                    serverLog.println(receiveTime + " : Received message from user " + user.getID());
                    Quartet<Instant, String, Integer, String> payload = new Quartet<>(
                            receiveTime,
                            user.getName(),
                            user.getID(),
                            message);
                    ServerMessage outgoingMessage = new ServerMessage(ServerMessage.ServerMessageType.Chat, payload);
                    pool.execute(() -> disseminate(outgoingMessage, activeUsers, serverLog));
                    chatLog.println(receiveTime + " " + user.getName() + " " + user.getID() + "\n" + message);
                    totMessages.incrementAndGet();
                    break;

                default:
            }
        }
    }

    private static void disseminate(ServerMessage message, Set<UserHandle> activeUsers, PrintWriter serverLog) {
        for (UserHandle user : activeUsers) {
            try {
                ObjectOutputStream out = user.getObjectOutputStream();
                out.writeObject(message);
                out.flush();
            } catch (IOException ioe) {
                serverLog.println(Instant.now() + " : Failed to send message to user " + user.getID());
            }
        }
    }
}
