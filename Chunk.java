public class Chunk {
    private int ID;
    private String fileID;
    private byte[] data;
    private int size;

    public Chunk(int ID, String fileID, int size) {
        this.setID(ID);
        this.setFileID(fileID);
        this.size = size;
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
}
