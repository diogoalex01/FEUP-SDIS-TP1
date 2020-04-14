import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CRL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.ArrayList;
import java.util.Iterator;
import java.text.DecimalFormat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.*;

import java.sql.Timestamp;
import java.util.Map;

// java Peer 1.0 1 AP1 230.0.0.0 4445 231.0.0.0 4446 232.0.0.0 4447

public class Peer implements RemoteInterface {
    private static final int BACKUP_BUFFER_SIZE = 64512; // bytes
    private static final String CRLF = "\r\n"; // CRLF delimiter
    private static final int INITIAL_WAITING_TIME = 1000; // 1 second
    private static final int MAX_ATTEMPTS = 5;
    private static String ID;
    private static StoredRecord storedRecord;
    private static StoredChunks storedChunks;
    private static Timeline timeline;
    private static String storageDirPath;
    private static String backupDirPath;
    private static String restoreDirPath;
    private static String storagePathRecord;
    private static String storagePathChunks;
    private static String storagePathTimeline;
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
        storagePathTimeline = storageDirPath + "/timeline.ser";
        final Path dirPath = Paths.get(storageDirPath);
        final Path pathRecord = Paths.get(storagePathRecord);
        final Path pathChunk = Paths.get(storagePathChunks);
        final Path pathTimeline = Paths.get(storagePathTimeline);

        storedRecord = new StoredRecord();
        storedChunks = new StoredChunks(backupDirPath);
        timeline = new Timeline();
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

