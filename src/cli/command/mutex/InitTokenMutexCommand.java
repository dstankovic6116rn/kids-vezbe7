package cli.command.mutex;

import app.AppConfig;
import cli.command.CLICommand;
import mutex.DistributedMutex;
import mutex.TokenMutex;

public class InitTokenMutexCommand implements CLICommand {

	private DistributedMutex mutex;
	
	public InitTokenMutexCommand(DistributedMutex mutex) {
		this.mutex = mutex;
	}
	
	@Override
	public String commandName() {
		return "init_token_mutex";
	}

	@Override
	public void execute(String args) {
		if (mutex != null && mutex instanceof TokenMutex) {
			((TokenMutex)mutex).sendTokenForward();
		} else {
			AppConfig.timestampedErrorPrint("Doing init token mutex on a non-token mutex: " + mutex);
		}

	}

}
