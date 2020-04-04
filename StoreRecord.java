import java.util.HashMap;

public class StoreRecord {
    HashMap<String, Chunk> storeRecord;

    public StoreRecord() {
        storeRecord = new HashMap<String, Chunk>();
    }

    public void insert(String key, Chunk chunk) {
        storeRecord.put(key, chunk);
    }

    public Chunk getChunk(String key) {
        return storeRecord.get(key);
    }

    public int getReplicationDegree(String key) {
        return storeRecord.get(key).getActualReplicationDegree();
    }

    
}