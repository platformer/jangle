import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Date;

public class UserHandle {
    private final int id;
    private final InetAddress ip;
    private String name;
    private Socket socket;
    BufferedReader serverIn;
    PrintWriter serverOut;

    public UserHandle(InetAddress ip) {
        this.ip = ip;
        this.id = this.ip.hashCode() % 9000 + 1000;
    }

    public void activate(Socket socket, PrintWriter serverLog) {
        this.socket = socket;
        try {
            this.serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.serverOut = new PrintWriter(socket.getOutputStream());
        } catch (IOException ioe) {
            serverLog.println(new Date() + ": Failed to create streams for user " + id);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof UserHandle) {
            return ((UserHandle) o).ip == this.ip;
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

    public BufferedReader getBufferedReader() {
        return serverIn;
    }

    public PrintWriter getPrintWriter() {
        return serverOut;
    }
}
