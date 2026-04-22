package servent.handler.mutex;

import app.AppConfig;
import mutex.DistributedMutex;
import mutex.MutexType;
import mutex.TokenMutex;
import servent.handler.MessageHandler;
import servent.message.Message;
import servent.message.MessageType;

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
