public class Chunk extends ChunkInfo {
    private byte[] data;

    public Chunk(int ID, String fileID, int size, int replicationDegree, String fileName) {
        super(ID, fileID, size, replicationDegree, fileName);
    }

    public byte[] getData() {
        return this.data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}