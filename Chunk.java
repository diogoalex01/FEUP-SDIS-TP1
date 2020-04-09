public class Chunk extends ChunkInfo {
    private byte[] data;

    public Chunk(int ID, String fileID, int size, int replicationDegree) {
        super(ID, fileID, size, replicationDegree);
    }

    public byte[] getData() {
        return this.data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}