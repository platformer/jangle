package jangle;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

public class UserHandle {
    private final InetAddress ip;
    private final ReentrantLock lock;
    private int id;
    private String name;
    private Socket socket;
    private ObjectInputStream serverIn;
    private ObjectOutputStream serverOut;
    private volatile boolean isDetached;
    private volatile Instant lastChatChunkRequestTime;

    public UserHandle(InetAddress ip) {
        this.ip = ip;
        this.lock = new ReentrantLock(true);
        this.isDetached = true;
        lastChatChunkRequestTime = Instant.EPOCH;
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
        } catch (IOException ioe) {
        }
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

    public synchronized ReentrantLock getLock() {
        return lock;
    }

    public synchronized int getID() {
        return id;
    }

    public void setID(int id){
        this.id = id;
    }

    public synchronized String getName() {
        return name;
    }

    public void setName(String newName) {
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
}
