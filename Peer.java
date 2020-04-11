import java.io.*;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.ServerSocket;
import java.net.Socket;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.Arrays;
import java.util.Random;
import java.text.DecimalFormat;
import static java.lang.Math.toIntExact;

// java Peer 1.0 1 AP1 230.0.0.0 4445 231.0.0.0 4446 232.0.0.0 4447

public class Peer implements RemoteInterface {
    private static final int BACKUP_BUFFER_SIZE = 64512; // bytes
    private static final String CRLF = "\r\n"; // CRLF delimiter
    private static final int INITIAL_WAITING_TIME = 1000; // 1 second
    private static final int RANDOM_TIME = 400; // milliseconds
    private static final int MAX_ATTEMPTS = 5;
    private static String ID;
    private static StoredRecord storedRecord;
    private static StoredChunks storedChunks;
    private static String storageDirPath;
    private static String backupDirPath;
    private static String restoreDirPath;
    private static String storagePathRecord;
    private static String storagePathChunks;
    private static ScheduledThreadPoolExecutor executor;
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
    private RestoreRecord restoreRecord;
    private volatile int restoredChunks = 0;
    private RemoveRecord removeRecord;
    private MulticastManager multicastManager;

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
        storageDirPath = "Peer" + "_" + ID;
        backupDirPath = storageDirPath + "/Backup";
        restoreDirPath = storageDirPath + "/Restore";
        storagePathRecord = storageDirPath + "/record.ser";
        storagePathChunks = storageDirPath + "/chunks.ser";
        final Path dirPath = Paths.get(storageDirPath);
        final Path pathRecord = Paths.get(storagePathRecord);
        final Path pathChunk = Paths.get(storagePathChunks);

        storedRecord = new StoredRecord();
        storedChunks = new StoredChunks();
        this.restoreRecord = new RestoreRecord();
        this.removeRecord = new RemoveRecord();

        executor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(100);

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
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
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

    public synchronized RestoreRecord getRestoreRecord() {
        return restoreRecord;
    }

    public RemoveRecord getRemoveRecord() {
        return removeRecord;
    }

    public String getProtocolVersion() {
        return this.protocolVersion;
    }

