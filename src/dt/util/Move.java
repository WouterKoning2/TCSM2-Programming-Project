package dt.util;

public class Move {

    private Integer move1 = null;
    private Integer move2 = null;

    public Move(int move1){
        this.move1 = move1;
    }

    public Move(int move1, int move2){
        this.move1 = move1;
        this.move2 = move2;
    }

    public boolean isDoubleMove(){
        return this.move2 != null;
    }

    public Integer getMove1(){
        return this.move1;
    }

    public Integer getMove2(){
        return this.move2;
    }


}
