package jangle;

import java.io.Serializable;

import org.javatuples.Pair;

public class UserMessage implements Serializable {
    public static enum UserMessageType {
        Username,
        Chat,
        RequestOldMessages,
        RequestNewMessages
    }

    private UserMessageType type;
    private Object payload;

    // suppressing warning because I want to catch
    // cast exception myself and throw an argument exception
    @SuppressWarnings("unchecked")
    public UserMessage(UserMessageType type, Object payload) {
        this.type = type;

        try {
            switch (this.type) {
                case Username:
                    this.payload = (String) payload;
                    break;

                case Chat:
                    this.payload = (String) payload;
                    break;

                case RequestOldMessages:
                    this.payload = (Pair<Integer, Integer>) payload;
                    break;

                case RequestNewMessages:
                    this.payload = (Pair<Integer, Integer>) payload;
                    break;
            }
        } catch (ClassCastException cce) {
            throw new IllegalArgumentException("type of payload does not correspond to type of message");
        }
    }

    public UserMessageType getType() {
        return type;
    }

    public Object getPayload() {
        return payload;
    }
}
