package mutex;

public class LamportRequestItem implements Comparable<LamportRequestItem>{

    private final long timeStamp;
    private final int id;

    public LamportRequestItem(long timeStamp, int id) {
        this.timeStamp = timeStamp;
        this.id = id;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public int getId() {
        return id;
    }

    @Override
    public int compareTo(LamportRequestItem o) {
        if(this.timeStamp == o.timeStamp){
            return this.id - o.id;
        }
        return (int) (this.timeStamp - o.getTimeStamp());
    }
}
