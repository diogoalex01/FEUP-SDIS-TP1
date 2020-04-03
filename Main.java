import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        // java TestApp <peer_ap> <sub_protocol> <opnd_1> <opnd_2>
        if (args.length > 5 | args.length < 3) {
            System.out.println(args.length);
            System.out.println("Incorrect command. Try: java Main <peer_ap> <sub_protocol> <opnd_1> <opnd_2>");
        }

        try {
            TestClient testClient = new TestClient(args);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
