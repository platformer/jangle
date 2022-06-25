package jangle;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.javatuples.Quartet;
import org.javatuples.Triplet;

public class ServerMode {
    private static final String SERVER_LOG_FILENAME = "jangle_server.log";
    private static final String CHAT_lOG_FILENAME = "jangle_chat.log";

    public static void startServer(String jangle_app_password, int port) {
        ExecutorService pool = new ThreadPoolExecutor(3, 100, 1, TimeUnit.HOURS, new LinkedBlockingQueue<>());
        Set<UserHandle> activeUsers = ConcurrentHashMap.newKeySet(100);

        try (
                ServerSocket serverSocket = new ServerSocket(port);
                PrintWriter serverLog = new PrintWriter(new FileOutputStream(SERVER_LOG_FILENAME), true);
                PrintWriter chatLog = new PrintWriter(new FileOutputStream(CHAT_lOG_FILENAME), true);
                Connection conn = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/jangle_app",
                    "jangle_app",
                    jangle_app_password
                );
            ) {
            conn.setAutoCommit(true);

            try (Statement stmt = conn.createStatement()) {
                String sql = "DROP TABLE IF EXISTS jangle_user CASCADE";
                stmt.execute(sql);
            } catch (SQLException sqle){
                serverLog.println(Instant.now() + " : Failed to drop old jangle_user table");
                return;
            }

            try (Statement stmt = conn.createStatement()) {
                String sql =
                    "CREATE TABLE IF NOT EXISTS jangle_user " +
                    "(user_id   INT     PRIMARY KEY GENERATED ALWAYS AS IDENTITY " +
                        "(START WITH 1000 INCREMENT BY 1), " +
                    " ip        TEXT    UNIQUE NOT NULL, " +
                    " name      TEXT    NOT NULL) ";
                stmt.execute(sql);
            } catch (SQLException sqle){
                serverLog.println(Instant.now() + " : Failed to create jangle_user table");
                return;
            }

            try (Statement stmt = conn.createStatement()) {
                String sql = "DROP TABLE IF EXISTS jangle_chat";
                stmt.execute(sql);
            } catch (SQLException sqle){
                serverLog.println(Instant.now() + " : Failed to drop old jangle_chat table");
                return;
            }

            try (Statement stmt = conn.createStatement()) {
                String sql =
                    "CREATE TABLE IF NOT EXISTS jangle_chat " +
                    "(chat_id   INT     PRIMARY KEY GENERATED ALWAYS AS IDENTITY " +
                        "(START WITH 1 INCREMENT BY 1), " +
                    " time      TEXT    NOT NULL, " +
                    " ip        TEXT    NOT NULL, " +
                    " body      TEXT    NOT NULL, " +
                    " FOREIGN KEY(ip) REFERENCES jangle_user(ip) " +
                        "ON DELETE CASCADE)";
                stmt.execute(sql);
            } catch (SQLException sqle){
                serverLog.println(Instant.now() + " : Failed to create jangle_chat table");
                return;
            }
            
            run(serverSocket, conn, pool, activeUsers, serverLog, chatLog);
        } catch (IOException ioe) {
            System.err.println("ERROR: could not establish server");
        } catch (SQLException sqle) {
            System.err.println("ERROR: could not connect to Postgres server");
        } finally {
            pool.shutdownNow();
            System.out.println();
        }
    }

    private static void sendMessageToUser(UserHandle user, ServerMessage message) throws IOException{
        ObjectOutputStream out = user.getObjectOutputStream();
        synchronized (out){
            out.writeObject(message);
            out.flush();
        }
    }

    private static void run(
            ServerSocket serverSocket,
            Connection conn,
            ExecutorService pool,
            Set<UserHandle> activeUsers,
            PrintWriter serverLog, PrintWriter chatlog) {
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
                    ServerMessage rejectConnectionMessage = new ServerMessage(ServerMessage.ServerMessageType.DisconnectDuplicate, true);
                    sendMessageToUser(user, rejectConnectionMessage);
                    user.deactivate();
                } catch (IOException ioe) {}
                serverLog.println(Instant.now() + " : Closing duplicate connection from " + ip.toString() + " port " + s.getPort());
                continue;
            }

            try {
                ServerMessage acceptConnectionMessage = new ServerMessage(ServerMessage.ServerMessageType.DisconnectDuplicate, false);
                sendMessageToUser(user, acceptConnectionMessage);
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

            try {
                String sql =
                    "INSERT INTO jangle_user (ip, name) VALUES(?, ?) " +
                    "ON CONFLICT (ip) DO " +
                        "UPDATE SET name = EXCLUDED.name";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, user.getIP().toString());
                    stmt.setString(2, user.getName());
                    stmt.execute();
                }

                sql =
                    "SELECT user_id " +
                    "FROM jangle_user " +
                    "WHERE ip = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, user.getIP().toString());
                    
                    try (ResultSet idResult = stmt.executeQuery()) {
                        idResult.next();
                        user.setID(idResult.getInt(1));
                    }
                }
            } catch (SQLException sqle){
                serverLog.println(Instant.now() + " : Could add/update user " + user.getID() + " in database - closing connection");
                user.deactivate();
                continue;
            }

            serverLog.println(Instant.now() + " : Connection from " + ip.toString() + " port " + s.getPort()
                + " recognized as user " + user.getID());

            try {
                ServerMessage idMessage = new ServerMessage(ServerMessage.ServerMessageType.ID, user.getID());
                sendMessageToUser(user, idMessage);
            }
            catch (IOException ioe){
                serverLog.println(Instant.now() + " : Failed to send ID to user " + user.getID() + " - closing connection");
                user.deactivate();
                continue;
            }

            activeUsers.add(user);
            pool.execute(() -> sendMostRecentChatChunk(user, App.NUM_MESSAGES_PER_CHUNK, conn, serverLog));
            pool.execute(() -> listen(user, conn, pool, activeUsers, serverLog, chatlog));
        }
    }

    private static void listen(
            UserHandle user,
            Connection conn,
            ExecutorService pool,
            Set<UserHandle> activeUsers,
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
                return;
            }

            if (serializedMessage.getType() == UserMessage.UserMessageType.Chat) {
                Instant receiveTime = Instant.now();
                String message = (String) serializedMessage.getPayload();

                if (message.length() > App.MAX_CHARS_PER_MESSAGE){
                    continue;
                }

                message = message.replaceAll("\\s", " ").trim();
                serverLog.println(receiveTime + " : Received message from user " + user.getID());
                Quartet<Instant, String, Integer, String> payload = new Quartet<>(
                    receiveTime,
                    user.getName(),
                    user.getID(),
                    message
                );

                String sql = "INSERT INTO jangle_chat (time, ip, body) VALUES(?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)){
                    stmt.setString(1, receiveTime.toString());
                    stmt.setString(2, user.getIP().toString());
                    stmt.setString(3, message);
                    stmt.execute();
                } catch (SQLException sqle){
                    serverLog.println(Instant.now() + " : Could not commit message to database");
                    return;
                }

                ServerMessage outgoingMessage = new ServerMessage(ServerMessage.ServerMessageType.Chat, payload);
                pool.execute(() -> disseminate(outgoingMessage, activeUsers, serverLog));
                chatLog.println(receiveTime + " " + user.getName() + " " + user.getID() + "\n" + message);

                synchronized (user){
                    if (user.getDetached()){
                        pool.execute(() -> sendMostRecentChatChunk(user, App.NUM_MESSAGES_PER_CHUNK, conn, serverLog));
                    }
                }
            }
            else if (serializedMessage.getType() == UserMessage.UserMessageType.RequestOldMessages ||
                     serializedMessage.getType() == UserMessage.UserMessageType.RequestNewMessages){
                Instant currChatChunkRequestTime = Instant.now();
                if (Duration.between(user.getLastChatChunkRequestTime(), currChatChunkRequestTime).getSeconds()
                    < App.SECONDS_BETWEEN_CHUNK_REQUESTS){
                    continue;
                }

                UserMessage.UserMessageType msgType = serializedMessage.getType();

                user.setLastChatChunkRequestTime(currChatChunkRequestTime);
                pool.execute(() -> sendChatChunk(
                    user,
                    (Integer) serializedMessage.getPayload(),
                    App.NUM_MESSAGES_PER_CHUNK * (msgType == UserMessage.UserMessageType.RequestOldMessages? -1 : 1),
                    conn,
                    serverLog
                ));
            }
        }
    }

    private static void sendMostRecentChatChunk(
            UserHandle user,
            int numMessages,
            Connection conn,
            PrintWriter serverLog) {
        synchronized (user){
            Triplet<Integer, Integer, List<Quartet<Instant, String, Integer, String>>> chatChunk;

            String sql =
                "SELECT * FROM (" +
                    "SELECT " +
                        "jangle_chat.chat_id AS chat_id, " +
                        "jangle_chat.time AS time, " +
                        "jangle_user.name AS name, " +
                        "jangle_user.user_id AS user_id, " +
                        "jangle_chat.body AS body " +
                    "FROM jangle_chat, jangle_user " +
                    "WHERE jangle_chat.ip = jangle_user.ip " +
                    "ORDER BY chat_id DESC LIMIT ?) AS jangle_app_chat_chunk " +
                "ORDER BY chat_id ASC";

            try (PreparedStatement chatChunkStmt = conn.prepareStatement(sql)){
                chatChunkStmt.setInt(1, App.NUM_MESSAGES_PER_CHUNK);

                try (ResultSet chatChunkResults = chatChunkStmt.executeQuery()){
                    chatChunk = buildChunkMessage(chatChunkResults, true);
                }
            } catch (SQLException sqle){
                serverLog.println(Instant.now() + " : Could not read chat messages in database");
                return;
            }

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
            Connection conn,
            PrintWriter serverLog) {
        synchronized (user){
            if ((!user.getDetached() && numMessages > 0)
                || numMessages == 0
                || curMessageNum <= 1
            ){
                return;
            }

            int firstMessageNum;
            int lastMessageNum;

            if (numMessages < 0){
                firstMessageNum = curMessageNum + numMessages;
                lastMessageNum = curMessageNum - 1;
            }
            else {
                firstMessageNum = curMessageNum + 1;
                lastMessageNum = curMessageNum + numMessages;
            }

            int chatLength = 0;

            try (Statement chatLengthStmt = conn.createStatement()){
                String sql = "SELECT MAX (chat_id) FROM jangle_chat";

                try (ResultSet chatLengthResults = chatLengthStmt.executeQuery(sql)) {
                    chatLengthResults.next();
                    chatLength = chatLengthResults.getInt(1);
                }
            } catch (SQLException sqle){
                serverLog.println(Instant.now() + " : Could not read chat length from database");
                return;
            }

            Triplet<Integer, Integer, List<Quartet<Instant, String, Integer, String>>> chatChunk;
            
            String sql =
                "SELECT " +
                    "jangle_chat.chat_id AS chat_id, " +
                    "jangle_chat.time AS time, " +
                    "jangle_user.name AS name, " +
                    "jangle_user.user_id AS user_id, " +
                    "jangle_chat.body AS body " +
                "FROM jangle_chat, jangle_user " +
                "WHERE " +
                    "jangle_chat.ip = jangle_user.ip AND " +
                    "jangle_chat.chat_id >= ? AND " +
                    "jangle_chat.chat_id <= ? " +
                "ORDER BY chat_id ASC";
            
            try (PreparedStatement chatChunkStmt = conn.prepareStatement(sql)){
                chatChunkStmt.setInt(1, firstMessageNum);
                chatChunkStmt.setInt(2, lastMessageNum);

                try (ResultSet chatChunkResults = chatChunkStmt.executeQuery()){
                    chatChunk = buildChunkMessage(chatChunkResults, false);
                }
            } catch (SQLException sqle){
                serverLog.println(Instant.now() + " : Could not read chat messages in database");
                return;
            }

            try {
                sendMessageToUser(user, new ServerMessage(ServerMessage.ServerMessageType.ChatChunk, chatChunk));
            } catch (IOException e) {
                serverLog.println(Instant.now() + " : Could not send chat chunk to user " + user.getID());
            }

            if (chatChunk.getValue1() >= chatLength){
                user.setDetached(false);
            }
            else {
                user.setDetached(true);
            }
        }
    }

    private static Triplet<Integer, Integer, List<Quartet<Instant, String, Integer, String>>>
    buildChunkMessage(ResultSet chatChunkResults, boolean isRecentChunk) throws SQLException{
        int chunkFirstMessageNum = isRecentChunk? 1 : 0;
        int chunkLastMessageNum = 0;
        List<Quartet<Instant, String, Integer, String>> messages = new ArrayList<>();

        while (chatChunkResults.next()){
            if (chatChunkResults.isFirst()){
                chunkFirstMessageNum = chatChunkResults.getInt("chat_id");
            }
            if (chatChunkResults.isLast()){
                chunkLastMessageNum = chatChunkResults.getInt("chat_id");
            }

            messages.add(new Quartet<Instant,String,Integer,String>(
                Instant.parse(chatChunkResults.getString("time")),
                chatChunkResults.getString("name"),
                chatChunkResults.getInt("user_id"),
                chatChunkResults.getString("body")
            ));
        }

        return new Triplet<Integer, Integer, List<Quartet<Instant, String, Integer, String>>>(
            chunkFirstMessageNum,
            chunkLastMessageNum,
            messages
        );
    }

    private static void disseminate(ServerMessage message, Set<UserHandle> activeUsers, PrintWriter serverLog) {
        for (UserHandle user : activeUsers) {
            synchronized (user){
                if (!user.getDetached()){
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
