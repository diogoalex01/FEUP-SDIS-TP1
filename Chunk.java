public class Chunk {
    private int ID;
    private String fileID;
    private byte[] data;
    private int size;

    public Chunk(int ID, String fileID, int size) {
        this.ID = ID;
        this.fileID = fileID;
        this.size = size;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
