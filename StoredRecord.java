import java.util.HashMap;
import java.io.Serializable;

public class StoredRecord implements Serializable {
    HashMap<String, ChunkInfo> storedRecord; // Record of chunks stored in other peers

    public StoredRecord() {
        storedRecord = new HashMap<String, ChunkInfo>();
    }

    public void insert(String key, ChunkInfo chunkInfo) {
        storedRecord.put(key, chunkInfo);
    }

    public void remove(String key) {
        storedRecord.remove(key);
    }

    public ChunkInfo getChunkInfo(String key) {
        return storedRecord.get(key);
    }

    public int getReplicationDegree(String key) {
        return storedRecord.get(key).getActualReplicationDegree();
    }

    public void print() {
        // Print values
        System.out.println("In storage:");
        for (ChunkInfo chunkInfo : storedRecord.values()) {
            System.out.println(chunkInfo.getFileID() + "_" + chunkInfo.getID());
        }
    }
}