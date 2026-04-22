package mutex;

import app.AppConfig;
import servent.message.mutex.LamportReleaseMessage;
import servent.message.mutex.LamportRequestMessage;
import servent.message.util.MessageUtil;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class LamportMutex implements DistributedMutex{

    //flag koji indikuje da zelimo da zapocnemo mutex, tj ceo proces lock - kriticna sekcija - unlock
    private AtomicBoolean distributedMutexInitiated = new AtomicBoolean(false);

    //timeStamp od cvora
    private AtomicLong timeStamp = null;

    //ovde brojimo da l su nam svi poslali reply
    private AtomicInteger replyCount = new AtomicInteger(0);
    //queue gde pratimo ko je u kriticnoj sekciji, odnosno drzi mutex, tj. poslao nam request poruku
    private BlockingQueue<LamportRequestItem> requestQueue;

    public LamportMutex() {
        timeStamp = new AtomicLong(AppConfig.myServentInfo.getId());

        requestQueue = new PriorityBlockingQueue<>();
    }

    public long getTimeStamp() {
        return timeStamp.get();
    }

    public void addToQueue(LamportRequestItem lamportRequestItem){
        try {
            requestQueue.put(lamportRequestItem);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    //updateovanje timeStamp-a, da bi bili sinhroniyovani sa timeStamp-ovima ostalih cvorova u sistemu
    public void updateTimeStamp(long newTimeStamp){
        long currentTimeStamp = timeStamp.get();

        while(newTimeStamp > currentTimeStamp){
            if(timeStamp.compareAndSet(currentTimeStamp, newTimeStamp+1)){
                break;
            }
            currentTimeStamp = timeStamp.get();
        }
    }

    public void incrementReplyCount(){
        replyCount.getAndIncrement();
    }

    public void removeHeadOfQueue(){
        try {
            requestQueue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void lock() {
        //pokusavamo comapre and set, dok ne uspemo, da bi zapoceli proces lock-a
        while (!distributedMutexInitiated.compareAndSet(false, true)){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        //saljemo svim susedima Request poruku
        for(int i = 0; i<AppConfig.myServentInfo.getNeighbors().size(); i++){
            int neighborId = AppConfig.myServentInfo.getNeighbors().get(i);

            MessageUtil.sendMessage(new LamportRequestMessage(AppConfig.myServentInfo,
                    AppConfig.getInfoById(neighborId), timeStamp.get()));
        }
        //dodajemo sebe u queue
        addToQueue(new LamportRequestItem(timeStamp.get(), AppConfig.myServentInfo.getId()));

        //cekamo da od svih dobijemo reply i da smo mi na vrhu queue, odnosno da smo mi sledeci za ulazak u kriticnu sekciju
        while(true){
            if(replyCount.get() == AppConfig.getServentCount() - 1 &&
                requestQueue.peek().getId() == AppConfig.myServentInfo.getId()){
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void unlock() {

        removeHeadOfQueue();

        replyCount.set(0);

        for(int i = 0; i<AppConfig.myServentInfo.getNeighbors().size(); i++){
            int neighborId = AppConfig.myServentInfo.getNeighbors().get(i);

            MessageUtil.sendMessage(new LamportReleaseMessage(AppConfig.myServentInfo,
                    AppConfig.getInfoById(neighborId), timeStamp.get()));
        }

        distributedMutexInitiated.set(false);

    }
}
