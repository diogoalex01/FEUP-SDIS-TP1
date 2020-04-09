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

// java Peer 1.0 1 AP1 230.0.0.0 4445 231.0.0.0 4446 232.0.0.0 4447

public class Peer implements RemoteInterface {
    private static String ID;
    private String accessPoint;
    private String protocolVersion;
    private InetAddress MCGroup;
    private InetAddress MDBGroup;
    private InetAddress MDRGroup;
    private int MCPort;
    private int MDBPort;
    private int MDRPort;
    private MulticastSocket MCSocket;
    private MulticastSocket MDBSocket;
    private MulticastSocket MDRSocket;
    private static StoredRecord storedRecord;
    private static StoredChunks storedChunks;
    private static String storageDirPath;
    private static String backupDirPath;
    private static String restoreDirPath;
    private static String storagePathRecord;
    private static String storagePathChunks;
    private RestoreRecord restoreRecord;
    private volatile int restoredChunks = 0;
    private RemoveRecord removeRecord;
    private static ScheduledThreadPoolExecutor executor;
    private MulticastManager multicastManager;

    private static final int BACKUP_BUFFER_SIZE = 64512; // bytes
    private static final String CRLF = "\r\n"; // CRLF delimiter
    private static final int INITIAL_WAITING_TIME = 1000; // 1 second
    private static final int RANDOM_TIME = 400; // milliseconds
    private static final int MAX_ATTEMPTS = 5;

    private boolean putChunkSent;

    public Peer(String peerProtocolVersion, String peerID, String peerAccessPoint, String MCName, String MCPort,
            String MDBName, String MDBPort, String MDRName, String MDRPort) throws IOException {
        protocolVersion = peerProtocolVersion;
        ID = peerID;
        accessPoint = peerAccessPoint;
        this.MCGroup = InetAddress.getByName(MCName);
        this.MDBGroup = InetAddress.getByName(MDBName);
        this.MDRGroup = InetAddress.getByName(MDRName);
        this.MCPort = Integer.parseInt(MCPort);
        this.MDBPort = Integer.parseInt(MDBPort);
        this.MDRPort = Integer.parseInt(MDRPort);
        this.storageDirPath = "Peer" + "_" + this.ID;
        this.backupDirPath = storageDirPath + "/Backup";
        this.restoreDirPath = storageDirPath + "/Restore";
        this.storagePathRecord = storageDirPath + "/record.ser";
        this.storagePathChunks = storageDirPath + "/chunks.ser";
        final Path dirPath = Paths.get(storageDirPath);
        final Path pathRecord = Paths.get(storagePathRecord);
        final Path pathChunk = Paths.get(storagePathChunks);

        this.storedRecord = new StoredRecord();
        this.storedChunks = new StoredChunks();
        this.restoreRecord = new RestoreRecord();
        this.removeRecord = new RemoveRecord();

        this.executor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(250);
        this.putChunkSent = true;

        // Check if storage file already exists
        try {
            if (Files.exists(pathRecord)) {
                System.out.println("Reading storage from " + pathRecord);
                deserializeRecord();
            } else {
                Files.createDirectories(dirPath);
                Files.write(pathRecord, new byte[0]);
                System.out.println("Storage created under " + pathRecord);
            }

            if (Files.exists(pathChunk)) {
                System.out.println("Reading storage from " + pathChunk);
                deserializeChunks();
            } else {
                Files.createDirectories(dirPath);
                Files.write(pathChunk, new byte[0]);
                System.out.println("Storage created under " + pathChunk);
            }
        } catch (Exception e) {
            System.err.println("Path exception: " + e.toString());
            e.printStackTrace();
        }
        // Join MC channel
        MCSocket = new MulticastSocket(this.MCPort);
        MCSocket.joinGroup(MCGroup);
        // Join MDB channel
        MDBSocket = new MulticastSocket(this.MDBPort);
        MDBSocket.joinGroup(MDBGroup);
        // Join MDR channel
        MDRSocket = new MulticastSocket(this.MDRPort);
        MDRSocket.joinGroup(MDRGroup);
        this.multicastManager = new MulticastManager(this);
    }

    public MulticastSocket getMCSocket() {
        return this.MCSocket;
    }

    public MulticastSocket getMDBSocket() {
        return this.MDBSocket;
    }

    public MulticastSocket getMDRSocket() {
        return this.MDRSocket;
    }

    public StoredChunks getStoredChunks() {
        return storedChunks;
    }

    public StoredRecord getStoredRecord() {
        return storedRecord;
    }

