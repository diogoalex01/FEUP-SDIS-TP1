import java.io.Serializable;

public class ChunkInfo implements Serializable {
    private int ID;
    private String fileID;
    private int size;
    private int desiredReplicationDegree;
    private int actualReplicationDegree;

    public ChunkInfo(int ID, String fileID, int size, int replicationDegree) {
        this.setID(ID);
        this.setFileID(fileID);
        this.size = size;
        this.desiredReplicationDegree = replicationDegree;
        this.actualReplicationDegree = 0;
    }

    ChunkInfo(ChunkInfo chunkInfo) {
        this.ID = chunkInfo.getID();
        this.fileID = chunkInfo.getFileID();
        this.size = chunkInfo.getSize();
        this.desiredReplicationDegree = chunkInfo.getDesiredReplicationDegree();
        this.actualReplicationDegree = chunkInfo.getActualReplicationDegree();
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

    public void setID(int iD) {
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

    public void updateActualReplicationDegree(int actualReplicationDegree) {
        this.actualReplicationDegree += actualReplicationDegree;
    }
}
