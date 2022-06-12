package jangle;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Date;

public class UserHandle {
    private final int id;
    private final InetAddress ip;
    private String name;
    private Socket socket;
    private ObjectInputStream serverIn;
    private PrintWriter serverOut;

    public UserHandle(InetAddress ip) {
        this.ip = ip;
        this.id = this.ip.hashCode() % 9000 + 1000;
    }

    public void activate(Socket socket, PrintWriter serverLog) {
        this.socket = socket;
        try {
            this.serverIn = new ObjectInputStream(socket.getInputStream());
            this.serverOut = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException ioe) {
            serverLog.println(new Date() + ": Failed to create streams for user " + id);
        }
    }

    public void deactivate(){
        try {
            serverOut.close();
            serverIn.close();
            socket.close();
        }
        catch (IOException ioe){}
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof UserHandle) {
            return ((UserHandle) o).ip.equals(this.ip);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return ip.hashCode();
    }

    public int getID() {
        return id;
    }

    public InetAddress getIP() {
        return ip;
    }

    public String getName() {
        return name;
    }

    public void setName(String newName) {
        name = newName;
    }

    public Socket getSocket() {
        return socket;
    }

    public ObjectInputStream getObjectInputStream() {
        return serverIn;
    }

    public PrintWriter getPrintWriter() {
        return serverOut;
    }
}
