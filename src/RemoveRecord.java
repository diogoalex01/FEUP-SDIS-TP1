import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RemoveRecord {
    Set<String> record; // Record of the removed chunks
    ConcurrentHashMap<String, Integer> removedMap = new ConcurrentHashMap<>();

    public RemoveRecord() {
        record = removedMap.newKeySet();
    }

    public synchronized void insertKey(String key) {
        record.add(key);
    }

    public synchronized boolean removeKey(String key) {
        return record.remove(key);
    }

    public synchronized boolean wasRemoved(String key) {
        return record.contains(key);
    }

    public synchronized Set<String> getRemoveRecord() {
        return record;
    }
}