import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class StoredRecord implements Serializable {
    ConcurrentHashMap<String, ChunkInfo> storedRecord; // Record of chunks stored in other peers
    ConcurrentHashMap<String, String> fileNames; // Record of files stored in other peers

    public StoredRecord() {
        storedRecord = new ConcurrentHashMap<String, ChunkInfo>();
        fileNames = new ConcurrentHashMap<String, String>();
    }

    public void insert(String key, ChunkInfo chunkInfo) {
        storedRecord.putIfAbsent(key, chunkInfo);
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

    public void removeFileChunks(String fileID) {
        Set<String> chunksSet = storedRecord.keySet().stream().filter(string -> string.endsWith("_" + fileID))
                .collect(Collectors.toSet());
        storedRecord.keySet().removeAll(chunksSet);
        fileNames.remove(fileID);
    }

    public void insertFileName(String key, String fileName) {
        fileNames.putIfAbsent(key, fileName);
    }

    public String print() {
        String state = "";
        for (String fileID : fileNames.keySet()) {
            state += "\n> File path: " + fileNames.get(fileID);
            state += "\n    File ID: " + fileID;
            Set<String> ChunkIDset = storedRecord.keySet().stream().filter(string -> string.endsWith("_" + fileID))
                    .collect(Collectors.toSet());

            if (ChunkIDset.size() != 0)
                state += "\n    Desired Replication Degree: "
                        + storedRecord.get(ChunkIDset.iterator().next()).getDesiredReplicationDegree();
            state += "\n    > Chunk Information: ";
            for (String chunkKey : ChunkIDset) {
                state += "\n        Chunk ID: " + storedRecord.get(chunkKey).getID();
                state += "\n        Actual Replication Degree: "
                        + storedRecord.get(chunkKey).getActualReplicationDegree() + "\n";
            }
        }

        state += "\n";
        return state;
    }
}