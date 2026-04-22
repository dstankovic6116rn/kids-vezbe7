package servent.handler.mutex;

import app.AppConfig;
import mutex.DistributedMutex;
import mutex.LamportMutex;
import mutex.LamportRequestItem;
import servent.handler.MessageHandler;
import servent.message.Message;
import servent.message.mutex.LamportReplyMessage;
import servent.message.util.MessageUtil;

public class LamportRequestHandler implements MessageHandler {

    private Message clientMessage;
    private LamportMutex mutex;

    public LamportRequestHandler(Message clientMessage, DistributedMutex mutex){
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

        //dodajemo posiljaoca u queue za ulayak u kriticnu sekciju
        mutex.addToQueue(new LamportRequestItem(messageTimeStamp, clientMessage.getOriginalSenderInfo().getId()));

        //saljemo mu Reply poruku
        MessageUtil.sendMessage(new LamportReplyMessage(
                clientMessage.getReceiverInfo(),
                clientMessage.getOriginalSenderInfo(),
                mutex.getTimeStamp()));
    }
}
