import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkInfo implements Serializable, Comparable<ChunkInfo> {
    private int ID;
    private String fileID;
    private int size;
    private int desiredReplicationDegree;
    private int actualReplicationDegree;
    private String fileName;
    Set<String> holders;
    ConcurrentHashMap<String, Integer> holdersMap = new ConcurrentHashMap<>();

    public ChunkInfo(int ID, String fileID, int size, int replicationDegree, String fileName) {
        this.ID = ID;
        this.fileID = fileID;
        this.size = size;
        this.desiredReplicationDegree = replicationDegree;
        this.actualReplicationDegree = 0;
        this.fileName = fileName;
        holders = holdersMap.newKeySet();
    }

    ChunkInfo(ChunkInfo chunkInfo) {
        this.ID = chunkInfo.getID();
        this.fileID = chunkInfo.getFileID();
        this.size = chunkInfo.getSize();
        this.desiredReplicationDegree = chunkInfo.getDesiredReplicationDegree();
        this.actualReplicationDegree = chunkInfo.getActualReplicationDegree();
        this.fileName = chunkInfo.getFileName();
        holders = chunkInfo.getHolders();
    }

    public String getFileID() {
        return fileID;
    }

    public void setFileID(String fileID) {
        this.fileID = fileID;
    }

    public int getID() {
        return ID;
    }

    public void setID(int ID) {
        this.ID = ID;
    }

    public int getSize() {
        return size;
    }

    public void setSize(String fileID) {
        this.size = size;
    }

    public int getDesiredReplicationDegree() {
        return desiredReplicationDegree;
    }

    public void setDesiredReplicationDegree(int desiredReplicationDegree) {
        this.desiredReplicationDegree = desiredReplicationDegree;
    }

    public int getActualReplicationDegree() {
        return actualReplicationDegree;
    }

    public void setActualReplicationDegree(int actualReplicationDegree) {
        this.actualReplicationDegree += actualReplicationDegree;
    }

    public String getFileName() {
        return fileName;
    }

    public void updateActualReplicationDegree(int actualReplicationDegree) {
        this.actualReplicationDegree += actualReplicationDegree;
    }

    public Set<String> getHolders() {
        return holders;
    }

    public void addHolders(String holder) {
        holders.add(holder);
    }

    public void removeHolders(String holder) {
        holders.remove(holder);
    }

    @Override
    public int compareTo(ChunkInfo chunkInfo) {
        if (ID < chunkInfo.getID())
            return -1;
        else
            return 1;
    }
}