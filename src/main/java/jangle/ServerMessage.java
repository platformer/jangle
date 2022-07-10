package jangle;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

import org.javatuples.Quartet;
import org.javatuples.Triplet;

public class ServerMessage implements Serializable {
    public static enum ServerMessageType {
        DisconnectDuplicate,
        ID,
        RecentChatChunk,
        ChatChunk,
        Chat,
        IdleTimeout
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
                case ID:
                    this.payload = (Integer) payload;
                    break;
                case RecentChatChunk:
                    this.payload = (Triplet<Integer, Integer, List<Quartet<Instant, String, Integer, String>>>) payload;
                    break;
                case ChatChunk:
                    this.payload = (Triplet<Integer, Integer, List<Quartet<Instant, String, Integer, String>>>) payload;
                    break;
                case Chat:
                    this.payload = (Quartet<Instant, String, Integer, String>) payload;
                    break;
                case IdleTimeout:
                    payload = new Object();
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
