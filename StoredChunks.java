import java.util.HashMap;
import java.io.Serializable;

public class StoredChunks implements Serializable {
    HashMap<String, ChunkInfo> storedChunks;  // Chunks stored in this peer

    public StoredChunks() {
        storedChunks = new HashMap<String, ChunkInfo>();
    }

    public void insert(String key, ChunkInfo chunkInfo) {
        storedChunks.put(key, chunkInfo);
    }

    public ChunkInfo getChunkInfo(String key) {
        return storedChunks.get(key);
    }

    public int getReplicationDegree(String key) {
        return storedChunks.get(key).getActualReplicationDegree();
    }

    public void print() {
        // Print values
        System.out.println("In storage:");
        for (ChunkInfo chunkInfo : storedChunks.values()) {
            System.out.println(chunkInfo.getFileID() + "_" + chunkInfo.getID());
        }
    }
}