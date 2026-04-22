package servent.message.mutex;

import app.ServentInfo;
import servent.message.BasicMessage;
import servent.message.MessageType;

public class LamportRequestMessage extends BasicMessage {
    private static final long serialVersionUID = 2084490973699262440L;

    public LamportRequestMessage(ServentInfo sender, ServentInfo receiver, long timeStamp) {
        super(MessageType.LAMPORT_REQUEST, sender, receiver, String.valueOf(timeStamp));
    }
}
