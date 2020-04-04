import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.NoSuchAlgorithmException;

public class TestClient {
    private String peerAccessAPoint;

    public TestClient(String[] args) throws FileNotFoundException, IOException {
        this.peerAccessAPoint = args[0];

        try {
            Registry registry = LocateRegistry.getRegistry(1099);
            RemoteInterface remote = (RemoteInterface) registry.lookup(peerAccessAPoint);
            parseArgs(args, remote);
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }

    private void parseArgs(String[] args, RemoteInterface remote)
            throws FileNotFoundException, IOException, NoSuchAlgorithmException {
        String command = args[1], fileName = args[2];
        int replicationDegree = Integer.parseInt(args[3]);
        System.out.println("Replication degree is " + replicationDegree);

        switch (command) {
            case "BACKUP": {
                remote.backup(fileName, replicationDegree);
                break;
            }
            case "RESTORE": {
                restore(fileName);
                break;
            }
            case "DELETE": {
                delete(fileName);
                break;
            }
            case "RECLAIM": {
                reclaim(Integer.parseInt(fileName)); // check
                break;
            }
            case "STATE": {
                break;
            }
        }
    }

    private void backup(String fileName, int replicationDegree) throws FileNotFoundException, IOException {
        String protocolVersion = "1.0";
        int senderID = 1;
        int fileID = 1; // sha256
        int chunkNumber = 1;
        String CRLF = "0xD0xA";
        System.out.println("exception");

        File file = new File(fileName);
        // Init array with file length
        byte[] body = new byte[(int) file.length()];

        FileInputStream fileInputStream = new FileInputStream(file);
        fileInputStream.read(body); // read file into bytes[]
        fileInputStream.close();

        // <Version> PUTCHUNK <SenderID> <FileID> <ChunkNo> <ReplicationDeg> <CRLF>
        // <CRLF> <Body>
        String message = protocolVersion + " PUTCHUNK " + senderID + " " + fileID + " " + chunkNumber + " "
                + replicationDegree + " " + CRLF + CRLF + " " + body;
    }

    private void restore(String fileName) {
        String protocolVersion = "1.0";
        int senderID = 1;
        int fileID = 1; // usar sha256
        int chunkNo = 1;
        String CRLF = "0xD0xA";

        String message = protocolVersion + " GETCHUNK " + senderID + " " + fileID + " " + chunkNo + " " + CRLF + CRLF;
        // <Version> GETCHUNK <SenderID> <FileID> <ChunkNo> <CRLF><CRLF>
    }

    private void delete(String fileName) {
        String protocolVersion = "1.0";
        int senderID = 1;
        int fileID = 1; // usar sha256
        String CRLF = "0xD0xA";

        String message = protocolVersion + " DELETE " + senderID + " " + fileID + " " + CRLF + CRLF;
        // <Version> DELETE <SenderID> <FileID> <CRLF><CRLF>
    }

    private void reclaim(int space) {
        String protocolVersion = "1.0";
        int senderID = 1;
        int fileID = 1; // usar sha256
        int chunkNo = 1;
        String CRLF = "0xD0xA";

        String message = protocolVersion + " REMOVED " + senderID + " " + fileID + " " + chunkNo + " " + CRLF + CRLF;
        // <Version> REMOVED <SenderID> <FileID> <ChunkNo> <CRLF><CRLF>
    }
}
