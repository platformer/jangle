package jangle;

import java.io.Serializable;
import java.time.Instant;

import org.javatuples.Quartet;

public class ServerMessage implements Serializable {
    public static enum ServerMessageType {
        DisconnectDuplicate,
        Chat
    }

    private ServerMessageType type;
    private Object payload;

    // suppressing warning because I want to catch
    // cast exception myself and throw an argument exception
    @SuppressWarnings("unchecked")
    public ServerMessage(ServerMessageType type, Object payload) {
        this.type = type;

        try {
            switch (this.type) {
                case DisconnectDuplicate:
                    this.payload = (Boolean) payload;
                    break;
                case Chat:
                    this.payload = (Quartet<Instant, String, Integer, String>) payload;
                    break;
            }
        } catch (ClassCastException cce) {
            throw new IllegalArgumentException("type of payload does not correspond to type of message");
        }
    }

    public ServerMessageType getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }
}