    public RestoreRecord getRestoreRecord() {
        return restoreRecord;
    }

    public RemoveRecord getRemoveRecord() {
        return removeRecord;
    }

    public String getProtocolVersion() {
        return this.protocolVersion;
    }

    public String getID() {
        return this.ID;
    }

    public InetAddress getMCGroup() {
        return this.MCGroup;
    }

    public InetAddress getMDBGroup() {
        return this.MDBGroup;
    }

    public InetAddress getMDRGroup() {
        return this.MDRGroup;
    }

    public int getMCPort() {
        return this.MCPort;
    }

    public int getMDBPort() {
        return this.MDBPort;
    }

    public int getMDRPort() {
        return this.MDRPort;
    }

    public String getBackupDirPath() {
        return backupDirPath;
    }

    private String getRestoreDirPath() {
        return restoreDirPath;
    }

    public static void main(String[] args) {
        if (args.length != 9) {
            System.err.println("[Wrong number of arguments]");
            System.exit(-1);
        }

        String peerProtVersion = args[0]; // Protocol Version
        String peerID = args[1]; // Peer ID
        String peerAccessPoint = args[2];
        String MCName = args[3];
        String MCPort = args[4];
        String MDBName = args[5];
        String MDBPort = args[6];
        String MDRName = args[7];
        String MDRPort = args[8];

        try {
            Peer obj = new Peer(peerProtVersion, peerID, peerAccessPoint, MCName, MCPort, MDBName, MDBPort, MDRName,
                    MDRPort);
            RemoteInterface stub = (RemoteInterface) UnicastRemoteObject.exportObject(obj, 0);
            // Bind the remote object's stub in the registry
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(peerAccessPoint, stub);
            System.err.println("Peer ready");
        } catch (Exception e) {
            System.err.println("Peer exception: " + e.toString());
            e.printStackTrace();
        }

        // Saves the storage when the peer is interrupted
        Runtime.getRuntime().addShutdownHook(new Thread(Peer::serialize));
    }

    public void backup(String fileName, int replicationDegree) throws IOException, NoSuchAlgorithmException {
        try {
            final Path path = Paths.get(fileName);

            // Checks
            if (!Files.exists(path)) {
                System.out.println("File does not exist!");
                return;
            }

            File file = new File(fileName);
            byte[] body = Files.readAllBytes(file.toPath());
            FileMetadata fileMetadata = new FileMetadata(file, replicationDegree);
            String fileID = fileMetadata.getID();

            fileMetadata.makeChunks();
            System.out.println("Made " + fileMetadata.getChunks().size() + " chunks");
            for (Chunk chunk : fileMetadata.getChunks()) {
                String chunkKey = makeKey(Integer.toString(chunk.getID()), fileID);

                // If the file is not stored, it's added to the storage record
                if (storedRecord.getChunkInfo(chunkKey) == null) {
                    ChunkInfo chunkInfo = new ChunkInfo(chunk);
                    storedRecord.insert(chunkKey, chunkInfo);
                } else {
                    if (replicationDegree > storedRecord.getChunkInfo(chunkKey).getDesiredReplicationDegree()) {
                        storedRecord.getChunkInfo(chunkKey).setDesiredReplicationDegree(replicationDegree);
                    }
                }
                sendStopAndWait(chunk, replicationDegree, fileID, chunkKey);
            }

        } catch (Exception e) {
            System.err.println("Path exception: " + e.toString());
            e.printStackTrace();
            return;
        }
    }

