``Token Mutex``
ServentMain.class
  main()
    u switchcase-u dodati mutex = new TokenMutex();

/cli/command/mutex path
  kreirati novi InitTokenMutexCommand.class isto kao i ostali +
  @Override
	public void execute(String args) {
		if (mutex != null && mutex instanceof TokenMutex) {
			((TokenMutex)mutex).sendTokenForward();
		} else {
			AppConfig.timestampedErrorPrint("Doing init token mutex on a non-token mutex: " + mutex);
		}
	}

/cli/CLIParser.class path
  dodati commandList.add(new InitTokenMutexCommand(mutex));

/mutex path
  dodati TokenMutex.class implementaciju
    public class TokenMutex implements DistributedMutex {

      private volatile boolean haveToken = false;
      private volatile boolean wantLock = false;
      
      @Override
      public void lock() {
        wantLock = true;
        
        while (!haveToken) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
      @Override
      public void unlock() {
        haveToken = false;
        wantLock = false;
        sendTokenForward();
      }
      public void receiveToken() {
        if (wantLock) {
          haveToken = true;
        } else {
          sendTokenForward();
        }
      }
      public void sendTokenForward() {
        int nextNodeId = (AppConfig.myServentInfo.getId() + 1) % AppConfig.getServentCount();
        MessageUtil.sendMessage(new TokenMessage(AppConfig.myServentInfo, AppConfig.getInfoById(nextNodeId)));
      }
    }

/servent/handler/mutex path
dodati TokenHandler.class implementaciju
  public class TokenHandler implements MessageHandler {

    private final Message clientMessage;
    private TokenMutex tokenMutex;
    
    public TokenHandler(Message clientMessage, DistributedMutex tokenMutex) {
      this.clientMessage = clientMessage;
      if (AppConfig.MUTEX_TYPE == MutexType.TOKEN) {
        this.tokenMutex = (TokenMutex)tokenMutex;
      } else {
        AppConfig.timestampedErrorPrint("Handling token message in non-token mutex: " + AppConfig.MUTEX_TYPE);
      }
    }
    @Override
    public void run() {
      if (clientMessage.getMessageType() == MessageType.TOKEN) {
        tokenMutex.receiveToken();
      } else {
        AppConfig.timestampedErrorPrint("Token handler for message: " + clientMessage);
      }
    }
  }

/servent/message/mutex
dodati TokenMessage.class implementaciju
  public class TokenMessage extends BasicMessage {

    private static final long serialVersionUID = 2084490973699262440L;

    public TokenMessage(ServentInfo sender, ServentInfo receiver) {
      super(MessageType.TOKEN, sender, receiver);
    }
  }

update /servent/message/MessageType.class sa TOKEN

SimpleServentListener.class
  switch (clientMessage.getMessageType()){
    ...
    dodati 
    case TOKEN:
					messageHandler = new TokenHandler(clientMessage, mutex);
					break;
  }


``Lamport Mutex``

ServentMain.class
  main()
    u switchcase-u dodati mutex = new LamportMutex();

/mutex path
  dodati LamportMutex.class implementaciju
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

/servent/handler/mutex path
  dodati LamportReleaseHandler.class
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

  dodati LamportReplyHandler.class
    isto sve kao LamportReleaseHanlder samo je run()
      umesto mutex.removeHeadOfQueue();
      treba mutex.incrementReplyCount();

  dodati LamportRequestHandler.class
    konstruktor i atributi su isti, razliku je samo run() metoda
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


/servent/message/mutex
  dodati LamportReleaseMessage.class, LamportRequestMessage, LamportReplyMessage
  sve klase su iste razlikuje se samo MessageType.<type>
    public class LamportRequestMessage extends BasicMessage {
      private static final long serialVersionUID = 2084490973699262440L;

      public LamportRequestMessage(ServentInfo sender, ServentInfo receiver, long timeStamp) {
          super(MessageType.LAMPORT_REQUEST, sender, receiver, String.valueOf(timeStamp));
      }
    }

update /servent/message/MessageType.class sa LAMPORT_REQUEST, LAMPORT_RELEASE, LAMPORT_REPLY

SimpleServentListener.class
  switch (clientMessage.getMessageType()){
    ...
    dodati 
    case TOKEN:
					messageHandler = new TokenHandler(clientMessage, mutex);
					break;
					case LAMPORT_REQUEST:
						messageHandler = new LamportRequestHandler(clientMessage,mutex);
						break;
					case LAMPORT_RELEASE:
						messageHandler = new LamportReleaseHandler(clientMessage, mutex);
						break;
					case LAMPORT_REPLY:
						messageHandler = new LamportReplyHandler(clientMessage, mutex);
						break;
  }