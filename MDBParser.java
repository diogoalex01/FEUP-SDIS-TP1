import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.MulticastSocket;
import java.security.NoSuchAlgorithmException;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Random;
import java.util.Arrays;

import java.lang.Throwable;

public class MDBParser implements Runnable {
    Peer peer;
    DatagramPacket packet;

    private static final int BACKUP_BUFFER_SIZE = 64512; // bytes
    private static final String CRLF = "\r\n"; // CRLF delimiter
    private static final int INITIAL_WAITING_TIME = 1000; // 1 second
    private static final int RANDOM_TIME = 400; // milliseconds
    private static final int MAX_ATTEMPTS = 5;

    public MDBParser(Peer peer, DatagramPacket packet) {
        this.peer = peer;
        this.packet = packet;
    }

    public void parseMessageMDB() {
        // <Version> PUTCHUNK <SenderId> <FileId> <ChunkNo> <ReplicationDeg>
        // <CRLF><CRLF><Body>
        try {
            String received = new String(this.packet.getData(), 0, this.packet.getLength());
            Random rand = new Random();
            String[] receivedMessage;
            receivedMessage = received.split("[\\u0020]+", 7); // blank space UTF-8

            int bodyStartIndex = received.lastIndexOf(CRLF) + CRLF.length();
            byte[] chunkBody = new byte[this.packet.getLength() - bodyStartIndex];
            chunkBody = Arrays.copyOfRange(this.packet.getData(), bodyStartIndex, this.packet.getLength());

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
                System.out.println("Tenho o original");
                return;
            }
            // <Version> STORED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            String storedMessage = this.peer.getProtocolVersion() + " STORED " + this.peer.getID() + " " + fileID + " "
                    + chunkNumber + " " + CRLF + CRLF;
            byte[] storedBuf = storedMessage.getBytes();
            DatagramPacket storedReply = new DatagramPacket(storedBuf, storedBuf.length, this.peer.getMCGroup(),
                    this.peer.getMCPort());

            if (command.equals("PUTCHUNK")) {
                System.out.println("PUTCHUNK");
                if (this.peer.getRemoveRecord().wasRemoved(key)) {
                    this.peer.getRemoveRecord().removeKey(key);
                    return;
                }

                // If the chunk has size of 0 Bytes, it is ignored
                if (chunkBody.length == 0) {
                    System.out.println("Empty chunk");
                    // Reply to sender
                    this.peer.getMCSocket().send(storedReply);
                    return;
                }

                // Only backs up the chunk if the peer has enough available storage
                if (this.peer.getStoredChunks().getOccupiedStorage() + chunkBody.length > this.peer.getStoredChunks()
                        .getAvailableStorage()) {
                    System.out.println("No storage available to store a new chunk!");
                    return;
                }

                String fileDirName = this.peer.getBackupDirPath() + "/" + fileID + "/";
                String chunkFileName = fileDirName + chunkNumber;

                // Check if file already exists
                try {
                    final Path path = Paths.get(chunkFileName);

                    if (Files.exists(path)) {
                        Thread.sleep(randomTime);
                        System.out.println("Sent STORED because I'm storing it!");
                        this.peer.getMCSocket().send(storedReply);
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Path exception: " + e.toString());
                    e.printStackTrace();
                }

                // Only stores a new entry if the chunk wasn't
                // already sent by any of the other peers
                if (this.peer.getStoredChunks().getChunkInfo(key) == null) {
                    ChunkInfo chunkInfo = new ChunkInfo(Integer.parseInt(chunkNumber), fileID, chunkBody.length,
                            Integer.parseInt(replicationDegree));
                    this.peer.getStoredChunks().insert(key, chunkInfo);
                }
                // If the intended replication degree changed,
                // it's updated so that it can be met
                else {
                    this.peer.getStoredChunks().getChunkInfo(key)
                            .setDesiredReplicationDegree(Integer.parseInt(replicationDegree));
                }

                // Wait random amount of time
                Thread.sleep(randomTime);

                // Only actually stores the chunk (data) if the replication
                // degree wasn't already met by the other peers
                if (this.peer.getStoredChunks().getChunkInfo(key).getActualReplicationDegree() < this.peer
                        .getStoredChunks().getChunkInfo(key).getDesiredReplicationDegree()) {
                    System.out.println("waited " + randomTime);

                    // Store chunk
                    final Path fileDirPath = Paths.get(fileDirName);

                    if (Files.notExists(fileDirPath)) {
                        Files.createDirectories(fileDirPath);
                    }

                    OutputStream outputStream = new FileOutputStream(chunkFileName);
                    outputStream.write(chunkBody);
                    outputStream.close();
                    System.out.println("Sent STORED");
                    // Reply to sender
                    this.peer.getMCSocket().send(storedReply);
                }
            } else {
                System.out.println("PUTCHUNK command not found!");
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