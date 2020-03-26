import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;

public class Peer implements RemoteInterface {
    int id;

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

            System.err.println("Server ready");
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }

    public void backup(String fileName, int replicationDegree) throws IOException, NoSuchAlgorithmException {
        String version = "000";
        int senderID = 1;
        int fileID = 1; // sha256
        int chunkNumber = 1;
        String CRLF = "0xD0xA";
        File file = new File(fileName);
        byte[] body = Files.readAllBytes(file.toPath());
        
        FileMetadata filedata = new FileMetadata(file,replicationDegree);
        filedata.makeChunks();
        // <Version> PUTCHUNK <SenderID> <FileID> <ChunkNo> <ReplicationDeg> <CRLF> <CRLF> <Body>
        String message = version + " PUTCHUNK " + senderID + " " + fileID + " " + chunkNumber + " " + replicationDegree + " " + CRLF + CRLF + " " + body;

        System.out.println(message);
    }
}
