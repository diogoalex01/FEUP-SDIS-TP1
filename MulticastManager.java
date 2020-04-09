import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.MulticastSocket;
import java.security.NoSuchAlgorithmException;

public class MulticastManager {
    Peer peer;
    private static final int BACKUP_BUFFER_SIZE = 64512; // bytes
    private static ExecutorService executor;

    public MulticastManager(Peer peer) {
        this.peer = peer;
        this.executor = (ExecutorService) Executors.newWorkStealingPool();

        Runnable readMC = () -> {
            byte[] buf = new byte[BACKUP_BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buf, buf.length, this.peer.getMCGroup(), this.peer.getMCPort());

            while (true) {
                try {
                    this.peer.getMCSocket().receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength());
                    this.executor.execute(new MCParser(this.peer, received));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        Runnable readMDB = () -> {
            while (true) {
                byte[] buf = new byte[BACKUP_BUFFER_SIZE];
                DatagramPacket packet = new DatagramPacket(buf, buf.length, this.peer.getMDBGroup(),
                        this.peer.getMDBPort());
                try {
                    this.peer.getMDBSocket().receive(packet);
                    this.executor.execute(new MDBParser(this.peer, packet));

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        Runnable readMDR = () -> {
            while (true) {
                byte[] buf = new byte[BACKUP_BUFFER_SIZE];
                DatagramPacket packet = new DatagramPacket(buf, buf.length, this.peer.getMDRGroup(),
                        this.peer.getMDRPort());
                try {
                    this.peer.getMDRSocket().receive(packet);
                    this.executor.execute(new MDRParser(this.peer, packet));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        new Thread(readMC).start();
        new Thread(readMDB).start();
        new Thread(readMDR).start();
    }
}