import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

public class RestoreRecord {
    ConcurrentSkipListSet<String> record; // Record of the restored chunks
    ConcurrentSkipListSet<Chunk> restoredChunks;

    public RestoreRecord() {
        record = new ConcurrentSkipListSet<String>();
        restoredChunks = new ConcurrentSkipListSet<Chunk>();
    }

    public synchronized void insertKey(String key) {
        record.add(key);
    }

    public synchronized boolean removeKey(String key) {
        return record.remove(key);
    }

    public synchronized void insertChunk(Chunk chunk) {
        restoredChunks.add(chunk);
    }

    public synchronized boolean removeChunk(Chunk chunk) {
        return restoredChunks.remove(chunk);
    }

    public synchronized boolean isRestored(String key) {
        return record.contains(key);
    }

    public synchronized Set<Chunk> getRestoredChunks() {
        return restoredChunks;
    }

    public synchronized Set<Chunk> getRestoredSet() {
        return restoredChunks;
    }

    public synchronized void clear() {
        record.clear();
        restoredChunks.clear();
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