    public String getID() {
        return ID;
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
            storedRecord.insertFileName(fileID, fileName);

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

    public void sendStopAndWait(Chunk chunk, int replicationDegree, String fileID, String chunkKey) throws IOException {
        String putChunkMessage = "";
        int timesSent = 0;
        byte[] putChunkBuf = new byte[BACKUP_BUFFER_SIZE], content = chunk.getData();
        long limitTime = INITIAL_WAITING_TIME;
        int nSleeps = 0;

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

            // Awaits for STORED messages
            try {
                Thread.sleep(limitTime);
                limitTime *= 2;
            } catch (Exception e) {
                System.err.println("Thread sending PUTCHUNK messages was interrupted.");
                e.printStackTrace();
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
        String deleteMessage = this.protocolVersion + " DELETE " + ID + " " + fileID + " " + CRLF + CRLF;
        byte[] deleteBuf = deleteMessage.getBytes();

        DatagramPacket deletePacket = new DatagramPacket(deleteBuf, deleteBuf.length, MCGroup, MCPort);
        MCSocket.send(deletePacket);
        storedRecord.removeFileChunks(fileID);
    }

    public void reclaim(int availableStorage) throws IOException, NoSuchAlgorithmException {
        String reclaimMessage;
        String key;
        storedChunks.setAvailableStorage(availableStorage);

        for (ChunkInfo chunkInfo : storedChunks.getChunks()) {

            if (storedChunks.getOccupiedStorage() <= storedChunks.getAvailableStorage())
                break;

            key = makeKey(Integer.toString(chunkInfo.getID()), chunkInfo.getFileID());
            // <Version> REMOVED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            reclaimMessage = this.protocolVersion + " REMOVED " + ID + " " + chunkInfo.getFileID() + " "
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

            storedChunks.remove(key);

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
            String getChunkMessage = this.protocolVersion + " GETCHUNK " + ID + " " + fileID + " " + chunk.getID() + " "
                    + CRLF + CRLF;
            byte[] getChunkBuf = getChunkMessage.getBytes();

            DatagramPacket getChunkPacket = new DatagramPacket(getChunkBuf, getChunkBuf.length, MCGroup, MCPort);
            MCSocket.send(getChunkPacket);
        }

        System.out.println("Waiting for " + numberChunks + " chunks...");

        while (true) {
            restoredChunks = restoreRecord.getRestoredChunks().size();
            if (restoredChunks == numberChunks) {
                break;
            }
        }

        System.out.println(restoreRecord.getRestoredChunks().size() + " chunks restored! Proceeding...");
        assembleFile(fileName);
        System.out.println("Assembling...\nNumber of chunks: " + restoreRecord.getRestoredChunks().size());
        System.out.println("Number of entries: " + restoreRecord.getRestoredSet().size());
        restoreRecord.printChunks();
        this.restoreRecord.clear();
        System.out.println("Clearing...\nNumber of chunks: " + restoreRecord.getRestoredChunks().size());
        System.out.println("Number of entries: " + restoreRecord.getRestoredSet().size());
        restoreRecord.print();
    }

    public String state() {
        String state = "\nPeer ID: " + ID;
        state += "\n Backups: \n";
        state += storedRecord.print();
        state += "\n Stored File Chunks: \n";
        state += storedChunks.print();
        state += "\nMaximum Storage Capacity: " + storedChunks.getAvailableStorage() + " Bytes";
        DecimalFormat decimalFormat = new DecimalFormat("###.##%");
        double ratio = storedChunks.getAvailableStorage() != 0
                ? (double) storedChunks.getOccupiedStorage() / storedChunks.getAvailableStorage()
                : 0;
        state += "\nOccupied Storage: " + storedChunks.getOccupiedStorage() + " Bytes ( " + decimalFormat.format(ratio)
                + " )\n";

        return state;
    }

    public String makeKey(String chunkID, String fileID) {
        return chunkID + "_" + fileID;
    }

    public void assembleFile(String fileName) {
        try {
            // Store chunk
            final Path restoreDirPath = Paths.get(Peer.restoreDirPath);

            if (Files.notExists(restoreDirPath)) {
                Files.createDirectories(restoreDirPath);
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            for (Chunk chunk : restoreRecord.getRestoredChunks()) {
                System.out.println("Copying form chunk "+ chunk.getID());
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

    public void sendOverTCP(String receiverID, String protocolVersion, String chunkID, String fileID, byte[] chunkBody)
            throws IOException {
        Random rand = new Random();
        int randomFactor = rand.nextInt(2 * RANDOM_TIME);
        // avoids port collision
        // int portNumber = (Integer.parseInt(ID) +
        // toIntExact(Thread.currentThread().getId()) + Integer.parseInt(chunkID)
        // + randomFactor) % 65535;
        int portNumber = (Integer.parseInt(chunkID) + randomFactor) % 65535;
        System.out.println("PORT => " + portNumber);
        String hostName = "127.0.0.1"; // localhost
        String connectMessage = "";

        // <Version> CONNECT <SenderID> <FileID> <ChunkNo> <hostName>
        // <port> <CRLF><CRLF>
        connectMessage += protocolVersion + " CONNECT " + ID + " " + fileID + " " + chunkID + " " + hostName + " "
                + portNumber + " " + CRLF + CRLF;
        byte[] connectBuf = connectMessage.getBytes();
        DatagramPacket connectPacket = new DatagramPacket(connectBuf, connectBuf.length, MDRGroup, MDRPort);
        MulticastSocket chunkSocket = MDRSocket;
        
        chunkSocket.send(connectPacket);
        try (ServerSocket serverSocket = new ServerSocket(portNumber);
                Socket clientSocket = serverSocket.accept();
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);) {
            // System.out.println("Accepted TCP connection");
            out.println(new String(chunkBody, 0, chunkBody.length, StandardCharsets.UTF_8));
            System.out.println("Sent " + chunkBody.length + " bytes");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public void receiveOverTCP(String hostName, String port, String chunkNumber, String fileID) {
        int portNumber = Integer.parseInt(port);
        String fromServer;
        char[] bodyRead = new char[BACKUP_BUFFER_SIZE];
        String chunkKey = makeKey(chunkNumber, fileID);
        int bytesRead = 0;

        if (!getRestoreRecord().isRestored(chunkKey)) {
            getRestoreRecord().insertKey(chunkKey);

            try (Socket restoreSocket = new Socket(hostName, portNumber);
                    BufferedReader in = new BufferedReader(new InputStreamReader(restoreSocket.getInputStream()));) {
                System.out.println("Receiving over TCP");
                bytesRead = in.read(bodyRead);
                in.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (bytesRead > CRLF.length())
                bytesRead -= CRLF.length();

            System.out.println("size: " + (bytesRead));
            byte[] chunkBody = Arrays.copyOfRange(new String(bodyRead).getBytes(StandardCharsets.UTF_8), 0, bytesRead);
            Chunk chunk = new Chunk(Integer.parseInt(chunkNumber), fileID, bytesRead, 0, "UNKNOWN");
            chunk.setData(chunkBody);
            getRestoreRecord().insertChunk(chunk);
        }
    }
}