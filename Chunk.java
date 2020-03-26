import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Chunk {

    private int ID;
    private String fileID;
    private byte[] data;
    private int size;
    public Chunk(int ID,String fileID, int size) {  
        this.ID = ID;
        this.fileID = fileID;
        this.size = size;
    }

    public void setData(byte[] data){
        this.data = data;
    }
}
        