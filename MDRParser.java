import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;

public class MDRParser implements Runnable {
    private static final int BACKUP_BUFFER_SIZE = 64512; // bytes
    private static final String CRLF = "\r\n"; // CRLF delimiter
    private static final int INITIAL_WAITING_TIME = 1000; // 1 second
    private static final int RANDOM_TIME = 400; // milliseconds
    private static final int MAX_ATTEMPTS = 5;
    Peer peer;
    DatagramPacket packet;

    public MDRParser(Peer peer, DatagramPacket packet) {
        this.peer = peer;
        this.packet = packet;
    }

    public void parseMessageMDR() {
        try {
            // <Version> CHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF><Body>
            String received = new String(this.packet.getData(), 0, this.packet.getLength(), StandardCharsets.UTF_8);
            Random rand = new Random();
            String[] receivedMessage;

            receivedMessage = received.split("[\\u0020]+", 6); // blank space UTF-8
            int bodyStartIndex = received.indexOf(CRLF) + 2 * CRLF.length();
            final byte[] chunkBody = Arrays.copyOfRange(packet.getData(), bodyStartIndex, packet.getLength());
            // System.out.println("BodystateIndex =  " + bodyStartIndex);
            // System.out.println("RECEIVED packet with " + packet.getLength() + " bytes");
            // System.out.println("RECEIVED chunk with " + chunkBody.length + " bytes");

            String protocolVersion = receivedMessage[0];
            String command = receivedMessage[1];
            String senderID = receivedMessage[2];
            String fileID = receivedMessage[3];
            String chunkNumber = receivedMessage[4];
            String key = this.peer.makeKey(chunkNumber, fileID);

            if (senderID.equals(this.peer.getID())) {
                return;
            }

            if (command.equals("CHUNK")) {
                // System.out.println("CHUNK");
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
                        // System.out.println("Received chunk with size =  " + chunkBody.length + " bytes");
                        chunk.setData(chunkBody);
                        this.peer.getRestoreRecord().insertChunk(chunk);
                    }
                }
            }
            // <Version> CONNECT <SenderID> <FileID> <ChunkNo> <hostName> <port>
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
                    this.peer.receiveOverTCP(hostname, port, chunkNumber, fileID);
                }
            } else {
                // System.out.println("Valid command not found!");
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