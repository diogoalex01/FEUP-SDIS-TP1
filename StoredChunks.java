import java.io.Serializable;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

public class StoredChunks implements Serializable {
    ConcurrentHashMap<String, ChunkInfo> storedChunks; // Chunks stored in this peer
    ConcurrentSkipListSet<String> fileNames; // Record of files stored in this peer

    int occupiedStorage;
    int availableStorage;

    public StoredChunks() {
        storedChunks = new ConcurrentHashMap<String, ChunkInfo>();
        fileNames = new ConcurrentSkipListSet<String>();
        this.occupiedStorage = 0; // bytes
        this.availableStorage = 256000; // bytes
    }

    public void insert(String key, ChunkInfo chunkInfo) {
        storedChunks.putIfAbsent(key, chunkInfo);
        occupiedStorage += chunkInfo.getSize();
        int underScoreIndex = key.lastIndexOf("_");
        fileNames.add(key.substring(underScoreIndex + 1));
    }

    public void remove(String key) {
        occupiedStorage -= storedChunks.get(key).getSize();
        storedChunks.remove(key);
        int underScoreIndex = key.lastIndexOf("_");
        fileNames.remove(key.substring(underScoreIndex + 1));
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
        for (String chunkID : set) {
            occupiedStorage -= storedChunks.get(chunkID).getSize();
        }
        storedChunks.keySet().removeAll(set);
    }

    public String print() {
        String state = "";
        for (String fileID : fileNames) {
            Set<String> ChunkIDset = storedChunks.keySet().stream().filter(string -> string.endsWith("_" + fileID))
                    .collect(Collectors.toSet());

            for (String chunkKey : ChunkIDset) {
                state += "\n> Chunk ID: " + storedChunks.get(chunkKey).getFileID() + "_"
                        + storedChunks.get(chunkKey).getID();
                state += "\n    Size: " + storedChunks.get(chunkKey).getSize() + " Bytes";
                state += "\n    Actual Replication Degree: " + storedChunks.get(chunkKey).getActualReplicationDegree();
            }

        }

        state += "\n\n";
        return state;
    }
}