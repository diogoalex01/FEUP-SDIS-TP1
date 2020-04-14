import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

public class MDRParser implements Runnable {
    private static final String CRLF = "\r\n"; // CRLF delimiter
    Peer peer;
    DatagramPacket packet;

    public MDRParser(Peer peer, DatagramPacket packet) {
        this.peer = peer;
        this.packet = packet;
    }

    public void parseMessageMDR() {
        try {
            String received = new String(this.packet.getData(), 0, this.packet.getLength(), StandardCharsets.UTF_8);
            Random rand = new Random();
            String[] receivedMessage;

            receivedMessage = received.split("[\\u0020]+", 6); // blank space UTF-8
            int bodyStartIndex = received.indexOf(CRLF) + 2 * CRLF.length();
            final byte[] chunkBody = Arrays.copyOfRange(packet.getData(), bodyStartIndex, packet.getLength());
            // System.out.println("BodystateIndex = " + bodyStartIndex);
            // System.out.println("RECEIVED packet with " + packet.getLength() + " bytes");
            // System.out.println("RECEIVED chunk with " + chunkBody.length + " bytes");
            // System.out.println("Recebido " + received);

            String protocolVersion = receivedMessage[0];
            String command = receivedMessage[1];
            String senderID = receivedMessage[2];
            String fileID = receivedMessage[3];
            String chunkNumber = receivedMessage[4];
            String key = this.peer.makeKey(chunkNumber, fileID);

            if (senderID.equals(this.peer.getID())) {
                return;
            }

            // <Version> CHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF><Body>
            if (command.equals("CHUNK")) {
                // Checks if the chunk belongs to a local file
                // True if peer is not original file owner.
                if (this.peer.getStoredRecord().getChunkInfo(key) == null) {
                    // Only stores a new key if the chunk wasn't
                    // already restored by any of the other peers
                    if (this.peer.getRestoreRecord().isRestored(key)) {
                        this.peer.getRestoreRecord().removeKey(key);
                    }
                } else {
                    // Original file owner, stores new chunks to assemble file
                    if (!this.peer.getRestoreRecord().isRestored(key)) {
                        this.peer.getRestoreRecord().insertKey(key);
                        Chunk chunk = new Chunk(Integer.parseInt(chunkNumber), fileID, chunkBody.length, 0, "UNKNOWN");
                        chunk.setData(chunkBody);
                        this.peer.getRestoreRecord().insertChunk(chunk);
                    }
                }
            }
            // <Version> CONNECT <SenderID> <FileID> <ChunkNo> <Hostname> <Port>
            // <CRLF><CRLF>
            else if (command.equals("CONNECT")) {
                receivedMessage = received.split("[\\u0020]+", 8); // blank space UTF-8
                String hostname = receivedMessage[5];
                String port = receivedMessage[6];

                // Checks if the chunk belongs to a local file
                // True if peer is not original file owner.
                if (this.peer.getStoredRecord().getChunkInfo(key) == null) {
                    // Only stores a new key if the chunk wasn't
                    // already restored by any of the other peers
                    if (!this.peer.getRestoreRecord().isRestored(key)) {
                        this.peer.getRestoreRecord().insertKey(key);
                    }
                } else {
                    // System.out.println("received: " + received);
                    this.peer.receiveOverTCP(hostname, port, chunkNumber, fileID);
                }
            }
            // <Version> DELETIONS <SenderID> <Placeholder> <Placeholder> <Body>
            else if (command.equals("DELETIONS")) {
                receivedMessage = received.split("[\\u0020]+", 6);
                String deletions[] = receivedMessage[5].split("[\\u0020]+");
                for (String fileToDelete : deletions) {
                    System.out.println("UPDATED: Deleting file with ID " + fileToDelete.substring(0, 4) + "...");
                    this.peer.deleteAllChunks(fileToDelete);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        this.parseMessageMDR();
    }
}