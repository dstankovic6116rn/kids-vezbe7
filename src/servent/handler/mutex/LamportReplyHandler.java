package servent.handler.mutex;

import app.AppConfig;
import mutex.DistributedMutex;
import mutex.LamportMutex;
import servent.handler.MessageHandler;
import servent.message.Message;

public class LamportReplyHandler implements MessageHandler {

    private Message clientMessage;
    private LamportMutex mutex;

    public LamportReplyHandler(Message clientMessage, DistributedMutex mutex){
        this.clientMessage = clientMessage;

        if(mutex instanceof LamportMutex){
            this.mutex = (LamportMutex) mutex;
        }
        else{
            AppConfig.timestampedErrorPrint("mutex nije Lamport");
        }
    }

    @Override
    public void run() {
        //update-ujemo svoj time stamp
        long messageTimeStamp = Long.parseLong(clientMessage.getMessageText());
        mutex.updateTimeStamp(messageTimeStamp);

        //uvecavamo koliko Reply poruka nam je stiglo
        mutex.incrementReplyCount();
    }
}
