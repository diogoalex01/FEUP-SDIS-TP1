import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.TreeSet;


public class RemoveRecord {
    Set<String> record; // Record of the removed chunks
    ConcurrentHashMap<String,Integer> removedMap =new ConcurrentHashMap<>();

    public RemoveRecord() {
        record = removedMap.newKeySet();
    }

    public void insertKey(String key) {
        record.add(key);
    }

    public boolean removeKey(String key) {
        return record.remove(key);
    }

    public boolean wasRemoved(String key) {
        return record.contains(key);
    }

    public Set<String> getRemoveRecord() {
        return record;
    }
}