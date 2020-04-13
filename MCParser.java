import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.net.DatagramPacket;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;

public class MCParser implements Runnable {
    private static final int BACKUP_BUFFER_SIZE = 64512; // bytes
    private static final String CRLF = "\r\n"; // CRLF delimiter
    private static final int INITIAL_WAITING_TIME = 1000; // 1 second
    private static final int RANDOM_TIME = 400; // milliseconds
    private static final int MAX_ATTEMPTS = 5;
    Peer peer;
    String received;

    public MCParser(Peer peer, String received) {
        this.peer = peer;
        this.received = received;
    }

    public void parseMessageMC() {
        try {
            String[] receivedMessage = received.split("[\\u0020]+"); // blank space UTF-8
            String protocolVersion = receivedMessage[0];
            String command = receivedMessage[1];
            String senderID = receivedMessage[2];
            String fileID = receivedMessage[3];
            Random rand = new Random();
            int randomTime = rand.nextInt(RANDOM_TIME);

            if (senderID.equals(this.peer.getID()))
                return;

            // <Version> STORED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            if (command.equals("STORED")) {
                String chunkID = receivedMessage[4];
                String key = this.peer.makeKey(chunkID, fileID);

                if (this.peer.getStoredChunks().getChunkInfo(key) != null
                        && !this.peer.getStoredChunks().getChunkInfo(key).getHolders().contains(senderID)) {
                    this.peer.getStoredChunks().getChunkInfo(key).updateActualReplicationDegree(1);
                    this.peer.getStoredChunks().getChunkInfo(key).addHolders(senderID);
                }

                // Each chunk updates the replication degree of the files that
                // are being stored by some other peer if the sender is distinct
                if (this.peer.getStoredRecord().getChunkInfo(key) != null
                        && !this.peer.getStoredRecord().getChunkInfo(key).getHolders().contains(senderID)) {
                    this.peer.getStoredRecord().getChunkInfo(key).updateActualReplicationDegree(1);
                    this.peer.getStoredRecord().getChunkInfo(key).addHolders(senderID);
                }

            }
            // <Version> DELETE <SenderId> <FileId> <CRLF><CRLF>
            else if (command.equals("DELETE")) {
                this.peer.deleteAllChunks(fileID);
            }
            // <Version> GETCHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            else if (command.equals("GETCHUNK")) {
                String chunkID = receivedMessage[4];
                String fileFolder = this.peer.getBackupDirPath() + "/" + fileID;
                File file = new File(fileFolder);

                if (file.exists()) {
                    File chunkFile = new File(fileFolder + "/" + chunkID);

                    if (chunkFile.exists()) {
                        byte[] content = Files.readAllBytes(chunkFile.toPath());
                        String key = this.peer.makeKey(chunkID, fileID);

                        this.peer.getRestoreRecord().insertKey(key);

                        // Wait random amount of time to execute
                        ScheduledExecutorService execService = Executors.newScheduledThreadPool(5);
                        execService.schedule(() -> {

                            if (this.peer.getRestoreRecord().isRestored(key)) {
                                this.peer.getRestoreRecord().removeKey(key);
                                try {
                                    // <Version> CHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF><Body>
                                    if (protocolVersion.equals("1.0")) {
                                        String chunkMessage = protocolVersion + " CHUNK " + this.peer.getID() + " "
                                                + fileID + " " + chunkID + " " + CRLF + CRLF;
                                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                        byteArrayOutputStream.write(chunkMessage.getBytes());
                                        byteArrayOutputStream.write(content);
                                        byte[] chunkBuf = byteArrayOutputStream.toByteArray();
                                        DatagramPacket chunkPacket = new DatagramPacket(chunkBuf, chunkBuf.length,
                                                this.peer.getMDRGroup(), this.peer.getMDRPort());
                                        this.peer.getMDRSocket().send(chunkPacket);
                                    } else {
                                        this.peer.sendOverTCP(senderID, protocolVersion, chunkID, fileID, content);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                        }, randomTime, TimeUnit.MILLISECONDS);
                    }
                }
            }
            // <Version> REMOVED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            else if (command.equals("REMOVED")) {
                String chunkID = receivedMessage[4];
                String key = this.peer.makeKey(chunkID, fileID);
                String fileFolder = this.peer.getBackupDirPath() + "/" + fileID;
                File file = new File(fileFolder);
                byte[] chunkBody;

                // Each chunk updates the replication degree of
                // the removed chunk that it is storing
                if (this.peer.getStoredChunks().getChunkInfo(key) != null) {
                    this.peer.getStoredChunks().getChunkInfo(key).updateActualReplicationDegree(-1);
                    this.peer.getStoredChunks().getChunkInfo(key).removeHolders(senderID);

                    if (file.exists()) {
                        File chunkFile = new File(fileFolder + "/" + chunkID);

                        if (chunkFile.exists()) {
                            chunkBody = Files.readAllBytes(chunkFile.toPath());
                            this.peer.getRemoveRecord().insertKey(key);

                            // Wait random amount of time
                            ScheduledExecutorService execService = Executors.newScheduledThreadPool(5);
                            execService.schedule(() -> {

                                if (this.peer.getRemoveRecord().wasRemoved(key)) {
                                    Chunk chunk = new Chunk(Integer.parseInt(chunkID), fileID, chunkBody.length,
                                            this.peer.getStoredChunks().getChunkInfo(key).getDesiredReplicationDegree(),
                                            "UNKNOWN");
                                    chunk.setData(chunkBody);
                                    chunk.setActualReplicationDegree(
                                            this.peer.getStoredChunks().getChunkInfo(key).getActualReplicationDegree());
                                    // chunk.setDesiredReplicationDegree(
                                    // this.peer.getStoredChunks().getChunkInfo(key).getDesiredReplicationDegree());
                                    try {
                                        this.peer.sendStopAndWait(chunk, chunk.getDesiredReplicationDegree(), fileID,
                                                key);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                            }, randomTime, TimeUnit.MILLISECONDS);
                        }
                    }
                }

                // Each chunk updates the replication degree of the
                // files that are being stored by some other peer
                if (this.peer.getStoredRecord().getChunkInfo(key) != null) {
                    this.peer.getStoredRecord().getChunkInfo(key).updateActualReplicationDegree(-1);
                    this.peer.getStoredRecord().getChunkInfo(key).removeHolders(senderID);
                }
            }
            // <Version> UPDATE <Placeholder> <Placeholder> <CRLF><CRLF>
            else if (command.equals("UPDATE")) {
                ScheduledExecutorService execService = Executors.newScheduledThreadPool(5);
                execService.schedule(() -> {
                    this.peer.sendAllDeletions();
                }, randomTime, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        this.parseMessageMC();
    }
}