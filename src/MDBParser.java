import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;
import java.net.DatagramPacket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;

public class MDBParser implements Runnable {
    private static final String CRLF = "\r\n"; // CRLF delimiter
    private static final int RANDOM_TIME = 400; // milliseconds
    Peer peer;
    DatagramPacket packet;

    public MDBParser(Peer peer, DatagramPacket packet) {
        this.peer = peer;
        this.packet = packet;
    }

    public void parseMessageMDB() {
        // <Version> PUTCHUNK <SenderId> <FileId> <ChunkNo> <ReplicationDeg>
        // <CRLF><CRLF><Body>
        try {
            String received = new String(this.packet.getData(), 0, this.packet.getLength(), StandardCharsets.UTF_8);
            Random rand = new Random();
            String[] receivedMessage;

            receivedMessage = received.split("[\\u0020]+", 7); // blank space UTF-8
            int bodyStartIndex = received.indexOf(CRLF) + 2 * CRLF.length();
            final byte[] chunkBody = Arrays.copyOfRange(packet.getData(), bodyStartIndex, packet.getLength());

            String protocolVersion = receivedMessage[0];
            String command = receivedMessage[1];
            String senderID = receivedMessage[2];
            String fileID = receivedMessage[3];
            String chunkNumber = receivedMessage[4];
            String replicationDegree = receivedMessage[5];
            String key = this.peer.makeKey(chunkNumber, fileID);
            int randomTime = rand.nextInt(RANDOM_TIME);

            // If a peer reads its own message
            // or if it receives a chunk it has previously backed up
            if (senderID.equals(this.peer.getID()) || this.peer.getStoredRecord().getChunkInfo(key) != null) {
                return;
            }

            // <Version> STORED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            String storedMessage = this.peer.getProtocolVersion() + " STORED " + this.peer.getID() + " " + fileID + " "
                    + chunkNumber + " " + CRLF + CRLF;
            byte[] storedBuf = storedMessage.getBytes();
            DatagramPacket storedReply = new DatagramPacket(storedBuf, storedBuf.length, this.peer.getMCGroup(),
                    this.peer.getMCPort());

            if (command.equals("PUTCHUNK")) {

                if (this.peer.getTimeline().wasDeleted(fileID)) {
                    this.peer.getTimeline().removeDeletion(fileID);
                }

                if (this.peer.getRemoveRecord().wasRemoved(key)) {
                    this.peer.getRemoveRecord().removeKey(key);
                    return;
                }

                // If the chunk has size of 0 Bytes, it is ignored
                if (chunkBody.length == 0) {
                    // Reply to sender
                    this.peer.getMCSocket().send(storedReply);
                    return;
                }

                // Only backs up the chunk if the peer has enough available storage
                if (this.peer.getStoredChunks().getOccupiedStorage() + chunkBody.length > this.peer.getStoredChunks()
                        .getAvailableStorage()) {
                    return;
                }

                String fileDirName = this.peer.getBackupDirPath() + "/" + fileID + "/";
                String chunkFileName = fileDirName + chunkNumber;

                // Check if file already exists
                try {
                    final Path path = Paths.get(chunkFileName);

                    if (Files.exists(path)) {
                        ScheduledExecutorService execService = Executors.newScheduledThreadPool(5);
                        execService.schedule(() -> {
                            try {
                                this.peer.getMCSocket().send(storedReply);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }, randomTime, TimeUnit.MILLISECONDS);
                        return;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Only stores a new entry if the chunk wasn't
                // already sent by any of the other peers
                if (this.peer.getStoredChunks().getChunkInfo(key) == null) {
                    ChunkInfo chunkInfo = new ChunkInfo(Integer.parseInt(chunkNumber), fileID, chunkBody.length,
                            Integer.parseInt(replicationDegree), "UNKNOWN");
                    this.peer.getStoredChunks().insert(key, chunkInfo);
                }
                // If the intended replication degree changed,
                // it's updated so that it can be met
                else {
                    this.peer.getStoredChunks().getChunkInfo(key)
                            .setDesiredReplicationDegree(Integer.parseInt(replicationDegree));
                }

                // Wait random amount of time
                ScheduledExecutorService execService = Executors.newScheduledThreadPool(5);
                execService.schedule(() -> {
                    try {
                        // Only actually stores the chunk (data) if the replication
                        // degree wasn't already met by the other peers
                        if (this.peer.getStoredChunks().getChunkInfo(key).getActualReplicationDegree() < this.peer
                                .getStoredChunks().getChunkInfo(key).getDesiredReplicationDegree()) {
                            System.out.println("Waited " + randomTime + "ms before replying.");
                            // Store chunk
                            final Path fileDirPath = Paths.get(fileDirName);

                            if (Files.notExists(fileDirPath)) {
                                Files.createDirectories(fileDirPath);
                            }

                            OutputStream outputStream = new FileOutputStream(chunkFileName);
                            outputStream.write(chunkBody);
                            outputStream.close();
                            // Reply to sender
                            this.peer.getMCSocket().send(storedReply);
                            this.peer.getStoredChunks().getChunkInfo(key).updateActualReplicationDegree(1);
                            this.peer.getStoredChunks().updateOccupiedStorage(chunkBody.length);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }, randomTime, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        this.parseMessageMDB();
    }
}