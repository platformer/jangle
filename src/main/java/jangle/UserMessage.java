package jangle;

import java.io.Serializable;
import java.util.Map;

public class UserMessage implements Serializable {
    public enum MessageType {
        Username,
        Chat,
        RequestOldMessages,
        RequestNewMessages
    }

    private MessageType type;
    private Object payload;

    // suppressing warning because I want to catch
    // cast exception myself and throw an argument exception
    @SuppressWarnings("unchecked")
    public UserMessage(MessageType type, Object payload) {
        this.type = type;

        try {
            switch (this.type){
                case Username:
                    this.payload = (String) payload;
                    break;
                
                case Chat:
                    this.payload = (String) payload;
                    break;

                case RequestOldMessages:
                    this.payload = (Map.Entry<Integer, Integer>) payload;
                    break;

                case RequestNewMessages:
                    this.payload = (Map.Entry<Integer, Integer>) payload;
                    break;
            }
        }
        catch (ClassCastException cce){
            throw new IllegalArgumentException("type of payload does not correspond to type of message");
        }
    }

    public MessageType getType(){
        return type;
    }

    public Object getPayload(){
        return payload;
    }
}
