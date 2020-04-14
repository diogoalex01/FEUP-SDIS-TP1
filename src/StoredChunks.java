import java.io.Serializable;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StoredChunks implements Serializable {
    String backupDir;
    ConcurrentHashMap<String, ChunkInfo> storedChunks; // Chunks stored in this peer
    ConcurrentSkipListSet<String> fileNames; // Record of files stored in this peer

    int occupiedStorage;
    int availableStorage;

    public StoredChunks(String backupDirPath) {
        backupDir = backupDirPath;
        storedChunks = new ConcurrentHashMap<String, ChunkInfo>();
        fileNames = new ConcurrentSkipListSet<String>();
        this.occupiedStorage = 0; // bytes
        this.availableStorage = 256000000; // bytes
    }

    public void insert(String key, ChunkInfo chunkInfo) {
        storedChunks.putIfAbsent(key, chunkInfo);
        int underScoreIndex = key.lastIndexOf("_");
        fileNames.add(key.substring(underScoreIndex + 1));
    }

    public void remove(String key) {
        storedChunks.remove(key);
        int underScoreIndex = key.lastIndexOf("_");
        Set<String> ChunkIDset = storedChunks.keySet().stream()
                .filter(string -> string.endsWith("_" + key.substring(underScoreIndex + 1)))
                .collect(Collectors.toSet());
        if (ChunkIDset.size() == 0) {
            fileNames.remove(key.substring(underScoreIndex + 1));
        }
    }

    public int getOccupiedStorage() {
        return this.occupiedStorage;
    }

    public void setOccupiedStorage(int occupiedStorage) {
        this.occupiedStorage = occupiedStorage;
    }

    public void updateOccupiedStorage(int amount) {
        // System.out.println("Occupied era " + occupiedStorage + "| " + amount);
        occupiedStorage += amount;
        // System.out.println("Occupied agora é " + occupiedStorage);
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

    public void removeFileChunksWithStorage(String fileID) {
        Set<String> set = storedChunks.keySet().stream().filter(string -> string.endsWith("_" + fileID))
                .collect(Collectors.toSet());
        for (String chunkID : set) {
            try {
                final Path path = Paths.get(this.backupDir + "/" + fileID + "/" + storedChunks.get(chunkID).getID());
                // Checks if file exists
                if (Files.exists(path)) {
                    occupiedStorage -= storedChunks.get(chunkID).getSize();
                    System.out.println("File exists, updating occupied");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        storedChunks.keySet().removeAll(set);
    }

    public void removeFileChunks(String fileID) {
        Set<String> set = storedChunks.keySet().stream().filter(string -> string.endsWith("_" + fileID))
                .collect(Collectors.toSet());
        storedChunks.keySet().removeAll(set);
    }

    public String print() {
        String state = "";
        for (String fileID : fileNames) {
            try {
                Set<String> ChunkIDset = storedChunks.keySet().stream().filter(string -> string.endsWith("_" + fileID))
                        .collect(Collectors.toSet());

                for (String chunkKey : ChunkIDset) {
                    final Path path = Paths
                            .get(this.backupDir + "/" + fileID + "/" + storedChunks.get(chunkKey).getID());
                    // Checks if file exists
                    if (Files.exists(path)) {
                        state += "\n> Chunk ID: " + storedChunks.get(chunkKey).getFileID() + "_"
                                + storedChunks.get(chunkKey).getID();
                        state += "\n    Size: " + storedChunks.get(chunkKey).getSize() + " Bytes";
                        state += "\n    Actual Replication Degree: "
                                + storedChunks.get(chunkKey).getActualReplicationDegree();
                    }
                }
            } catch (Exception e) {
                System.err.println("Path exception: " + e.toString());
                e.printStackTrace();
            }
        }

        state += "\n\n";
        return state;
    }
}