    public void sendStopAndWait(Chunk chunk, int replicationDegree, String fileID, String chunkKey)
            throws IOException, SocketException {
        String putChunkMessage = "";
        int timesSent = 0;
        byte[] putChunkBuf = new byte[BACKUP_BUFFER_SIZE], content = chunk.getData();
        long limitTime = INITIAL_WAITING_TIME, startTime, elapsedTime;

        // Terminates after 5 unsuccessful attempts (2^n seconds) or when
        // the ammount of stores meets the desired replication degree
        do {
            // <Version> PUTCHUNK <SenderID> <FileID> <ChunkNo> <ReplicationDeg> <CRLF>
            // <CRLF> <Body>
            putChunkMessage = getProtocolVersion() + " PUTCHUNK " + getID() + " " + fileID + " " + chunk.getID() + " "
                    + replicationDegree + " " + CRLF + CRLF;
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byteArrayOutputStream.write(putChunkMessage.getBytes());
            byteArrayOutputStream.write(content);
            putChunkBuf = byteArrayOutputStream.toByteArray();
            DatagramPacket putChunkPacket = new DatagramPacket(putChunkBuf, putChunkBuf.length, getMDBGroup(),
                    getMDBPort());
            getMDBSocket().send(putChunkPacket);
            System.out.println("Sending chunk");

            timesSent++;
            startTime = System.currentTimeMillis();

            // Loops while the ammount of stores doesn't meet the desired replication degree
            while (storedRecord.getChunkInfo(chunkKey) != null
                    // Prevents peers sending PUTCHUNK caused by SPACE
                    // RECLAIMING from being registered as the file owner
                    ? storedRecord.getReplicationDegree(chunkKey) < chunk.getDesiredReplicationDegree()
                    : storedChunks.getReplicationDegree(chunkKey) < chunk.getDesiredReplicationDegree()) {
                elapsedTime = System.currentTimeMillis();
                if (elapsedTime - startTime > limitTime) {
                    limitTime *= 2;
                    break;
                }
            }
            // System.out.println("Actual: " + storedRecord.getReplicationDegree(chunkKey));
            // System.out.println("Desired: " + chunk.getDesiredReplicationDegree());
        } while ((storedRecord.getChunkInfo(chunkKey) != null
                // Prevents peers sending PUTCHUNK caused by SPACE
                // RECLAIMING from being registered as the file owner
                ? storedRecord.getReplicationDegree(chunkKey) < chunk.getDesiredReplicationDegree()
                : storedChunks.getReplicationDegree(chunkKey) < chunk.getDesiredReplicationDegree())
                && timesSent < MAX_ATTEMPTS);
        System.out.println("Backed up chunk " + chunk.getID());
    }

    public String makeKey(String chunkID, String fileID) {
        return chunkID + "_" + fileID;
    }

    public void delete(String fileName) throws IOException, NoSuchAlgorithmException {
        try {
            final Path path = Paths.get(fileName);

            // Check if any of the file's chunks is being stored by the peer
            if (!Files.exists(path)) {
                System.out.println("File does not exist!");
                return;
            }

        } catch (Exception e) {
            System.err.println("Path exception: " + e.toString());
            e.printStackTrace();
            return;
        }

        File file = new File(fileName);
        FileMetadata fileMetadata = new FileMetadata(file, 0);
        String fileID = fileMetadata.getID();

        // <Version> DELETE <SenderId> <FileId> <CRLF><CRLF>
        String deleteMessage = this.protocolVersion + " DELETE " + this.ID + " " + fileID + " " + CRLF + CRLF;
        byte[] deleteBuf = deleteMessage.getBytes();

        DatagramPacket deletePacket = new DatagramPacket(deleteBuf, deleteBuf.length, MCGroup, MCPort);
        MCSocket.send(deletePacket);
        storedRecord.removeFileChunks(fileID);
    }

    public void reclaim(int availableStorage) throws IOException, NoSuchAlgorithmException {
        String reclaimMessage;
        String key;
        this.storedChunks.setAvailableStorage(availableStorage);

        for (ChunkInfo chunkInfo : storedChunks.getChunks()) {
            this.putChunkSent = false;
            key = makeKey(Integer.toString(chunkInfo.getID()), chunkInfo.getFileID());
            // <Version> REMOVED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            reclaimMessage = this.protocolVersion + " REMOVED " + this.ID + " " + chunkInfo.getFileID() + " "
                    + chunkInfo.getID() + " " + CRLF + CRLF;

            try {
                byte[] reclaimBuf = reclaimMessage.getBytes();
                DatagramPacket reclaimPacket = new DatagramPacket(reclaimBuf, reclaimBuf.length, MCGroup, MCPort);
                MCSocket.send(reclaimPacket);
            } catch (Exception e) {
                e.printStackTrace();
            }

            File backupDir = new File(backupDirPath);
            File fileIDDir = new File(backupDir.getPath(), chunkInfo.getFileID());
            File file = new File(fileIDDir.getPath(), Integer.toString(chunkInfo.getID()));
            file.delete();

            // Deletes fileID directory if it is empty after the file deletion
            File[] fileIDDirectory = fileIDDir.listFiles();
            if (fileIDDirectory.length == 0)
                fileIDDir.delete();

            // Deletes backup directory if it is empty after the fileID directory deletion
            File[] backupDirectory = backupDir.listFiles();
            if (backupDirectory.length == 0)
                backupDir.delete();

            this.storedChunks.remove(key);

            if (this.storedChunks.getOccupiedStorage() <= this.storedChunks.getAvailableStorage())
                break;
        }
    }

