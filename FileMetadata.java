import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class FileMetadata {

    private String ID;
    private File file;
    private ArrayList<Chunk> chunks;
    private int replicationDegree;
    private static final int MAX_CHUNK_SIZE = 64000;

    public FileMetadata(File file, int replicationDegree) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        String hashInput = file.getName()+ file.lastModified() + Files.getOwner(file.toPath());
        this.ID = toHexString(md.digest(hashInput.getBytes(StandardCharsets.UTF_8))); 
        this.file = file;
        this.replicationDegree = replicationDegree;
        this.chunks = new ArrayList<>();
        System.out.println("string is " + hashInput);
        System.out.println("SHA is " + this.ID);
    }

    public static String toHexString(byte[] hash) 
    { 
        // Convert byte array into signum representation  
        BigInteger number = new BigInteger(1, hash);  
  
        // Convert message digest into hex value  
        StringBuilder hexString = new StringBuilder(number.toString(16));  
  
        // Pad with leading zeros 
        while (hexString.length() < 32)  
        {  
            hexString.insert(0, '0');  
        }  
  
        return hexString.toString();  
    }  

    public void makeChunks() throws IOException {
        byte[] body = Files.readAllBytes(this.file.toPath());
        int chunkNumber = body.length / MAX_CHUNK_SIZE;
        int remainder = body.length % MAX_CHUNK_SIZE;
        int chunkCounter = 0;
        for (; chunkCounter < chunkNumber; chunkCounter++) {
            Chunk chunk = new Chunk(chunkCounter, this.ID, MAX_CHUNK_SIZE);
            chunks.add(chunk);
        }
        Chunk chunk = new Chunk(chunkCounter, this.ID, remainder);
        chunks.add(chunk);
    }
}