package dt.protocol;

import dt.exceptions.ServerUnavailableException;

import java.net.ProtocolException;

public interface ClientProtocol {

    String doHello() throws ServerUnavailableException, ProtocolException;
    boolean doLogin(String username) throws ServerUnavailableException, ProtocolException;
    String doGetList() throws ServerUnavailableException, ProtocolException;
    String doMove(int move) throws ServerUnavailableException, ProtocolException;
    String doMove(int move, int move2) throws ServerUnavailableException, ProtocolException;
    String doEnterQueue() throws ServerUnavailableException, ProtocolException;
}