    public void restore(String fileName) throws IOException, NoSuchAlgorithmException {
        try {
            final Path path = Paths.get(fileName);

            // Check if any of the file's chunks is being stored by the peer
            if (!Files.exists(path)) {
                System.out.println("File does not exist!");
                return;
            }

        } catch (Exception e) {
            System.err.println("Path exception: " + e.toString());
            e.printStackTrace();
            return;
        }

        File file = new File(fileName);
        byte[] body = Files.readAllBytes(file.toPath());
        FileMetadata fileMetadata = new FileMetadata(file, 0);
        String fileID = fileMetadata.getID();
        fileMetadata.makeChunks();
        int numberChunks = fileMetadata.getChunks().size();

        // If one of the chunks is not stored, the file itself should not be as well
        String key = makeKey(Integer.toString(fileMetadata.getChunks().get(0).getID()), fileID);
        if (storedRecord.getChunkInfo(key) == null) {
            System.out.println("That file was not successfully stored by any peer!");
            return;
        }

        for (Chunk chunk : fileMetadata.getChunks()) {
            if (chunk.getSize() == 0) {
                numberChunks--;
                continue;
            }

            // <Version> GETCHUNK <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            String getChunkMessage = this.protocolVersion + " GETCHUNK " + this.ID + " " + fileID + " " + chunk.getID()
                    + " " + CRLF + CRLF;
            byte[] getChunkBuf = getChunkMessage.getBytes();

            DatagramPacket getChunkPacket = new DatagramPacket(getChunkBuf, getChunkBuf.length, MCGroup, MCPort);
            MCSocket.send(getChunkPacket);
        }

        System.out.println("Waiting for " + numberChunks + " chunks...");

        while (true) {
            restoredChunks = restoreRecord.getRestoredChunks().size();
            if (restoredChunks == numberChunks)
                break;
        }

        System.out.println(restoreRecord.getRestoredChunks().size() + " chunks restored! Proceeding...");
        assembleFile(fileName);
        restoreRecord.printChunks();
    }

    public void assembleFile(String fileName) {
        try {
            // Store chunk
            final Path restoreDirPath = Paths.get(this.restoreDirPath);

            if (Files.notExists(restoreDirPath)) {
                Files.createDirectories(restoreDirPath);
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            for (Chunk chunk : restoreRecord.getRestoredChunks()) {
                byteArrayOutputStream.write(chunk.getData());
            }
            byte[] allChunks = byteArrayOutputStream.toByteArray();

            OutputStream outputStream = new FileOutputStream(restoreDirPath + "/" + fileName);
            outputStream.write(allChunks);
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void serialize() {
        serializeChunks();
        serializeRecord();
    }

    public static void serializeRecord() {
        try {
            FileOutputStream fileOutputStreamRecord = new FileOutputStream(storagePathRecord);
            ObjectOutputStream objectOutputStreamRecord = new ObjectOutputStream(fileOutputStreamRecord);
            objectOutputStreamRecord.writeObject(storedRecord);
            objectOutputStreamRecord.close();
            fileOutputStreamRecord.close();
            System.out.println("Data was stored under " + storagePathRecord);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deserializeRecord() {
        try {
            FileInputStream fileInputStreamRecord = new FileInputStream(storagePathRecord);
            ObjectInputStream objectInputStreamRecord = new ObjectInputStream(fileInputStreamRecord);
            storedRecord = (StoredRecord) objectInputStreamRecord.readObject();
            objectInputStreamRecord.close();
            fileInputStreamRecord.close();
            storedRecord.print();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public static void serializeChunks() {
        try {
            FileOutputStream fileOutputStreamChunks = new FileOutputStream(storagePathChunks);
            ObjectOutputStream objectOutputStreamChunks = new ObjectOutputStream(fileOutputStreamChunks);
            objectOutputStreamChunks.writeObject(storedChunks);
            objectOutputStreamChunks.close();
            fileOutputStreamChunks.close();
            System.out.println("Data was stored under " + storagePathChunks);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deserializeChunks() {
        try {
            FileInputStream fileInputStreamChunks = new FileInputStream(storagePathChunks);
            ObjectInputStream objectInputStreamChunks = new ObjectInputStream(fileInputStreamChunks);
            storedChunks = (StoredChunks) objectInputStreamChunks.readObject();
            objectInputStreamChunks.close();
            fileInputStreamChunks.close();
            storedChunks.print();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }
}
