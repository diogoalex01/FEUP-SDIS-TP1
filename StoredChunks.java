import java.util.Set;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Collection;
import java.io.Serializable;

public class StoredChunks implements Serializable {
    HashMap<String, ChunkInfo> storedChunks; // Chunks stored in this peer
    int occupiedStorage;
    int availableStorage;

    public StoredChunks() {
        storedChunks = new HashMap<String, ChunkInfo>();
        this.occupiedStorage = 0; // bytes
        this.availableStorage = 256000; // bytes
    }

    public void insert(String key, ChunkInfo chunkInfo) {
        storedChunks.put(key, chunkInfo);
        occupiedStorage += chunkInfo.getSize();
    }

    public void remove(String key) {
        storedChunks.remove(key);
        occupiedStorage -= storedChunks.get(key).getSize();
    }

    public int getOccupiedStorage() {
        return this.occupiedStorage;
    }

    public void setOccupiedStorage(int occupiedStorage) {
        this.occupiedStorage = occupiedStorage;
     }

    public int getAvailableStorage() {
        return this.availableStorage;
    }

    public void setAvailableStorage(int availableStorage) {
       this.availableStorage = availableStorage;
    }

    public Collection<ChunkInfo> getChunks() {
        return storedChunks.values();
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