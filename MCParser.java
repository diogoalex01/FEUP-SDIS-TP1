import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.net.DatagramPacket;
import java.util.Random;

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
            // System.out.println("REC: " + received);
            String[] receivedMessage = received.split("[\\u0020]+"); // blank space UTF-8
            String protocolVersion = receivedMessage[0];
            String command = receivedMessage[1];
            String senderID = receivedMessage[2];
            String fileID = receivedMessage[3];
            Random rand = new Random();

            if (senderID.equals(this.peer.getID()))
                return;

            // <Version> STORED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            if (command.equals("STORED")) {
                System.out.println("STORED");
                String chunkID = receivedMessage[4];
                String key = this.peer.makeKey(chunkID, fileID);

                if (this.peer.getStoredChunks().getChunkInfo(key) != null
                        && !this.peer.getStoredChunks().getChunkInfo(key).getHolders().contains(senderID)) {
                    this.peer.getStoredChunks().getChunkInfo(key).updateActualReplicationDegree(1);
                    this.peer.getStoredChunks().getChunkInfo(key).addHolders(senderID);
                    System.out.println("Updating storedChunks +1");
                }

                // Each chunk updates the replication degree of the files that
                // are being stored by some other peer if the sender is distinct
                if (this.peer.getStoredRecord().getChunkInfo(key) != null
                        && !this.peer.getStoredRecord().getChunkInfo(key).getHolders().contains(senderID)) {
                    this.peer.getStoredRecord().getChunkInfo(key).updateActualReplicationDegree(1);
                    this.peer.getStoredRecord().getChunkInfo(key).addHolders(senderID);
                    System.out.println("Updating storedRecord");
                }

                if (this.peer.getStoredRecord().getChunkInfo(key) != null) {
                    System.out.println("STORED=> actual: "
                            + this.peer.getStoredRecord().getChunkInfo(key).getActualReplicationDegree());
                    System.out.println("STORED=> desired: "
                            + this.peer.getStoredRecord().getChunkInfo(key).getDesiredReplicationDegree());
                }

                if (this.peer.getStoredChunks().getChunkInfo(key) != null) {
                    System.out.println("CHUNK=> actual: "
                            + this.peer.getStoredChunks().getChunkInfo(key).getActualReplicationDegree());
                    System.out.println("CHUNK=> desired: "
                            + this.peer.getStoredChunks().getChunkInfo(key).getDesiredReplicationDegree());
                }
            }
            // <Version> DELETE <SenderId> <FileId> <CRLF><CRLF>
            else if (command.equals("DELETE")) {
                File backupDir = new File(this.peer.getBackupDirPath());
                File fileIDDir = new File(backupDir.getPath(), fileID);

                if (fileIDDir.exists()) {
                    String[] entries = fileIDDir.list();
                    for (String entry : entries) {
                        File currentFile = new File(fileIDDir.getPath(), entry);
                        currentFile.delete();
                    }

                    fileIDDir.delete();

                    // Deletes backup directory if it is empty after the fileID directory deletion
                    File[] backupDirectory = backupDir.listFiles();
                    if (backupDirectory.length == 0)
                        backupDir.delete();
                }

                this.peer.getStoredChunks().removeFileChunks(fileID);
                this.peer.getStoredRecord().removeFileChunks(fileID);
            }
            // <Version> GETCHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            else if (command.equals("GETCHUNK")) {
                System.out.println("GETCHUUUNK");
                String chunkID = receivedMessage[4];
                String fileFolder = this.peer.getBackupDirPath() + "/" + fileID;
                File file = new File(fileFolder);

                if (file.exists()) {
                    // System.out.println("Folder exists");
                    File chunkFile = new File(fileFolder + "/" + chunkID);

                    if (chunkFile.exists()) {
                        // System.out.println("Chunk file exists");
                        byte[] content = Files.readAllBytes(chunkFile.toPath());
                        // Wait random amount of time
                        int randomTime = rand.nextInt(RANDOM_TIME);
                        Thread.sleep(randomTime);
                        String key = this.peer.makeKey(chunkID, fileID);

                        if (!this.peer.getRestoreRecord().isRestored(key)) {
                            // this.peer.getRestoreRecord().removeKey(key);

                            // <Version> CHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF><Body>
                            if (protocolVersion.equals("1.0")) {
                                String chunkMessage = protocolVersion + " CHUNK " + this.peer.getID() + " " + fileID
                                        + " " + chunkID + " " + CRLF + CRLF;
                                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                byteArrayOutputStream.write(chunkMessage.getBytes());
                                byteArrayOutputStream.write(content);
                                byte[] chunkBuf = byteArrayOutputStream.toByteArray();
                                DatagramPacket chunkPacket = new DatagramPacket(chunkBuf, chunkBuf.length,
                                        this.peer.getMDRGroup(), this.peer.getMDRPort());
                                this.peer.getMDRSocket().send(chunkPacket);
                                System.out.println("Sent chunk with id: " + chunkID);
                            } else {
                                System.out.println("Sending chunk " + chunkID);
                                this.peer.sendOverTCP(senderID, protocolVersion, chunkID, fileID, content);
                            }
                        } else {
                            System.out.println("JA alguem mandou o " + chunkID);
                        }
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
                        System.out.println("Folder exists");
                        File chunkFile = new File(fileFolder + "/" + chunkID);

                        if (chunkFile.exists()) {
                            chunkBody = Files.readAllBytes(chunkFile.toPath());
                            this.peer.getRemoveRecord().insertKey(key);

                            // Sleep
                            int randomTime = rand.nextInt(RANDOM_TIME);
                            Thread.sleep(randomTime);

                            if (this.peer.getRemoveRecord().wasRemoved(key)) {
                                System.out.println("No one sent... Sending...");
                                Chunk chunk = new Chunk(Integer.parseInt(chunkID), fileID, chunkBody.length,
                                        this.peer.getStoredChunks().getChunkInfo(key).getDesiredReplicationDegree(),
                                        "UNKNOWN");
                                chunk.setData(chunkBody);
                                chunk.setActualReplicationDegree(
                                        this.peer.getStoredChunks().getChunkInfo(key).getActualReplicationDegree());
                                // chunk.setDesiredReplicationDegree(
                                // this.peer.getStoredChunks().getChunkInfo(key).getDesiredReplicationDegree());
                                System.out.println("A repor o chunk com ARD: " + chunk.getActualReplicationDegree()
                                        + " e RD: " + chunk.getDesiredReplicationDegree());
                                this.peer.sendStopAndWait(chunk, chunk.getDesiredReplicationDegree(), fileID, key);
                            } else {
                                System.out.println("Someone else already sent!");
                            }
                        }
                    }
                }

                // Each chunk updates the replication degree of the
                // files that are being stored by some other peer
                if (this.peer.getStoredRecord().getChunkInfo(key) != null) {
                    this.peer.getStoredRecord().getChunkInfo(key).updateActualReplicationDegree(-1);
                    this.peer.getStoredRecord().getChunkInfo(key).removeHolders(senderID);
                    System.out.println("Updating storedRecord");
                }
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