import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.net.MulticastSocket;

public class Peer implements RemoteInterface {
    int ID;

    public Peer() {
    }

    public static void main(String[] args) {
        String accessPoint = args[0];

        try {
            Peer obj = new Peer();
            RemoteInterface stub = (RemoteInterface) UnicastRemoteObject.exportObject(obj, 0);

            // Bind the remote object's stub in the registry
            Registry registry = LocateRegistry.getRegistry();
            registry.bind(accessPoint, stub);

            System.err.println("Peer ready");
            if (args.length == 2) {
                receive();
            }
        } catch (Exception e) {
            System.err.println("Peer exception: " + e.toString());
            e.printStackTrace();
        }
    }

    public void backup(String fileName, int replicationDegree) throws IOException, NoSuchAlgorithmException {
        String version = "1.0";
        int chunkNumber = 1;
        String CRLF = "0xD0xA";
        File file = new File(fileName);
        byte[] body = Files.readAllBytes(file.toPath());
        FileMetadata fileMetadata = new FileMetadata(file, replicationDegree);

        fileMetadata.makeChunks();

        // <Version> PUTCHUNK <SenderID> <FileID> <ChunkNo> <ReplicationDeg> <CRLF>
        // <CRLF> <Body>
        String message = version + " PUTCHUNK " + this.ID + " " + fileMetadata.getID() + " " + chunkNumber + " "
                + replicationDegree + " " + CRLF + CRLF + " " + body;

        System.out.println(message);
        broadcast();
    }

    public void broadcast() throws UnknownHostException, SocketException {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        DatagramSocket socket;
        InetAddress group;
        byte[] buf;

        socket = new DatagramSocket();
        group = InetAddress.getByName("230.0.0.0");
        buf = "multicastMessage".getBytes();

        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, 4445);

        final Runnable sendPacket = new Runnable() {
            public void run() {
                try {
                    socket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Advertisement Sent!");
            }
        };

        ScheduledFuture<?> timer = scheduler.scheduleWithFixedDelay(sendPacket, 1, 1, SECONDS);

    }

    public static void receive() throws IOException {
        MulticastSocket socket = null;
        byte[] buf = new byte[256];

        socket = new MulticastSocket(4445);
        InetAddress group = InetAddress.getByName("230.0.0.0");
        socket.joinGroup(group);

        while (true) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            socket.receive(packet);
            String received = new String(packet.getData(), 0, packet.getLength());
            System.out.println(received);
        }

        // socket.leaveGroup(group);
        // socket.close();
    }
}
