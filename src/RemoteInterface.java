import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;

public interface RemoteInterface extends Remote {
    void backup(String filepath, int replicationDegree) throws IOException, NoSuchAlgorithmException;

    void restore(String filepath) throws IOException, NoSuchAlgorithmException;

    void delete(String filepath) throws IOException, NoSuchAlgorithmException;

    void reclaim(int availableStorage) throws IOException, NoSuchAlgorithmException;

    String state() throws IOException;
}