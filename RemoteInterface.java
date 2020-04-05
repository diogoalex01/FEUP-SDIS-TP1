import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;

public interface RemoteInterface extends Remote {
    void backup(String filepath, int replicationDegree)
            throws IOException, FileNotFoundException, NoSuchAlgorithmException, RemoteException;

    void delete(String filepath) throws IOException, NoSuchAlgorithmException;
}