            if (Files.exists(pathTimeline)) {
                System.out.println("Reading storage from " + pathTimeline);
                deserializeChunks();
            } else {
                Files.createDirectories(dirPath);
                Files.write(pathTimeline, new byte[0]);
                System.out.println("Storage created under " + pathTimeline);
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

        if (!protocolVersion.equals("1.0")) {
            // Once ready, a peer asks for updates on deleted files
            ScheduledExecutorService execService = Executors.newScheduledThreadPool(5);
            execService.schedule(() -> {
                askForUpdate();
            }, 0, TimeUnit.MILLISECONDS);
        }
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
        serializeTimeline();
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

    public static void serializeTimeline() {
        try {
            FileOutputStream fileOutputStreamTimeline = new FileOutputStream(storagePathTimeline);
            ObjectOutputStream objectOutputStreamTimeline = new ObjectOutputStream(fileOutputStreamTimeline);
            objectOutputStreamTimeline.writeObject(timeline);
            objectOutputStreamTimeline.close();
            fileOutputStreamTimeline.close();
            System.out.println("Data was stored under " + storagePathTimeline);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deserializeTimeline() {
        try {
            FileInputStream fileInputStreamTimeline = new FileInputStream(storagePathTimeline);
            ObjectInputStream objectInputStreamTimeline = new ObjectInputStream(fileInputStreamTimeline);
            timeline = (Timeline) objectInputStreamTimeline.readObject();
            objectInputStreamTimeline.close();
            fileInputStreamTimeline.close();
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

    public Timeline getTimeline() {
        return timeline;
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
            FileMetadata fileMetadata = new FileMetadata(file, replicationDegree);
            String fileID = fileMetadata.getID();
            Boolean keepSending;

            storedRecord.insertFileName(fileID, fileName);
            if (timeline.wasDeleted(fileID)) {
                timeline.removeDeletion(fileID);
            }

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
                keepSending = sendStopAndWait(chunk, replicationDegree, fileID, chunkKey);
                if (!keepSending) {
                    System.out.println("Failed to achieve replication degree. Aborting BACKUP.");
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public boolean sendStopAndWait(Chunk chunk, int replicationDegree, String fileID, String chunkKey)
            throws IOException {
        String putChunkMessage = "";
        int timesSent = 0;
        byte[] putChunkBuf = new byte[BACKUP_BUFFER_SIZE], content = chunk.getData();
        long limitTime = INITIAL_WAITING_TIME;
        boolean success = false;

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
            timesSent++;

            // Awaits for STORED messages
            try {
                Thread.sleep(limitTime);
                limitTime *= 2;
            } catch (Exception e) {
                System.err.println("Thread sending PUTCHUNK messages was interrupted.");
                e.printStackTrace();
            }
        } while ((storedRecord.getChunkInfo(chunkKey) != null
                // Prevents peers sending PUTCHUNK caused by SPACE
                // RECLAIMING from being registered as the file owner
                ? storedRecord.getReplicationDegree(chunkKey) < chunk.getDesiredReplicationDegree()
                : storedChunks.getReplicationDegree(chunkKey) < chunk.getDesiredReplicationDegree())
                && timesSent < MAX_ATTEMPTS);

        if (storedRecord.getChunkInfo(chunkKey) != null) {
            success = storedRecord.getReplicationDegree(chunkKey) >= chunk.getDesiredReplicationDegree();
        } else {
            success = storedChunks.getReplicationDegree(chunkKey) >= chunk.getDesiredReplicationDegree();
        }

        return success;
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
        storedChunks.setAvailableStorage(availableStorage * 1000);
        ArrayList<String> removedKeys = new ArrayList<>();

        for (ChunkInfo chunkInfo : storedChunks.getChunks()) {
            try {
                final Path path = Paths.get(backupDirPath + "/" + chunkInfo.getFileID() + "/" + chunkInfo.getID());

                if (Files.exists(path)) {
                    if (storedChunks.getOccupiedStorage() <= storedChunks.getAvailableStorage())
                        break;

                    key = makeKey(Integer.toString(chunkInfo.getID()), chunkInfo.getFileID());
                    // <Version> REMOVED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
                    reclaimMessage = this.protocolVersion + " REMOVED " + ID + " " + chunkInfo.getFileID() + " "
                            + chunkInfo.getID() + " " + CRLF + CRLF;

                    try {
                        byte[] reclaimBuf = reclaimMessage.getBytes();
                        DatagramPacket reclaimPacket = new DatagramPacket(reclaimBuf, reclaimBuf.length, MCGroup,
                                MCPort);
                        MCSocket.send(reclaimPacket);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    File backupDir = new File(backupDirPath);
                    File fileIDDir = new File(backupDir.getPath(), chunkInfo.getFileID());
                    File file = new File(fileIDDir.getPath(), Integer.toString(chunkInfo.getID()));
                    // System.out.println("delete File " + chunkInfo.getID());
                    file.delete();

                    // Deletes fileID directory if it is empty after the file deletion
                    File[] fileIDDirectory = fileIDDir.listFiles();
                    if (fileIDDirectory != null) {
                        if (fileIDDirectory.length == 0) {
                            // System.out.println("delete da pasta");
                            fileIDDir.delete();
                        }
                    }

                    // Deletes backup directory if it is empty after the fileID directory deletion
                    File[] backupDirectory = backupDir.listFiles();
                    if (backupDirectory.length == 0) {
                        // System.out.println("delete da pasta de backup");
                        backupDir.delete();
                    }

                    storedChunks.updateOccupiedStorage(-1 * storedChunks.getChunkInfo(key).getSize());
                    removedKeys.add(key);
                }

            } catch (Exception e) {
                System.err.println("Path exception: " + e.toString());
                e.printStackTrace();
                return;
            }
        }

        for (String aKey : removedKeys) {
            storedChunks.remove(aKey);
        }

    }

    public void restore(String fileName) throws IOException, NoSuchAlgorithmException {
        try {
            final Path path = Paths.get(fileName);

            // Checks if file exists
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
        fileMetadata.makeChunks();
        int numberChunks = fileMetadata.getChunks().size();
        this.restoreRecord.clear();
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
        System.out.println("Assembling file...\nNumber of chunks: " + restoreRecord.getRestoredChunks().size());
        this.restoreRecord.clear();
    }

    public String state() {
        String state = "\n> Peer ID: " + ID;
        state += "\n----------------------------------";
        state += "\n------------- Backups ------------\n";
        state += storedRecord.print();
        state += "-------- Stored File Chunks --------\n";
        state += storedChunks.print();
        state += "Maximum Storage Capacity: " + storedChunks.getAvailableStorage() + " KBytes";
        DecimalFormat decimalFormat = new DecimalFormat("###.## %");
        double ratio = storedChunks.getAvailableStorage() != 0
                ? (double) storedChunks.getOccupiedStorage() / storedChunks.getAvailableStorage()
                : 0;
        state += "\nOccupied Storage: " + storedChunks.getOccupiedStorage() + " KBytes ( " + decimalFormat.format(ratio)
                + " )\n";

        return state;
    }

    public String makeKey(String chunkID, String fileID) {
        return chunkID + "_" + fileID;
    }

    public void assembleFile(String fileName) {
        String realFileName;
        int slashIndex = fileName.lastIndexOf("\\");

        if (slashIndex != -1) {
            realFileName = fileName.substring(slashIndex + 1);
        } else {
            slashIndex = fileName.lastIndexOf("/");
            if (slashIndex != -1) {
                realFileName = fileName.substring(slashIndex + 1);
            } else {
                realFileName = fileName;
            }
        }

        try {
            // Store chunk
            final Path restoreDirPath = Paths.get(Peer.restoreDirPath);

            if (Files.notExists(restoreDirPath)) {
                Files.createDirectories(restoreDirPath);
            }

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            for (Chunk chunk : restoreRecord.getRestoredChunks()) {
                System.out.println("Copying form chunk " + chunk.getID() + " with size " + chunk.getSize() + " to file "
                        + realFileName);
                byteArrayOutputStream.write(chunk.getData());
            }

            byte[] allChunks = byteArrayOutputStream.toByteArray();
            OutputStream outputStream = new FileOutputStream(restoreDirPath + "/" + realFileName);
            outputStream.write(allChunks);
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendOverTCP(String receiverID, String protocolVersion, String chunkID, String fileID, byte[] chunkBody)
            throws IOException {
        String connectMessage = "";

        // Returns the preferred outbound IP
        DatagramSocket socket = new DatagramSocket();
        socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
        String hostname = socket.getLocalAddress().getHostAddress();
        socket.close();

        try {
            ServerSocket serverSocket = new ServerSocket(0); // Uses the first available port
            int portNumber = serverSocket.getLocalPort();

            // <Version> CONNECT <SenderID> <FileID> <ChunkNo> <Hostname>
            // <Port> <CRLF><CRLF>
            connectMessage += protocolVersion + " CONNECT " + ID + " " + fileID + " " + chunkID + " " + hostname + " "
                    + portNumber + " " + CRLF + CRLF;
            byte[] connectBuf = connectMessage.getBytes();
            DatagramPacket connectPacket = new DatagramPacket(connectBuf, connectBuf.length, MDRGroup, MDRPort);
            MulticastSocket chunkSocket = MDRSocket;
            chunkSocket.send(connectPacket);

            Socket clientSocket = serverSocket.accept();
            OutputStream out = clientSocket.getOutputStream();
            out.write(chunkBody);
            out.flush();

            serverSocket.close();
            clientSocket.close();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public void receiveOverTCP(String hostname, String port, String chunkNumber, String fileID) {
        int portNumber = Integer.parseInt(port);
        byte[] bodyRead = new byte[BACKUP_BUFFER_SIZE];
        byte[] chunkBody = new byte[BACKUP_BUFFER_SIZE];
        String chunkKey = makeKey(chunkNumber, fileID);
        int bytesRead = -1;

        if (!getRestoreRecord().isRestored(chunkKey)) {
            getRestoreRecord().insertKey(chunkKey);

            try {
                Socket restoreSocket = new Socket(hostname, portNumber);
                InputStream inputStream = restoreSocket.getInputStream();

                // Read from the stream
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                while ((bytesRead = inputStream.read(bodyRead)) != -1) {
                    buffer.write(bodyRead, 0, bytesRead);
                }

                buffer.flush();
                chunkBody = buffer.toByteArray();

                buffer.close();
                restoreSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            Chunk chunk = new Chunk(Integer.parseInt(chunkNumber), fileID, chunkBody.length, 0, "UNKNOWN");
            chunk.setData(chunkBody);
            getRestoreRecord().insertChunk(chunk);
        }
    }

    public void askForUpdate() {
        // <Version> UPDATE <Placeholder> <Placeholder> <CRLF><CRLF>
        String updateMessage = this.protocolVersion + " UPDATE" + " " + ID + " " + "<Placeholder>" + CRLF + CRLF;
        byte[] updateBuf = updateMessage.getBytes();
        DatagramPacket updatePacket = new DatagramPacket(updateBuf, updateBuf.length, MCGroup, MCPort);
        try {
            System.out.println("Asking for updates.");
            MCSocket.send(updatePacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendAllDeletions() {
        // <Version> DELETIONS <SenderID> <Placeholder> <Placeholder> <Body>
        String deletionsMessage = "";
        String body = "";
        deletionsMessage = this.protocolVersion + " DELETIONS " + ID + " " + "<Placeholder>" + " " + "<Placeholder>"
                + " ";
        Iterator<Map.Entry<String, java.sql.Timestamp>> itr = this.getTimeline().getMap().entrySet().iterator();
        while (itr.hasNext()) {
            body += itr.next().getKey() + " ";
        }

        deletionsMessage += body.substring(0, body.length() - 1);
        byte[] deletionsBuf = deletionsMessage.getBytes();
        DatagramPacket deletionsPacket = new DatagramPacket(deletionsBuf, deletionsBuf.length, MDRGroup, MDRPort);
        try {
            MDRSocket.send(deletionsPacket);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteAllChunks(String fileID) {
        File backupDir = new File(backupDirPath);
        File fileIDDir = new File(backupDir.getPath(), fileID);
        if (fileIDDir.exists()) {
            storedChunks.removeFileChunksWithStorage(fileID);
            String[] entries = fileIDDir.list();
            for (String entry : entries) {
                File currentFile = new File(fileIDDir.getPath(), entry);
                currentFile.delete();
            }

            fileIDDir.delete();

            // Deletes the backup directory if it's empty after the fileID dir deletion
            File[] backupDirectory = backupDir.listFiles();
            if (backupDirectory.length == 0)
                backupDir.delete();
        }

        timeline.insertDeletion(fileID);
        storedChunks.removeFileChunks(fileID);
        storedRecord.removeFileChunks(fileID);
    }
}