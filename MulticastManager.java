import java.net.DatagramPacket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class MulticastManager {
    private static final int BACKUP_BUFFER_SIZE = 64512; // bytes
    private static ScheduledThreadPoolExecutor executor;
    Peer peer;

    public MulticastManager(Peer peer) {
        this.peer = peer;
        executor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(100);

        Runnable readMC = () -> {
            byte[] buf = new byte[BACKUP_BUFFER_SIZE];
            DatagramPacket packet = new DatagramPacket(buf, buf.length, this.peer.getMCGroup(), this.peer.getMCPort());

            while (true) {
                try {
                    this.peer.getMCSocket().receive(packet);
                    String received = new String(packet.getData(), 0, packet.getLength());
                    executor.execute(new MCParser(this.peer, received));
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
                    executor.execute(new MDBParser(this.peer, packet));
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
                    executor.execute(new MDRParser(this.peer, packet));
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