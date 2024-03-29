package dt.collectoClient;

import dt.ai.AITypes;
import dt.exceptions.CommandException;
import dt.exceptions.InvalidMoveException;
import dt.exceptions.UserExit;
import dt.model.ClientBoard;
import dt.util.SimpleTUI;

import java.net.InetAddress;
import java.net.ProtocolException;
import java.net.UnknownHostException;

/**
 * Handles all interaction with the user
 *
 * @author Emiel Rous and Wouter Koning
 */
public class ClientTUI extends SimpleTUI implements ClientView {
    private final boolean interrupted = false;
    private final Client client;

    ClientTUI(Client client) {
        this.client = client;
    }

    /**
     * Enter the start flow. Port and ip are queried, after that username,
     * then normal operation starts
     *
     * @requires {@link Client} to notify the thread to continue
     * @ensures the user can exit at any point in the flow
     */
    @Override
    public void start() {
        try {
            while (client.getIp() == null) {
                this.client.setIp(getIp());
            }
            while (client.getPort() == null) {
                this.client.setPort(getPort());
            }

            this.createConnection();

            String username = "Somethin wong";
            while (true) {
                try {
                    synchronized (this) {
                        this.wait(); //Wait for login from server
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (client.getState() == ClientStates.LOGGEDIN) {
                    break;
                }
                username = getUsername();
                client.doLogin(username);
            }
            this.client.setUsername(username);

            while (true) {
                try {
                    String input = getString(); //Wait for user input
                    if (input != null) {
                        handleUserInput(input);
                    }
                } catch (CommandException e) {
                    this.showMessage(e.getMessage());
                }
            }
        } catch (UserExit e) {
            client.shutDown();
        }
    }

    /**
     * Handles the raw user input. Parses the input and selects a {@link UserCmds}
     *
     * @param input
     * @throws CommandException
     * @throws UserExit
     * @requires the input should not be null
     * @ensures an action is executed if the command is valid
     */
    private void handleUserInput(String input) throws CommandException, UserExit {
        try {
            String[] arguments = input.split(UserCmds.separators);
            UserCmds cmd = UserCmds.getUserCmd(arguments[0]);
            if (cmd == null) {
                throw new CommandException(String.format(UNKOWNCOMMAND, arguments[0]));
            }
            switch (cmd) {
                case LIST:
                    this.client.doGetList();
                    break;
                case QUEUE:
                    if (this.client.getState() == ClientStates.LOGGEDIN ||
                    this.client.getState() == ClientStates.GAMEOVER) {
                        this.client.doEnterQueue();
                    } else {
                        throw new CommandException("Youre already in game");
                    }
                    break;
                case MOVE:
                    if (this.client.getState() == ClientStates.WAITOURMOVE) {
                        if (this.client.getAi() == null) {
                            this.client.doMove(this.client.createMove(arguments));
                        } else {
                            this.client.doAIMove();
                        }
                    }
                    break;
                case HINT:
                    if (this.client.getBoard() != null) {
                        this.client.provideHint();
                    } else {
                        throw new CommandException("You're not in a game");
                    }
                    break;
                case HELP:
                    printHelpMenu();
                    break;
                case CHAT:
                    String[] splitChat = input.split(UserCmds.separators, 2);
                    this.client.doSendChat(splitChat[1]);
                    this.showMessage(client.getUserName() + ":" + splitChat[1]);
                    break;
                case WHISPER:
                    String[] splitWhisper = input.split(UserCmds.separators, 3);
                    String receiver = splitWhisper[1];
                    String whisperMessage = splitWhisper[2];
                    this.client.doSendWhisper(receiver, whisperMessage);
                    break;
                case PLAYER:
                    this.client.setAI(this.getClientAI());
                    break;
                case RANK:
                    this.client.doGetRanking();
                    break;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new CommandException("Invalid number of arguments give");
        } catch (NumberFormatException e) {
            throw new CommandException(NOTINTEGERMOVE);
        } catch (InvalidMoveException | ProtocolException e) {
            throw new CommandException(e.getMessage());
        }

    }

    public String getUsername() throws UserExit {
        return getString("What username would you like to have?");
    }

    /**
     * Prompts the user to reconnect
     *
     * @return if the user wants to reconnect
     * @throws UserExit
     * @ensures the user can exit
     */
    public boolean reconnect() throws UserExit {
        return getBoolean("Reconnect to server? (y/n)");
    }

    /**
     * Create a connection. If failed, prompt the user to try again
     *
     * @throws UserExit
     * @ensures the {@link Client} to create a new connection if the port an ip are valid
     * @ensures the user is prompted again if the ip and port are invalid
     * @ensures the user can exit
     */
    private void createConnection() throws UserExit {
        while (true) {
            try {
                client.createConnection();
                break;
            } catch (Exception e) {
                this.showMessage("Server not availabe. Reason: " + e.getMessage());
                if (!getBoolean("Try again? (y/n)")) {
                    throw new UserExit();
                }
            }
        }
    }

    /**
     * Prints a help menu with all available commands
     */
    private void printHelpMenu() {
        String ret = "Here is the list of commands:\n";
        ret += UserCmds.getPrettyCommands();
        this.showMessage(ret);
    }

    /**
     * Parses the list into a pretty list and prints it
     *
     * @param list
     */
    @Override
    public void displayList(String[] list) {
        this.showMessage("List of logged in users");
        for (int i = 0; i < list.length; i++) {
            this.showMessage(list[i]);
        }
    }


    /**
     * Enter the AI selection flow
     *
     * @return A new instance of an AI type. If the return value is null, the person has chosen for manual playing.
     * @throws UserExit if the user decides to exit the program.
     * @ensures a selection is made
     * @ensures the user can exit
     */
    public AITypes getClientAI() throws UserExit {

        String question = "What AI difficulty would you like to use for this game? Choose from:"
            .concat(System.lineSeparator())
            .concat(AITypes.allToString());

        while (true) {
            String aiString = getString(question);
            try {
                AITypes ai = AITypes.valueOf(aiString.toUpperCase());
                this.showMessage(ai + " chosen");
                return ai;
            } catch (IllegalArgumentException e) {
                getString(
                    aiString + " is not a valid AI type. Choose one of the following AI Types: "
                        .concat(System.lineSeparator())
                        .concat(AITypes.allToString()));
            }
        }
    }

    @Override
    public void setClientAI(AITypes type) {
        this.client.setAI(type);
    }


    @Override
    public void showRank(String rank) {
        showMessage(rank);
    }

    @Override
    public void showHint(String toString) {
        showMessage(toString);
    }

    @Override
    public void showBoard(ClientBoard board) {
        this.showMessage(board.getPrettyBoardState());
        this.showMessage("############################################");

    }

    @Override
    public void displayChatMessage(String msg) {
        showMessage(msg);
    }

    /**
     * Requests the user for an IP address
     *
     * @return
     * @throws UserExit
     * @ensures ip is not null
     * @ensures the user can exit
     */
    public InetAddress getIp() throws UserExit {
        try {
            return InetAddress
                .getByName(getString("What IP address is the server running on (format: x.x.x.x)"));
        } catch (UnknownHostException e) {
            showMessage("Invalid IP, try again. Format: x.x.x.x where x stands for 1-3 integers");
        }

        return null;
    }
}
