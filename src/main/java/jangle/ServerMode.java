package jangle;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
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

import org.javatuples.Pair;
import org.javatuples.Quartet;

import io.lettuce.core.Range;
import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

public class ServerMode {
    private static final String SERVER_LOG_FILENAME = "JANGLE_SERVER.log";
    private static final String CHAT_LOG_FILENAME = "JANGLE_CHAT.LOG";
    private static final String CHAT_LOG_KEY = "JANGLE_CHAT_LOG";
    private static final String USER_NAME_KEY = "user";
    private static final String USER_ID_KEY = "id";
    private static final String MSG_IP_ADDR_KEY = "IP_ADDR";
    private static final String MSG_TIME_KEY = "INSTANT";
    private static final String MSG_BODY_KEY = "BODY";
    public static final int NUM_MESSAGES_PER_CHUNK = 50;

    public static void startServer(int port) {
        ExecutorService pool = new ThreadPoolExecutor(2, 100, 1, TimeUnit.HOURS, new LinkedBlockingQueue<>());
        RedisClient redisClient = RedisClient.create("redis://password@localhost:6379/0");
        StatefulRedisConnection<String, String> redisConnection = redisClient.connect();
        RedisCommands<String, String> syncCommands = redisConnection.sync();
        Set<UserHandle> activeUsers = ConcurrentHashMap.newKeySet(100);

        try (
                ServerSocket serverSocket = new ServerSocket(port);
                PrintWriter serverLog = new PrintWriter(new FileOutputStream(SERVER_LOG_FILENAME), true);
                PrintWriter chatLog = new PrintWriter(new FileOutputStream(CHAT_LOG_FILENAME), true);
            ) {
            run(serverSocket, pool, syncCommands, activeUsers, serverLog, chatLog);
        } catch (IOException ioe) {
            System.err.println("ERROR: could not establish server");
        } finally {
            pool.shutdownNow();
            redisConnection.close();
            redisClient.shutdown();
        }
    }

    private static void run(
            ServerSocket serverSocket,
            ExecutorService pool,
            RedisCommands<String, String> syncCommands,
            Set<UserHandle> activeUsers,
            PrintWriter serverLog, PrintWriter chatLog) {
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

            if (activeUsers.contains(user)) {
                try {
                    ServerMessage rejectConnectionMessage = new ServerMessage(
                            ServerMessage.ServerMessageType.DisconnectDuplicate, true);
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
            } catch (IOException ioe) {
                serverLog.println(Instant.now() + " : Failed to accept connection from user " + user.getID());
                user.deactivate();
                continue;
            }

            try {
                UserMessage usernameMsg = (UserMessage) user.getObjectInputStream().readObject();
                user.setName((String) usernameMsg.getPayload());
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                serverLog.println(Instant.now() + " : Could not get name for user " + user.getID() + " - closing connection");
                user.deactivate();
                continue;
            }

            Map<String, String> userInfo = new HashMap<>();
            userInfo.put(USER_NAME_KEY, user.getName());
            userInfo.put(USER_ID_KEY, "" + user.getID());
            syncCommands.hset(user.getIP().toString(), userInfo);

            activeUsers.add(user);
            pool.execute(() -> sendChatLogChunk(user, syncCommands.xlen(CHAT_LOG_KEY), -50, syncCommands, serverLog));
            pool.execute(() -> listen(user, chatDateFormat, pool, syncCommands, activeUsers, serverLog, chatLog));
        }
    }

    private static void sendChatLogChunk(
            UserHandle user,
            long curMessageNum,
            int numMessages,
            RedisCommands<String, String> syncCommands,
            PrintWriter serverLog) {
        if ((curMessageNum == 0 && numMessages < 0)
            || (!user.isDetached() && numMessages > 0)){
            return;
        }
        
        Map<String, Pair<String, Integer>> userMap = new HashMap<>();
        List<StreamMessage<String, String>> streamMessages;
        long lastMessageNum;
        
        if (numMessages < 0){
            streamMessages = syncCommands.xrange(CHAT_LOG_KEY, Range.create("" + (curMessageNum - 50), "" + curMessageNum));
            lastMessageNum = curMessageNum - 50;
        }
        else {
            streamMessages = syncCommands.xrange(CHAT_LOG_KEY, Range.create("" + curMessageNum, "" + (curMessageNum + 50)));
            lastMessageNum = curMessageNum + 50;
        }

        long numTotMessages = syncCommands.xlen(CHAT_LOG_KEY);

        if (lastMessageNum < numTotMessages){
            user.setDetached(true);
        }
        else {
            user.setDetached(false);
        }

        List<Quartet<Instant, String, Integer, String>> chunk = new ArrayList<>();

        for (int i = 0; i < streamMessages.size(); i++){
            Map<String, String> msg = streamMessages.get(i).getBody();
            String ip = msg.get(MSG_IP_ADDR_KEY);
            String time = msg.get(MSG_TIME_KEY);
            String body = msg.get(MSG_BODY_KEY);
            String username;
            Integer id;
            
            if (userMap.containsKey(ip)){
                Pair<String, Integer> userDesc = userMap.get(ip);
                username = userDesc.getValue0();
                id = userDesc.getValue1();
            }
            else {
                Map<String, String> userDesc = syncCommands.hgetall(ip);
                username = userDesc.get(USER_NAME_KEY);
                id = Integer.parseInt(userDesc.get(USER_ID_KEY));
                userMap.put(ip, new Pair<String,Integer>(username, id));
            }

            chunk.add(new Quartet<Instant,String,Integer,String>(Instant.parse(time), username, id, body));
        }

        try {
            user.getObjectOutputStream().writeObject(chunk);
        } catch (IOException ioe) {
            serverLog.println(Instant.now() + " : Failed to send chunk to user " + user.getID());
        }
    }

    private static void listen(
            UserHandle user,
            DateTimeFormatter dateFormat,
            ExecutorService pool,
            RedisCommands<String, String> syncCommands,
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

                    long numMessages = syncCommands.xlen(CHAT_LOG_KEY);
                    Map<String, String> messageMap = new HashMap<>();
                    messageMap.put(MSG_TIME_KEY, receiveTime.toString());
                    messageMap.put(MSG_IP_ADDR_KEY, user.getIP().toString());
                    messageMap.put(MSG_BODY_KEY, message);
                    syncCommands.xadd(CHAT_LOG_KEY, new XAddArgs().id(numMessages + 1 + ""), messageMap);

                    chatLog.println(receiveTime + " " + user.getName() + " " + user.getID() + "\n" + message);
                    break;

                default:
            }
        }
    }

    private static void disseminate(ServerMessage message, Set<UserHandle> activeUsers, PrintWriter serverLog) {
        for (UserHandle user : activeUsers) {
            if (!user.isDetached()) {
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
}
