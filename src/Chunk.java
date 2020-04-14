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

    @Override
    public boolean equals(Object objectInfo) {
        if (this == objectInfo)
            return true;

        if (objectInfo == null || objectInfo.getClass() != this.getClass())
            return false;

        Chunk chunk = (Chunk) objectInfo;

        return (super.getID() == chunk.getID() && super.getFileID().equals(chunk.getFileID())
                && super.getSize() == chunk.getSize()
                && super.getDesiredReplicationDegree() == chunk.getDesiredReplicationDegree()
                && super.getActualReplicationDegree() == chunk.getActualReplicationDegree()
                && super.getFileName().equals(chunk.getFileName())
                && data.equals(chunk.getData()));
    }

    @Override
    public int hashCode() {
        return super.getID();
    }
}