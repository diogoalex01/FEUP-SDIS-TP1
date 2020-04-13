import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.NoSuchAlgorithmException;

public class TestClient {
    private String peerAccessAPoint;

    public TestClient(String[] args) throws IOException {
        this.peerAccessAPoint = args[0];

        try {
            Registry registry = LocateRegistry.getRegistry(1099);
            RemoteInterface remote = (RemoteInterface) registry.lookup(peerAccessAPoint);
            parseArgs(args, remote);
        } catch (Exception e) {
            System.err.println("Test Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private void parseArgs(String[] args, RemoteInterface remote) throws IOException, NoSuchAlgorithmException {
        String peerAP = args[0];
        String subProtocol = args[1];
        String opnd1 = "", opnd2 = "";
        int replicationDegree;

        if (args.length > 2) {
            opnd1 = args[2];
            if (args.length > 3) {
                opnd2 = args[3];
            }
        }

        switch (subProtocol) {
            case "BACKUP": {
                remote.backup(opnd1, Integer.parseInt(args[3]));
                break;
            }
            case "RESTORE": {
                remote.restore(opnd1);
                break;
            }
            case "DELETE": {
                remote.delete(opnd1);
                break;
            }
            case "RECLAIM": {
                remote.reclaim(Integer.parseInt(opnd1));
                break;
            }
            case "STATE": {
                String state;
                state = remote.state();
                System.out.println(state);
                break;
            }
        }
    }
}
