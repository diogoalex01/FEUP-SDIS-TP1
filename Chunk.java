public class Chunk {
    private int ID;
    private String fileID;
    private byte[] data;
    private int size;
    private int desiredReplicationDegree;
    private int actualReplicationDegree;

    public Chunk(int ID, String fileID, int size, int replicationDegree) {
        this.setID(ID);
        this.setFileID(fileID);
        this.size = size;
        this.desiredReplicationDegree = replicationDegree;
        this.actualReplicationDegree = 0;
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
        this.ID = iD;
    }

    public byte[] getData() {
        return this.data;
    }

    public void setData(byte[] data) {
        this.data = data;
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
