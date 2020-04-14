import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.io.Serializable;

public class Timeline implements Serializable {

    ConcurrentHashMap<String, Timestamp> deletedChunks; // Record of deleted chunks and time of deletion

    public Timeline() {
        deletedChunks = new ConcurrentHashMap<String, Timestamp>();
    }

    public synchronized void insertDeletion(String key) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        deletedChunks.putIfAbsent(key, timestamp);
    }

    public synchronized void removeDeletion(String key) {
        deletedChunks.remove(key);
    }

    public synchronized boolean wasDeleted(String key) {
        return deletedChunks.containsKey(key);
    }

    public synchronized Timestamp getTimeOfDeletion(String key) {
        return deletedChunks.get(key);
    }

    public synchronized ConcurrentHashMap<String, Timestamp> getMap() {
        return deletedChunks;
    }

}