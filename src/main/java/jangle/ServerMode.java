package jangle;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.javatuples.Quartet;
import org.javatuples.Triplet;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

public class ServerMode {
    private static final String SERVER_LOG_FILENAME = "jangle_server.log";
    private static final String CHAT_lOG_FILENAME = "jangle_chat.log";
    private static final String REDIS_CHAT_LIST_KEY = "CHAT";
    private static final String REDIS_USER_ID_FIELD = "user_id";
    private static final String REDIS_USER_NAME_FIELD = "user_name";
    private static final String REDIS_NUM_USERS_KEY = "num_users";

    public static void startServer(int port, String redis_pass) {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1000,
                1000,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());
        pool.allowCoreThreadTimeOut(true);
        Set<UserHandle> activeUsers = ConcurrentHashMap.newKeySet(100);
        RedisClient redisClient = RedisClient.create("redis://" + redis_pass + "@localhost:6379/0");

        try (
                ServerSocket serverSocket = new ServerSocket(port);
                PrintWriter serverLog = new PrintWriter(new FileOutputStream(SERVER_LOG_FILENAME), true);
                PrintWriter chatLog = new PrintWriter(new FileOutputStream(CHAT_lOG_FILENAME), true);
                StatefulRedisConnection<String, String> conn = redisClient.connect();) {
            RedisCommands<String, String> syncCommands = conn.sync();
            syncCommands.flushdb();
            syncCommands.set(REDIS_NUM_USERS_KEY, "" + 0);
            run(serverSocket, syncCommands, pool, activeUsers, serverLog, chatLog);
        } catch (IOException ioe) {
            System.err.println("ERROR: could not establish server");
        } finally {
            pool.shutdownNow();
            redisClient.shutdown();
            System.out.println();
        }
    }

    private static void sendMessageToUser(UserHandle user, ServerMessage message) throws IOException {
        ObjectOutputStream out = user.getObjectOutputStream();
        synchronized (out) {
            out.writeObject(message);
            out.flush();
        }
    }

    private static void run(
            ServerSocket serverSocket,
            RedisCommands<String, String> syncCommands,
            ExecutorService pool,
            Set<UserHandle> activeUsers,
            PrintWriter serverLog, PrintWriter chatlog) {
        pool.execute(() -> kickIdleUsers(activeUsers, serverLog));

        while (true) {
            Socket s;
            try {
                s = serverSocket.accept();
                s.setTcpNoDelay(true);
            } catch (IOException ioe) {
                serverLog.println(Instant.now() + " : Failed to accept incoming connection");
                continue;
            }

            InetAddress ip = s.getInetAddress();
            serverLog.println(Instant.now() + " : Received connection from " + ip.toString() + " port " + s.getPort());

            UserHandle user = new UserHandle(ip);
            user.activate(s, serverLog);

            if (activeUsers.contains(user)) {
                try {
                    ServerMessage rejectConnectionMessage = new ServerMessage(
                            ServerMessage.ServerMessageType.DisconnectDuplicate, true);
                    sendMessageToUser(user, rejectConnectionMessage);
                    user.deactivate();
                } catch (IOException ioe) {
                }
                serverLog.println(Instant.now() + " : Closing duplicate connection from " + ip.toString() + " port "
                        + s.getPort());
                continue;
            }

            try {
                ServerMessage acceptConnectionMessage = new ServerMessage(
                        ServerMessage.ServerMessageType.DisconnectDuplicate, false);
                sendMessageToUser(user, acceptConnectionMessage);
            } catch (IOException ioe) {
                serverLog.println(Instant.now() + " : Failed to accept connection from user " + user.getID());
                user.deactivate();
                continue;
            }

            try {
                UserMessage usernameMsg = (UserMessage) user.getObjectInputStream().readObject();
                user.setName((String) usernameMsg.getPayload());
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                serverLog.println(
                        Instant.now() + " : Could not get name for user " + user.getID() + " - closing connection");
                user.deactivate();
                continue;
            }

            if (syncCommands.hexists(ip.toString(), "user_id")) {
                syncCommands.hset(ip.toString(), REDIS_USER_NAME_FIELD, user.getName());
            } else {
                Map<String, String> tempMap = new HashMap<>();
                tempMap.put(REDIS_USER_NAME_FIELD, user.getName());
                int numUsers = Integer.parseInt(syncCommands.get(REDIS_NUM_USERS_KEY)) + 1;
                tempMap.put(REDIS_USER_ID_FIELD, "" + (numUsers + 999));
                syncCommands.hset(ip.toString(), tempMap);
                syncCommands.set(REDIS_NUM_USERS_KEY, "" + numUsers);
                user.setID(numUsers + 999);
            }

            serverLog.println(Instant.now() + " : Connection from " + ip.toString() + " port " + s.getPort()
                    + " recognized as user " + user.getID());

            try {
                ServerMessage idMessage = new ServerMessage(ServerMessage.ServerMessageType.ID, user.getID());
                sendMessageToUser(user, idMessage);
            } catch (IOException ioe) {
                serverLog.println(
                        Instant.now() + " : Failed to send ID to user " + user.getID() + " - closing connection");
                user.deactivate();
                continue;
            }

            activeUsers.add(user);
            pool.execute(() -> sendMostRecentChatChunk(user, App.NUM_MESSAGES_PER_CHUNK, syncCommands, serverLog));
            pool.execute(() -> listen(user, syncCommands, pool, activeUsers, serverLog, chatlog));
        }
    }

    private static void listen(
            UserHandle user,
            RedisCommands<String, String> syncCommands,
            ExecutorService pool,
            Set<UserHandle> activeUsers,
            PrintWriter serverLog, PrintWriter chatLog) {
        ObjectInputStream in = user.getObjectInputStream();

        while (true) {
            UserMessage serializedMessage;

            try {
                serializedMessage = (UserMessage) in.readObject();
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                serverLog.println(Instant.now() + " : User " + user.getID() + " has disconnected - closing connection");
                user.deactivate();
                activeUsers.remove(user);
                return;
            }

            if (serializedMessage.getType() == UserMessage.UserMessageType.Chat) {
                Instant receiveTime = Instant.now();
                user.setLastMessageTime(receiveTime);
                String message = (String) serializedMessage.getPayload();

                if (message.length() > App.MAX_CHARS_PER_MESSAGE) {
                    continue;
                }

                message = message.replaceAll("\\s", " ").trim();
                serverLog.println(receiveTime + " : Received message from user " + user.getID());
                Quartet<Instant, String, Integer, String> payload = new Quartet<>(
                        receiveTime,
                        user.getName(),
                        user.getID(),
                        message);

                syncCommands.rpush(REDIS_CHAT_LIST_KEY, receiveTime.toString() + " " + user.getIP() + " "
                        + syncCommands.llen(REDIS_CHAT_LIST_KEY) + " " + message);

                ServerMessage outgoingMessage = new ServerMessage(ServerMessage.ServerMessageType.Chat, payload);
                pool.execute(() -> disseminate(outgoingMessage, activeUsers, serverLog));
                chatLog.println(receiveTime + " " + user.getName() + " " + user.getID() + "\n" + message);

                synchronized (user) {
                    if (user.getDetached()) {
                        pool.execute(() -> sendMostRecentChatChunk(user, App.NUM_MESSAGES_PER_CHUNK, syncCommands,
                                serverLog));
                    }
                }
            } else if (serializedMessage.getType() == UserMessage.UserMessageType.RequestOldMessages ||
                    serializedMessage.getType() == UserMessage.UserMessageType.RequestNewMessages) {
                Instant currChatChunkRequestTime = Instant.now();
                if (Duration.between(user.getLastChatChunkRequestTime(), currChatChunkRequestTime)
                        .getSeconds() < App.SECONDS_BETWEEN_CHUNK_REQUESTS) {
                    continue;
                }

                UserMessage.UserMessageType msgType = serializedMessage.getType();

                user.setLastChatChunkRequestTime(currChatChunkRequestTime);
                pool.execute(() -> sendChatChunk(
                        user,
                        (Integer) serializedMessage.getPayload(),
                        App.NUM_MESSAGES_PER_CHUNK
                                * (msgType == UserMessage.UserMessageType.RequestOldMessages ? -1 : 1),
                        syncCommands,
                        serverLog));
            }
        }
    }

    private static void kickIdleUsers(Set<UserHandle> activeUsers, PrintWriter serverLog) {
        while (true){
            try {
                Thread.sleep(300_000);
            } catch (InterruptedException e) {}

            Instant time = Instant.now();

            for (UserHandle user : activeUsers){
                if (Duration.between(user.getLastMessageTime(), time).getSeconds() > App.USER_KEEPALIVE_SECONDS){
                    serverLog.println(Instant.now() + " : User " + user.getID() + " idle for 60 min");
                    ServerMessage disconnectMsg = new ServerMessage(ServerMessage.ServerMessageType.IdleTimeout, null);
                    try {
                        sendMessageToUser(user, disconnectMsg);
                    } catch (IOException e) {}
                    user.deactivate();
                }
            }
        }
    }

    private static void sendMostRecentChatChunk(
            UserHandle user,
            int numMessages,
            RedisCommands<String, String> syncCommands,
            PrintWriter serverLog) {
        synchronized (user) {
            Triplet<Integer, Integer, List<Quartet<Instant, String, Integer, String>>> chatChunk;
            List<String> messages = syncCommands.lrange(REDIS_CHAT_LIST_KEY, -numMessages, -1);
            chatChunk = buildChunkMessage(syncCommands, messages, true);

            try {
                sendMessageToUser(user, new ServerMessage(ServerMessage.ServerMessageType.RecentChatChunk, chatChunk));
            } catch (IOException e) {
                serverLog.println(Instant.now() + " : Could not send chat chunk to user " + user.getID());
            }

            user.setDetached(false);
        }
    }

    private static void sendChatChunk(
            UserHandle user,
            int curMessageNum,
            int numMessages,
            RedisCommands<String, String> syncCommands,
            PrintWriter serverLog) {
        synchronized (user) {
            if ((!user.getDetached() && numMessages > 0)
                    || numMessages == 0
                    || curMessageNum <= 1) {
                return;
            }

            int firstMessageNum;
            int lastMessageNum;

            if (numMessages < 0) {
                firstMessageNum = curMessageNum + numMessages;
                lastMessageNum = curMessageNum - 1;
            } else {
                firstMessageNum = curMessageNum + 1;
                lastMessageNum = curMessageNum + numMessages;
            }

            long chatLength = syncCommands.llen(REDIS_CHAT_LIST_KEY);
            Triplet<Integer, Integer, List<Quartet<Instant, String, Integer, String>>> chatChunk;
            List<String> messages = syncCommands.lrange(REDIS_CHAT_LIST_KEY, firstMessageNum - 1, lastMessageNum - 1);
            chatChunk = buildChunkMessage(syncCommands, messages, false);

            try {
                sendMessageToUser(user, new ServerMessage(ServerMessage.ServerMessageType.ChatChunk, chatChunk));
            } catch (IOException e) {
                serverLog.println(Instant.now() + " : Could not send chat chunk to user " + user.getID());
            }

            if (chatChunk.getValue1() >= chatLength) {
                user.setDetached(false);
            } else {
                user.setDetached(true);
            }
        }
    }

    private static Triplet<Integer, Integer, List<Quartet<Instant, String, Integer, String>>> buildChunkMessage(
            RedisCommands<String, String> syncCommands, List<String> queryResults, boolean isRecentChunk) {
        int chunkFirstMessageNum = isRecentChunk ? 1 : 0;
        int chunkLastMessageNum = 0;
        List<Quartet<Instant, String, Integer, String>> messages = new ArrayList<>();

        for (int i = 0; i < queryResults.size(); i++) {
            String message = queryResults.get(i);
            String[] msgComponents = message.split(" ", 4);

            if (i == 0) {
                chunkFirstMessageNum = Integer.parseInt(msgComponents[2]) + 1;
            }
            if (i == queryResults.size() - 1) {
                chunkLastMessageNum = Integer.parseInt(msgComponents[2]) + 1;
            }

            messages.add(new Quartet<Instant, String, Integer, String>(
                    Instant.parse(msgComponents[0]),
                    syncCommands.hget(msgComponents[1], REDIS_USER_NAME_FIELD),
                    Integer.parseInt(syncCommands.hget(msgComponents[1], REDIS_USER_ID_FIELD)),
                    msgComponents[3]));
        }

        return new Triplet<Integer, Integer, List<Quartet<Instant, String, Integer, String>>>(
                chunkFirstMessageNum,
                chunkLastMessageNum,
                messages);
    }

    private static void disseminate(ServerMessage message, Set<UserHandle> activeUsers, PrintWriter serverLog) {
        for (UserHandle user : activeUsers) {
            synchronized (user) {
                if (!user.getDetached()) {
                    try {
                        sendMessageToUser(user, message);
                    } catch (IOException ioe) {
                        serverLog.println(Instant.now() + " : Failed to send message to user " + user.getID());
                    }
                }
            }
        }
    }
}
