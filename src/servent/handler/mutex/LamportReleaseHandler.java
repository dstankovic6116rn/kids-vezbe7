package servent.handler.mutex;

import app.AppConfig;
import mutex.DistributedMutex;
import mutex.LamportMutex;
import servent.handler.MessageHandler;
import servent.message.Message;

public class LamportReleaseHandler implements MessageHandler {

    private Message clientMessage;
    private LamportMutex mutex;

    public LamportReleaseHandler(Message clientMessage, DistributedMutex mutex){
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
        //update-ujemo svoj timeStamp
        long messageTimeStamp = Long.parseLong(clientMessage.getMessageText());
        mutex.updateTimeStamp(messageTimeStamp);

        //sklanjamo glavu sa queue, to je onaj koji je poslao release
        mutex.removeHeadOfQueue();

    }
}
