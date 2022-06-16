package jangle;

import java.io.Serializable;

public class UserMessage implements Serializable {
    public static enum UserMessageType {
        Username,
        Chat,
        RequestOldMessages,
        RequestNewMessages
    }

    private UserMessageType type;
    private Object payload;

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
                    this.payload = (Integer) payload;
                    break;

                case RequestNewMessages:
                    this.payload = (Integer) payload;
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
