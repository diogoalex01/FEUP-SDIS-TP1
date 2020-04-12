import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.concurrent.ConcurrentHashMap;

public class Timeline {

    ConcurrentHashMap<String, Timestamp> deletedChunks; //Record of deleted chunks and time of deletion

    public Timeline(){
        deletedChunks = new ConcurrentHashMap<String, Timestamp>();
    }

    public void insertDeletion(String key){
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        deletedChunks.putIfAbsent(key, timestamp);
    }

    public void removeDeletion(String key){
        deletedChunks.remove(key);
    }

    public boolean wasDeleted(String key){
        return deletedChunks.containsKey(key);
    }

    public Timestamp getTimeOfDeletion(String key){
        return deletedChunks.get(key);
    }

}