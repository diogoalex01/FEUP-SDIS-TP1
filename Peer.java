import java.io.*;
import java.nio.file.Files;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.SECONDS;
import java.net.MulticastSocket;

import java.util.Random;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

// java Peer 1.0 1 AP1 230.0.0.0 4445 231.0.0.0 4446 232.0.0.0 4447

public class Peer implements RemoteInterface {
    private static String ID;
    private String accessPoint;
    private String protocolVersion;
    private InetAddress group;
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
    private static String storagePathRecord;
    private static String storagePathChunks;

    private static final int BACKUP_BUFFER_SIZE = 64512; // bytes
    private static final String CRLF = "\r\n"; // CRLF delimiter
    private static final int INITIAL_WAITING_TIME = 1000; // 1 second
    private static final int MAX_ATTEMPTS = 5;

    final Runnable ReadMC = new Runnable() {
        byte[] buf = new byte[BACKUP_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length, MCGroup, MCPort);

        public void run() {
            try {
                MCSocket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());

                parseMessageMC(received);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    final Runnable ReadMDB = new Runnable() {
        byte[] buf = new byte[BACKUP_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length, MDBGroup, MDBPort);

        public void run() {
            try {
                MDBSocket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());
                parseMessageMDB(received);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    final Runnable ReadMDR = new Runnable() {
        byte[] buf = new byte[BACKUP_BUFFER_SIZE];
        DatagramPacket packet = new DatagramPacket(buf, buf.length, MDRGroup, MDRPort);

        public void run() {
            try {
                MDRSocket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());
                // parseMessageMDR(received);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    public void parseMessageMC(String received) {
        // <Version> STORED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
        System.out.println("REC: " + received);
        String[] receivedMessage = received.split("[\\s]+");
        System.out.println("Size : " + receivedMessage.length);
        String protocolVersion = receivedMessage[0];
        String command = receivedMessage[1];
        String senderID = receivedMessage[2];
        String fileID = receivedMessage[3];
        String chunkNumber = receivedMessage[4];
        String key = makeKey(chunkNumber, fileID);

        if (command.equals("STORED")) {
            System.out.println("STORED");
            if (storedChunks.getChunkInfo(key) != null) {
                storedChunks.getChunkInfo(key).updateActualReplicationDegree(1);
                System.out.println("Updating store count");
            }
            if (storedRecord.getChunkInfo(key) != null) {
                storedRecord.getChunkInfo(key).updateActualReplicationDegree(1);
                System.out.println("Updating store count");
            }
        }
    }

    public void parseMessageMDB(String received) throws IOException, InterruptedException {
        // <Version> PUTCHUNK <SenderId> <FileId> <ChunkNo> <ReplicationDeg>
        // <CRLF><CRLF> <Body>
        Random rand = new Random();
        String[] receivedMessage = received.split("[\\s]+", 8);
        String protocolVersion = receivedMessage[0];
        String command = receivedMessage[1];
        String senderID = receivedMessage[2];
        String fileID = receivedMessage[3];
        String chunkNumber = receivedMessage[4];
        String replicationDegree = receivedMessage[5];
        String chunkBody = receivedMessage[7];
        String key = makeKey(chunkNumber, fileID);

        if (senderID.equals(this.ID))
            return;

        if (command.equals("PUTCHUNK")) {
            System.out.println("PUTCHUNK");
            String chunkFileName = storageDirPath + "/" + this.ID + "_" + fileID + "_" + chunkNumber + ".txt";

            // Check if file already exists
            try {
                final Path path = Paths.get(chunkFileName);

                if (Files.exists(path)) {
                    System.out.println("000");
                    return;
                }

            } catch (Exception e) {
                System.err.println("Path exception: " + e.toString());
                e.printStackTrace();
            }

            // Store chunk
            FileWriter fileWriter = new FileWriter(chunkFileName);
            fileWriter.write(chunkBody);
            fileWriter.close();

            // Wait random amount of time
            int randomTime = rand.nextInt(400);
            Thread.sleep(randomTime);
            System.out.println("waited " + randomTime);

            // Reply to sender
            // <Version> STORED <SenderId> <FileId> <ChunkNo> <CRLF><CRLF>
            String storedMessage = this.protocolVersion + " STORED " + this.ID + " " + fileID + " " + chunkNumber + " "
                    + CRLF + CRLF;
            byte[] storedBuf = storedMessage.getBytes();
            DatagramPacket storedReply = new DatagramPacket(storedBuf, storedBuf.length, MCGroup, MCPort);
            MCSocket.send(storedReply);
            ChunkInfo chunkInfo = new ChunkInfo(Integer.parseInt(chunkNumber), fileID, chunkBody.length(),
                    Integer.parseInt(replicationDegree));
            storedChunks.insert(key, chunkInfo);
            System.out.println("Sent STORED");
        } else {
            System.out.println("PUTCHUNK command not found!");
        }
    }

    public Peer(String peerProtocolVersion, String peerID, String peerAccessPoint, String MCName, String MCPort,
            String MDBName, String MDBPort, String MDRName, String MDRPort) throws IOException {
        protocolVersion = peerProtocolVersion;
        ID = peerID;
        accessPoint = peerAccessPoint;
        MCGroup = InetAddress.getByName(MCName);
        MDBGroup = InetAddress.getByName(MDBName);
        MDRGroup = InetAddress.getByName(MDRName);
        this.MCPort = Integer.parseInt(MCPort);
        this.MDBPort = Integer.parseInt(MDBPort);
        this.MDRPort = Integer.parseInt(MDRPort);
        storageDirPath = "Peer" + "_" + this.ID;
        storagePathRecord = "Peer" + "_" + this.ID + "/record.ser";
        storagePathChunks = "Peer" + "_" + this.ID + "/chunks.ser";
        final Path dirPath = Paths.get(storageDirPath);
        final Path pathRecord = Paths.get(storagePathRecord);
        final Path pathChunk = Paths.get(storagePathChunks);
        this.storedRecord = new StoredRecord();
        this.storedChunks = new StoredChunks();

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
        ScheduledExecutorService MCScheduler = Executors.newScheduledThreadPool(1);
        ScheduledFuture<?> MCtimer = MCScheduler.scheduleWithFixedDelay(ReadMC, 1, 1, SECONDS);

        // Join MDB channel
        MDBSocket = new MulticastSocket(this.MDBPort);
        MDBSocket.joinGroup(MDBGroup);
        ScheduledExecutorService MDBScheduler = Executors.newScheduledThreadPool(1);
        ScheduledFuture<?> MDBtimer = MDBScheduler.scheduleWithFixedDelay(ReadMDB, 1, 1, SECONDS);

        // Join MDR channel
        MDRSocket = new MulticastSocket(this.MDRPort);
        MDRSocket.joinGroup(MDRGroup);
        ScheduledExecutorService MDRScheduler = Executors.newScheduledThreadPool(1);
        ScheduledFuture<?> MDRtimer = MDRScheduler.scheduleWithFixedDelay(ReadMDR, 1, 1, SECONDS);
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

        Runtime.getRuntime().addShutdownHook(new Thread(Peer::serialize));
    }

    public void backup(String fileName, int replicationDegree) throws IOException, NoSuchAlgorithmException {
        File file = new File(fileName);
        System.out.println("Replication degree is " + replicationDegree);
        byte[] body = Files.readAllBytes(file.toPath());
        FileMetadata fileMetadata = new FileMetadata(file, replicationDegree);
        String fileID = fileMetadata.getID();
        fileMetadata.makeChunks();

        // <Version> PUTCHUNK <SenderID> <FileID> <ChunkNo> <ReplicationDeg> <CRLF>
        // <CRLF> <Body>

        String message = "";
        for (Chunk chunk : fileMetadata.getChunks()) {
            sendStopAndWait(chunk, replicationDegree, fileID);
        }

        // System.out.println(message);
        // broadcast();
    }

    public void broadcast() throws UnknownHostException, SocketException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        byte[] buf;
        buf = "multicastMessage".getBytes();

        DatagramPacket MCPacket = new DatagramPacket(buf, buf.length, MCGroup, MCPort);
        DatagramPacket MDBPacket = new DatagramPacket(buf, buf.length, MDBGroup, MDBPort);
        DatagramPacket MDRPacket = new DatagramPacket(buf, buf.length, MDRGroup, MDRPort);

        final Runnable sendPacket = new Runnable() {
            public void run() {
                try {
                    MCSocket.send(MCPacket);
                    MDBSocket.send(MDBPacket);
                    MDRSocket.send(MDRPacket);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                System.out.println("Advertisement Sent!");
            }
        };

        ScheduledFuture<?> timer = scheduler.scheduleWithFixedDelay(sendPacket, 1, 1, SECONDS);
    }

    public void sendStopAndWait(Chunk chunk, int replicationDegree, String fileID) throws IOException, SocketException {
        String message = "";
        int storeCounter = 0, timesSent = 0;
        byte[] bufMDB = new byte[BACKUP_BUFFER_SIZE];
        byte[] bufMC = new byte[256];
        String content = new String(chunk.getData());
        long limitTime = INITIAL_WAITING_TIME, startTime, elapsedTime;

        String chunkKey = makeKey(Integer.toString(chunk.getID()), fileID);
        ChunkInfo chunkInfo = new ChunkInfo(chunk);
        storedRecord.insert(chunkKey, chunkInfo);

        // Terminates after 5 unsuccessful attempts (2^n seconds) or when
        // the ammount of stores meets the desired replication degree
        while (storedRecord.getReplicationDegree(chunkKey) < chunk.getDesiredReplicationDegree()
                && timesSent < MAX_ATTEMPTS) {
            message = this.protocolVersion + " PUTCHUNK " + this.ID + " " + fileID + " " + chunk.getID() + " "
                    + replicationDegree + " " + CRLF + CRLF + " " + content;

            bufMDB = message.getBytes();
            DatagramPacket commandPacket = new DatagramPacket(bufMDB, bufMDB.length, MDBGroup, MDBPort);
            MDBSocket.send(commandPacket);
            timesSent++;

            // Loops while the ammount of stores doesn't meet the desired replication degree
            startTime = System.currentTimeMillis();

            while (storedRecord.getReplicationDegree(chunkKey) < chunk.getDesiredReplicationDegree()) {
                elapsedTime = System.currentTimeMillis();
                if (elapsedTime - startTime > limitTime) {
                    System.out.println("LIMIT: " + limitTime);
                    limitTime *= 2;
                    break;
                }
            }
        }

        System.out.println("Backed up chunk " + chunk.getID());
    }

    public String makeKey(String chunkID, String fileID) {
        return chunkID + "_" + fileID;
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
