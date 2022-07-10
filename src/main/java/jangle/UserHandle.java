package jangle;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.Date;

public class UserHandle {
    private final InetAddress ip;
    private int id;
    private String name;
    private Socket socket;
    private ObjectInputStream serverIn;
    private ObjectOutputStream serverOut;
    private volatile boolean isDetached;
    private volatile Instant lastChatChunkRequestTime;
    private volatile Instant lastMessageTime;

    public UserHandle(InetAddress ip) {
        this.ip = ip;
        this.isDetached = true;
        lastChatChunkRequestTime = Instant.now();
        lastMessageTime = Instant.now();
    }

    public void activate(Socket socket, PrintWriter serverLog) {
        this.socket = socket;
        try {
            this.serverOut = new ObjectOutputStream(socket.getOutputStream());
            this.serverIn = new ObjectInputStream(socket.getInputStream());
        } catch (IOException ioe) {
            serverLog.println(new Date() + ": Failed to create streams for user " + id);
        }
    }

    public synchronized void deactivate(){
        try {
            serverIn.close();
            serverOut.close();
            socket.close();
        }
        catch (IOException ioe){}
    }

    @Override
    public synchronized boolean equals(Object o) {
        if (o instanceof UserHandle) {
            return ((UserHandle) o).ip.equals(this.ip);
        }
        return false;
    }

    @Override
    public synchronized int hashCode() {
        return ip.hashCode();
    }

    public synchronized InetAddress getIP() {
        return ip;
    }

    public synchronized int getID() {
        return id;
    }

    public synchronized void setID(int id){
        this.id = id;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized void setName(String newName) {
        name = newName;
    }

    public synchronized Socket getSocket() {
        return socket;
    }

    public synchronized ObjectInputStream getObjectInputStream() {
        return serverIn;
    }

    public synchronized ObjectOutputStream getObjectOutputStream() {
        return serverOut;
    }

    public synchronized boolean getDetached(){
        return isDetached;
    }

    public synchronized void setDetached(boolean isDetached){
        this.isDetached = isDetached;
    }

    public synchronized Instant getLastChatChunkRequestTime(){
        return lastChatChunkRequestTime;
    }

    public synchronized void setLastChatChunkRequestTime(Instant newChatChunkRequestTime){
        lastChatChunkRequestTime = newChatChunkRequestTime;
    }

    public synchronized Instant getLastMessageTime() {
        return lastMessageTime;
    }

    public synchronized void setLastMessageTime(Instant newLastMessageTime) {
        lastMessageTime = newLastMessageTime;
    }
}
