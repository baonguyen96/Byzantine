import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Random;

public class ClientNode {
    private final int TIME_DIFFERENCE_BETWEEN_PROCESSES = 1;
    private int localTime;
    private String name;
    private Hashtable<String, Socket> serverSockets;
    private Logger logger = new Logger(Logger.LogLevel.Release);

    public ClientNode(String name, ArrayList<ServerInfo> servers) throws IOException {
        this.name = name;
        localTime = 0;
        serverSockets = new Hashtable<>();
        populateServerSockets(servers);
    }

    private void populateServerSockets(ArrayList<ServerInfo> servers) throws IOException {
        for (ServerInfo server : servers) {
            Socket socket = new Socket(server.getIpAddress(), server.getPort());
            serverSockets.put(server.getName(), socket);
            sendMessage(socket, String.format("Client '%s'", this.name));
        }
    }

    public void up() throws IOException, InterruptedException {
        logger.log(String.format("'%s' starts", this.name));

        Random random = new Random();
        String message;
        int fileNumber;
        int serverNumber;
        String serverName;

        for(int i = 0; i < 100; i++) {
            serverNumber = random.nextInt(serverSockets.size());
            serverName = (String) serverSockets.keySet().toArray()[serverNumber];
            fileNumber = random.nextInt(4) + 1;
            message = String.format("File%d.txt|%s message #%d -- %s", fileNumber, this.name, i, serverName);

            requestWrite(serverName, message);
            Thread.sleep(random.nextInt(1000));
        }

        logger.log(String.format("'%s' gracefully exits", this.name));
    }

    private void requestWrite(String serverName, String messagePayload) throws IOException {
        incrementLocalTime();

        Socket socket = serverSockets.get(serverName);
        Message message = new Message(this.name, Message.MessageType.ClientWriteRequest, localTime, messagePayload);

        logger.log(String.format("%s sends '%s' to %s", this.name, message, serverName));

        sendMessage(socket, message.toString());

        DataInputStream dis = new DataInputStream(socket.getInputStream());
        String responseMessageText = dis.readUTF();
        Message responseMessage = new Message(responseMessageText);

        logger.log(String.format("%s receives '%s' from %s", this.name, responseMessageText, serverName));

        setLocalTime(responseMessage.getTimeStamp());
        incrementLocalTime();
    }

    private void sendMessage(Socket socket, String message) throws IOException {
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.writeUTF(message);
    }

    private synchronized void incrementLocalTime() {
        localTime += TIME_DIFFERENCE_BETWEEN_PROCESSES;
    }

    private synchronized void setLocalTime(int messageTimeStamp) {
        localTime = Math.max(localTime, messageTimeStamp + TIME_DIFFERENCE_BETWEEN_PROCESSES);
    }
}
