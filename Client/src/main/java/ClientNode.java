import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

public class ClientNode {
    @SuppressWarnings("FieldCanBeLocal")
    private final boolean IS_DEBUGGING = true;
    private final int TIME_DIFFERENCE_BETWEEN_PROCESSES = 1;
    private int localTime;
    private String name;
    private Hashtable<String, Socket> serverSockets;
    private Logger logger = new Logger(Logger.LogLevel.Debug);

    public ClientNode(String name, ArrayList<ServerInfo> servers) throws InterruptedException {
        this.name = name;
        localTime = 0;
        serverSockets = new Hashtable<>();
        populateServerSockets(servers);
    }

    private void populateServerSockets(ArrayList<ServerInfo> servers) throws InterruptedException {
        for (int trial = 0; trial < 3; trial++) {
            for (ServerInfo server : servers) {
                if (serverSockets.containsKey(server.getName())) {
                    continue;
                }

                logger.debug(String.format("%s tries to connect to %s...", name, server));

                try {
                    Socket socket = new Socket(server.getIpAddress(), server.getPort());
                    sendMessage(socket, String.format("Client '%s'", this.name));
                    serverSockets.put(server.getName(), socket);

                    logger.debug(String.format("%s successfully connects to %s", name, server));
                }
                catch (IOException ignored) {
                    logger.debug(String.format("%s fails to connect to %s - attempt %d", name, server, trial + 1));
                }
            }

            if (serverSockets.keySet().size() == servers.size()) {
                break;
            }
            else {
                Thread.sleep(500);
            }
        }

        if (serverSockets.size() == 0) {
            logger.debug(String.format("%s cannot connect to any other servers", name));
        }
        else if (serverSockets.size() < servers.size()) {
            String successfulServers = String.join(", ", serverSockets.keySet());
            logger.debug(String.format("%s successfully connects to %s server(s): (%s)",
                    name, serverSockets.size(), successfulServers));
        }
        else {
            logger.debug(String.format("%s connect to all server(s)", name));
        }
    }

    public void up() throws IOException, InterruptedException {
        logger.log(String.format("%s starts", this.name));

        Random random = new Random();

        for(int i = 0; i < 1; i++) {
            boolean needToWrite = random.nextBoolean();
            int fileNumber = random.nextInt(1000);

            if(IS_DEBUGGING) {
                needToWrite = true;
                fileNumber = 0;
            }

            Thread.sleep(random.nextInt(100));

            if (needToWrite) {
                writeToServers(fileNumber);
            }
            else {
                readFromServers(fileNumber);
            }
        }

        logger.log(String.format("%s gracefully exits", this.name));
    }

    private void writeToServers(int fileNumber) throws IOException {
        List<Integer> serverNumbers = getServerNumbersForObject(fileNumber);
        List<Integer> reachableServerNumbers = new ArrayList<>();

        for (int serverNumber : serverNumbers) {
            if (isServerReachable(serverNumber)) {
                reachableServerNumbers.add(serverNumber);
            }
        }

        if (reachableServerNumbers.size() >= 2) {
            for(int serverNumber : reachableServerNumbers) {
                String serverName = (String) serverSockets.keySet().toArray()[serverNumber];
                String message = String.format("File%d.txt|%s message #%d -- %s", fileNumber, this.name, 0, serverName);
                requestServer(serverName, Message.MessageType.ClientWriteRequest, message);
            }
        }
        else {
            List<Integer> unreachableServerNumbers = serverNumbers.stream().filter(i -> !reachableServerNumbers.contains(i)).collect(Collectors.toList());
            String errorMessage = String.format("%s: Cannot write because of too many unreachable servers", name);

            try {
                List<String> unreachableServerNames = new ArrayList<>();

                for (int unreachableServerNumber : unreachableServerNumbers) {
                    String serverName = (String) serverSockets.keySet().toArray()[unreachableServerNumber];
                    unreachableServerNames.add(serverName);
                }

                errorMessage = String.format("%s: (%s)", errorMessage, String.join(", ", unreachableServerNames));
            }
            catch(ArrayIndexOutOfBoundsException ignored) {
            }

            logger.log(errorMessage);
        }
    }

    private void readFromServers(int fileNumber) throws IOException {
        Random random = new Random();
        List<Integer> serverNumbers = getServerNumbersForObject(fileNumber);
        int serverNumber =  serverNumbers.get(random.nextInt(serverNumbers.size()));
        String serverName = (String) serverSockets.keySet().toArray()[serverNumber];
        String message = String.format("File%d.txt", fileNumber);

        Message.MessageType responseType = requestServer(serverName, Message.MessageType.ClientReadRequest, message);

        if(responseType.equals(Message.MessageType.ReadFailureAck)) {
            logger.log(String.format("%s: %s cannot find file '%s'", name, serverName, message));
        }
    }

    private Message.MessageType requestServer(String serverName, Message.MessageType messageType, String messagePayload) throws IOException {
        incrementLocalTime();

        Socket socket = serverSockets.get(serverName);
        Message message = new Message(this.name, messageType, localTime, messagePayload);

        logger.log(String.format("%s sends '%s' to %s", this.name, message, serverName));

        sendMessage(socket, message.toString());

        DataInputStream dis = new DataInputStream(socket.getInputStream());
        String responseMessageText = dis.readUTF();
        Message responseMessage = new Message(responseMessageText);

        logger.log(String.format("%s receives '%s' from %s", this.name, responseMessageText, serverName));

        setLocalTime(responseMessage.getTimeStamp());
        incrementLocalTime();

        return responseMessage.getType();
    }

    private void sendMessage(Socket socket, String message) throws IOException {
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.writeUTF(message);
    }

    private List<Integer> getServerNumbersForObject(int objectNumber) {
        List<Integer> serverNumbers = new ArrayList<>();
        int hashForObject = objectNumber % 7;

        serverNumbers.add(hashForObject);
        serverNumbers.add((hashForObject + 1) % 7);
        serverNumbers.add((hashForObject + 2) % 7);

        return serverNumbers;
    }

    private boolean isServerReachable(int serverNumber) {
        boolean isReachable = false;

        try {
            String serverName = (String) serverSockets.keySet().toArray()[serverNumber];
            Socket serverSocket = serverSockets.get(serverName);
            isReachable = serverSocket.getInetAddress().isReachable(10000);
        }
        catch (Exception ignored) {
        }

        return isReachable;
    }

    private synchronized void incrementLocalTime() {
        localTime += TIME_DIFFERENCE_BETWEEN_PROCESSES;
    }

    private synchronized void setLocalTime(int messageTimeStamp) {
        localTime = Math.max(localTime, messageTimeStamp + TIME_DIFFERENCE_BETWEEN_PROCESSES);
    }
}
