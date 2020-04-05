import java.util.Set;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.io.Serializable;

public class StoredChunks implements Serializable {
    HashMap<String, ChunkInfo> storedChunks; // Chunks stored in this peer

    public StoredChunks() {
        storedChunks = new HashMap<String, ChunkInfo>();
    }

    public void insert(String key, ChunkInfo chunkInfo) {
        storedChunks.put(key, chunkInfo);
    }

    public void remove(String key) {
        storedChunks.remove(key);
    }

    public ChunkInfo getChunkInfo(String key) {
        return storedChunks.get(key);
    }

    public int getReplicationDegree(String key) {
        return storedChunks.get(key).getActualReplicationDegree();
    }

    public void removeFileChunks(String fileID) {
        Set<String> set = storedChunks.keySet().stream().filter(string -> string.endsWith("_" + fileID))
                .collect(Collectors.toSet());
        storedChunks.keySet().removeAll(set);
    }

    public void print() {
        // Print values
        System.out.println("In storage:");
        for (ChunkInfo chunkInfo : storedChunks.values()) {
            System.out.println(chunkInfo.getFileID() + "_" + chunkInfo.getID());
        }
    }
}