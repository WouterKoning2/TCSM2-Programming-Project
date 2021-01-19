package dt.collectoClient;

import java.io.*;

import dt.exceptions.CommandException;
import dt.exceptions.InvalidMoveException;
import dt.exceptions.UnexpectedResponseException;
import dt.exceptions.UserExit;
import dt.model.board.Board;
import dt.model.board.ClientBoard;
import dt.peer.SocketHandler;
import dt.peer.NetworkEntity;
import dt.protocol.ClientMessages;
import dt.protocol.ClientProtocol;
import dt.protocol.ProtocolMessages;
import dt.protocol.ServerMessages;
import dt.util.Move;

import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Client implements ClientProtocol, NetworkEntity {
    //TODO prompt the player when it is their turn

    private Socket serverSocket;
    private SocketHandler socketHandler;
    private String userName;
    private Integer port;
    private final ClientView clientView;
    private ClientBoard board;
    private InetAddress ip;
    private final String CLIENTDESCRIPTION = "Client By: Emiel";
    private boolean chatEnabled;
    private boolean rankEnabled;
    private boolean cryptEnabled;
    private boolean authEnabled;
    private ClientStates state;
    private Move ourLastMove;
    private boolean moveConfirmed;

    public Client() {
        this.clientView = new ClientTUI(this);
        this.board = new ClientBoard();
        this.userName = null;
        this.ip = null;
        this.port = null;
        this.chatEnabled = true;
        this.rankEnabled = false;
        this.cryptEnabled = false;
        this.authEnabled = false;
    }

    public static void main(String[] args) {
        Client client = new Client();
        if(args.length > 1) {
            try {
                client.ip = InetAddress.getByName(args[0]);
                client.port = Integer.parseInt(args[1]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        client.start();
    }

    public void start() {
        this.state = ClientStates.STARTINGUP;
        new Thread(clientView).start();
    }

    public void setDebug(Boolean state){
        socketHandler.setDebug(state);
    }

    public void writeMessage(String msg) {
        socketHandler.write(msg);
    }
    @Override
    public synchronized void handleMessage(String msg) {
        String[] arguments = msg.split(ProtocolMessages.delimiter);
        String keyWord = arguments[0];
        clientView.showMessage(this.state.toString());
        try {
            switch (ServerMessages.valueOf(keyWord)) {
                case HELLO:
                    if (this.state == ClientStates.PENDINGHELLO) {
                        this.handleHello(arguments);
                        synchronized (clientView) {
                            this.clientView.notify();
                        }
                    } else {
                        throw new UnexpectedResponseException();
                    }
                    break;
                case LOGIN:
                    if (this.state == ClientStates.PENDINGLOGIN) {
                        this.state = ClientStates.LOGGEDIN;
                        this.clientView.showMessage("Successfully logged in!");
                        synchronized (clientView) {
                            this.clientView.notifyAll();
                        }
                    } else {
                        throw new UnexpectedResponseException();
                    }
                    break;
                case ALREADYLOGGEDIN:
                    if (this.state == ClientStates.PENDINGLOGIN) {
                        this.clientView.showMessage("Username already logged in, try again");
                        synchronized (clientView) {
                            this.clientView.notifyAll();
                        }
                    }else {
                        throw new UnexpectedResponseException();
                    }
                    break;
                case LIST:
                    if (this.state == ClientStates.WAITINGONLIST) {
                        this.clientView.displayList(parseListResponse(arguments));
                    }
                    synchronized (clientView) {
                        this.clientView.notify();
                    }
                    break;
                case NEWGAME:
                    if(this.state == ClientStates.INQUEUE) { //TODO Sometimes when creating a new game, it doesn't expect the NEWGAME response from the server
                        this.createNewBoard(arguments);
                    }   else {
                        throw new UnexpectedResponseException();
                    }
                    this.clientView.showMessage(this.board.getPrettyBoardState());
                    break;
                case MOVE:
                    if (this.state == ClientStates.AWAITMOVERESPONSE) {
                        this.checkMoveResponse(arguments);
                    } else if (this.state == ClientStates.AWAITNGTHEIRMOVE) {
                        try {
                            this.makeTheirMove(arguments);
                            this.clientView.showMessage(this.board.getPrettyBoardState());
                        } catch (InvalidMoveException e) {
                            throw new InvalidMoveException("The server move was invalid");
                        }
                    } else {
                        throw new UnexpectedResponseException();
                    }
                    break;
                case GAMEOVER:
                    this.clientView.showMessage(this.handleGameOver(arguments));
                    break;
                case ERROR:
                    clientView.showMessage("Server threw error: " + msg);
                    this.notifyAll();
                    break;
                case CHAT:
                    String[] splitChat = msg.split(ProtocolMessages.delimiter, 3);
                    clientView.showMessage(splitChat[1] + ": " + splitChat[2]);
                    break;
                case WHISPER:
                    String[] splitWhisper = msg.split(ProtocolMessages.delimiter, 3);
                    clientView.showMessage(splitWhisper[1] + " whispers: " + splitWhisper[2]);
                    break;
                case CANNOTWHISPER:
                    throw new CommandException(arguments[1] + "Cannot receive whispers");
            }
        } catch (UnexpectedResponseException e) {
            clientView.showMessage("Unexpected response: " + msg);
        } catch (NumberFormatException  | ProtocolException e) {
            clientView.showMessage("Invalid response from server. Response: " + msg);
            if (!e.getMessage().equals("")) clientView.showMessage("Reason: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            clientView.showMessage("Unkown command from server. Response: " + msg);
        } catch (InvalidMoveException | CommandException e) {
            clientView.showMessage(e.getMessage());
        }
    }

    //Outgoing messages. Updates state
    @Override
    public void doHello() {
        List<String> extensions = new ArrayList<>();
        extensions.add(userName);
        if(this.chatEnabled) extensions.add(ProtocolMessages.Messages.CHAT.name());
        if(this.authEnabled) extensions.add(ProtocolMessages.Messages.AUTH.name());
        if(this.cryptEnabled) extensions.add(ProtocolMessages.Messages.CRYPT.name());
        if(this.rankEnabled) extensions.add(ProtocolMessages.Messages.RANK.name());

        socketHandler.write(ClientMessages.HELLO.constructMessage(extensions));
        this.state = ClientStates.PENDINGHELLO;
    }


    @Override
    public void doLogin(String username) {
        socketHandler.write(ClientMessages.LOGIN.constructMessage(username));
        this.state = ClientStates.PENDINGLOGIN;
    }

    @Override
    public void doGetList() {
        socketHandler.write(ClientMessages.LIST.constructMessage());
        this.state = ClientStates.WAITINGONLIST;
        try {
            clientView.wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void doSendChat(String message){
        socketHandler.write(ClientMessages.CHAT.constructMessage(message));
    }

    public void doSendWhisper(String recipient, String message){
        socketHandler.write(ClientMessages.WHISPER.constructMessage(recipient, message));
    }


    @Override
    public synchronized void doMove(Move move) throws InvalidMoveException {
        this.state = ClientStates.AWAITMOVERESPONSE;
        socketHandler.write(ClientMessages.MOVE.constructMessage(move));
        this.ourLastMove = move;
        this.moveConfirmed = false;
        try {
            this.wait();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(this.moveConfirmed) {
            makeMove(move);
            this.state = ClientStates.AWAITNGTHEIRMOVE;
        }
    }


    @Override
    public void doEnterQueue()  {
        socketHandler.write(ClientMessages.QUEUE.constructMessage());
        clientView.showMessage("Entered queue");
        this.state = ClientStates.INQUEUE;
    }

    //Parsing response
    //HELLO
    private void handleHello(String[] arguments) throws ProtocolException {
        if(arguments.length == 1) throw new ProtocolException("Not enough arguments");
        for(int i = 1; i < arguments.length; i++) {
            String arg = arguments[i];
            chatEnabled = arg.equals(ProtocolMessages.Messages.CHAT.toString());
            rankEnabled = arg.equals(ProtocolMessages.Messages.RANK.toString());
            cryptEnabled = arg.equals(ProtocolMessages.Messages.CRYPT.toString());
            authEnabled = arg.equals(ProtocolMessages.Messages.AUTH.toString());
        }
        this.clientView.showMessage("Handshake successful! Connected to server: " + arguments[1]);
        this.state = ClientStates.HELLOED;
    }

    //LIST
    private String[] parseListResponse(String[] arguments) {
        String[] ret = new String[arguments.length-1];
        if (arguments.length - 1 >= 0) {
            System.arraycopy(arguments, 1, ret, 0, arguments.length - 1);
        }
        return ret;
    }

    //NEWGAME
    private void createNewBoard(String[] arguments) throws NumberFormatException {
        int[] boardState = new int[arguments.length-2]; //TODO add check on valid number of squares
        for(int i = 1; i < arguments.length-2; i++) {
            boardState[i-1] = Integer.parseInt(arguments[i]);
        }

        String beginner = arguments[arguments.length - 2];
        if(this.userName.equals(beginner)){
            this.state = ClientStates.AWAITMOVERESPONSE;
            clientView.showMessage("You start");
        } else {
            this.state = ClientStates.AWAITNGTHEIRMOVE;
            clientView.showMessage("Waiting on their move");
        }

        this.board = new ClientBoard(boardState);
    }

    //MOVE (1st)
    @SuppressWarnings("UnusedAssignment")
    private synchronized void checkMoveResponse(String[] arguments) throws NumberFormatException, ProtocolException { //Wörks
        boolean errorThrown = false;
        switch (arguments.length) {
            case 1:
                errorThrown = true;
                throw new ProtocolException("No move in response");
            case 2:
                if(!ourLastMove.equals(new Move(Integer.parseInt(arguments[1])))) {
                    errorThrown = true;
                    throw new ProtocolException("Move mismatch. Our move was: " + ourLastMove.toString());
                }
                break;
            case 3:
                if(!ourLastMove.equals(new Move(Integer.parseInt(arguments[1], Integer.parseInt(arguments[2]))))) {
                    errorThrown = true;
                    throw new ProtocolException("Move mismatch. Our move was: " + ourLastMove.toString());
                }
                break;
            default:
                errorThrown = true;
                throw new ProtocolException("Too many arguments");
        }
        //noinspection ConstantConditions
        this.notify();
        this.moveConfirmed = true;
    }

    //MOVE (2nd)
    private void makeTheirMove(String[] arguments) throws NumberFormatException, ProtocolException, InvalidMoveException {
        switch (arguments.length) {
            case 1:
                throw new ProtocolException("No move in response of their move");
            case 2:
                makeMove(new Move(Integer.parseInt(arguments[1])));
                break;
            case 3:
                makeMove(new Move(Integer.parseInt(arguments[1]),Integer.parseInt(arguments[1])));
                break;
            default:
                throw new ProtocolException("Too many arguments");
        }
        this.state = ClientStates.INGAME;
    }

    private String handleGameOver(String[] arguments) throws IllegalArgumentException, ProtocolException {
        String ret = "The game is over. \nReason: ";
        if(arguments.length != 3) throw new ProtocolException("Invalid number of arguments");
        switch (ServerMessages.GameOverReasons.valueOf(arguments[1])) {
            case VICTORY:
                ret = ret.concat("VICTORY");
                if(arguments[2].equals(this.userName)) {
                    ret += "Congratulations YOU WON! :)";
                } else {
                    ret = ret.concat("You lost. You donkey. I will disown you. You are not worthy >:(");
                }
                break;
            case DRAW:
                ret = ret.concat("DRAW \nGood game though! :|");
                break;
            case DISCONNECT:
                ret = ret.concat("Your opponent left because he/she could not stand your face ;)");
                break;
        }
        this.state = ClientStates.IDLE;
        return ret;
    }

    public void provideHint() {
        clientView.showMessage(this.board.getAHint().toString());
    }

    private void makeMove(Move move) throws InvalidMoveException {
        board.makeMove(move);
    }

    public void createConnection() throws IOException {
        createConnection(this.ip, this.port, this.userName);
    }

    private void createConnection(InetAddress ip, Integer port, String userName) throws IOException {
        clearConnection();

        while (serverSocket == null) {
            serverSocket = new Socket(ip, port);
        }

        this.socketHandler = new SocketHandler(this, serverSocket, this.CLIENTDESCRIPTION);
        new Thread(socketHandler).start();

        clientView.showMessage("Connected to server!");
        this.chatEnabled = true;
        doHello();
    }

    @Override
    public void handlePeerShutdown() {
        this.clientView.showMessage("Server shutdown");

        try {
            this.clientView.reconnect();
        } catch (UserExit e) {
            this.shutDown();
        }
    }

    @Override
    public void shutDown() {
        clearConnection();
        if(socketHandler != null) socketHandler.shutDown();
        clientView.showMessage("See you next time!");
        System.exit(69);
    }

    public void clearConnection() {
        serverSocket = null;
    }
    public String getUserName() {
        return this.userName;
    }
    public String setUsername(String userName) {
        return this.userName = userName;
    }
    public InetAddress getIp() {
        return this.ip;
    }
    public void setIp(InetAddress ip) {
        this.ip = ip;
    }
    public Integer getPort() {
        return this.port;
    }
    public void setPort(Integer port) {
        this.port = port;
    }
    public ClientStates getState() {
        return this.state;
    }
    public Board getBoard() {
        return this.board;
    }

}
