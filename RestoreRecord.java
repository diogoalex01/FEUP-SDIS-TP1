import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;

public class RestoreRecord {
    Set<String> record; // Record of the restored chunks
    Set<Chunk> restoredChunks;

    public RestoreRecord() {
        record = new HashSet<String>();
        restoredChunks = new TreeSet<Chunk>();
    }

    public void insertKey(String key) {
        record.add(key);
    }

    public boolean removeKey(String key) {
        return record.remove(key);
    }

    public void insertChunk(Chunk chunk) {
        restoredChunks.add(chunk);
    }

    public boolean removeChunk(Chunk chunk) {
        return restoredChunks.remove(chunk);
    }

    public boolean isRestored(String key) {
        return record.contains(key);
    }

    public Set<Chunk> getRestoredChunks() {
        return restoredChunks;
    }

    public void print() {
        // Print values
        for (String string : record) {
            System.out.println("ID: " + string);
        }
    }

    public void printChunks() {
        // Print values
        for (Chunk chunk : restoredChunks) {
            System.out.println("Chunk: " + chunk.getID());
        }
    }
}