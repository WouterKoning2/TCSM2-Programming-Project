package dt.protocol;

import dt.util.Move;

import java.util.List;

public enum ClientMessages implements ProtocolMessages {
    HELLO (Messages.HELLO),
    LOGIN (Messages.LOGIN),
    LIST (Messages.LIST),
    MOVE (Messages.MOVE),
    QUEUE (Messages.QUEUE),
    CHAT (Messages.CHAT),
    WHISPER (Messages.WHISPER);

    private String msg = "";

    ClientMessages(Messages msg) {
        this.msg = msg.toString();
    }

    public String constructMessage() {
        return this.msg;
    }
    public String constructMessage(String arg) {
        return this.msg + delimiter + arg;
    }
    public String constructMessage(String arg1, String arg2) {
        return this.msg + delimiter + arg1 + delimiter + arg2;
    }

    public String constructMessage(Move move) {
        StringBuilder msg = new StringBuilder(this.msg+delimiter);
        return move.isDoubleMove()?
                msg.append(move.getMove1()).append(move.getMove2()).toString() :
                msg.append(move.getMove1()).toString();
    }
    public String constructMessage(List<String> args) {
        StringBuilder msg = new StringBuilder(this.msg);
        for(String arg : args) {
            msg.append(delimiter).append(arg);
        }
        return msg.toString();
    }
}
