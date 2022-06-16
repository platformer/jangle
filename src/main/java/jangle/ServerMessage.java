package jangle;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import org.javatuples.Pair;
import org.javatuples.Quartet;

public class ServerMessage implements Serializable {
    public static enum ServerMessageType {
        DisconnectDuplicate,
        ChatChunk,
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
                case ChatChunk:
                    this.payload = (Pair<Integer, List<Quartet<Instant, String, Integer, String>>>) payload;
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
