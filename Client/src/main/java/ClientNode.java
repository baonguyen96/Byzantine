import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

public class ClientNode {
    @SuppressWarnings("FieldCanBeLocal")
    private final boolean IS_DEBUGGING = false;
    private Logger logger = new Logger(Logger.LogLevel.Debug);
    private final int TIME_DIFFERENCE_BETWEEN_PROCESSES = 1;
    private int localTime;
    private String name;
    private LinkedHashMap<String, Socket> serverSockets;

    public ClientNode(String name, ArrayList<ServerInfo> servers) throws InterruptedException {
        this.name = name;
        localTime = 0;
        serverSockets = new LinkedHashMap<>();
        populateServerSockets(servers);
    }

    private void populateServerSockets(ArrayList<ServerInfo> servers) throws InterruptedException {
        List<String> connectedServers = new ArrayList<>();

        for (int trial = 0; trial < 2; trial++) {
            for (ServerInfo server : servers) {
                if (connectedServers.contains(server.getName())) {
                    continue;
                }

                logger.debug(String.format("%s tries to connect to %s...", name, server));

                try {
                    Socket socket = new Socket(server.getIpAddress(), server.getPort());
                    serverSockets.put(server.getName(), socket);
                    sendMessage(socket, String.format("Client '%s'", this.name));

                    connectedServers.add(server.getName());
                    logger.debug(String.format("%s successfully connects to %s", name, server));
                }
                catch (IOException ignored) {
                    serverSockets.put(server.getName(), null);
                    logger.debug(String.format("%s fails to connect to %s - attempt %d", name, server, trial + 1));
                }
            }

            if (connectedServers.size() == servers.size()) {
                break;
            }
            else {
                Thread.sleep(100);
            }
        }

        if (connectedServers.size() == 0) {
            logger.debug(String.format("%s cannot connect to any other servers", name));
        }
        else if (connectedServers.size() < servers.size()) {
            logger.debug(String.format("%s successfully connects to %s server(s): (%s)",
                    name, connectedServers.size(), String.join(", ", connectedServers)));
        }
        else {
            logger.debug(String.format("%s connect to all server(s)", name));
        }
    }

    public void up() throws IOException, InterruptedException {
        logger.log(String.format("%s starts", this.name));

        Random random = new Random();

        for (int i = 0; i < 20; i++) {
            boolean needToWrite = random.nextBoolean();
            int fileNumber = random.nextInt(20);

            if (IS_DEBUGGING) {
                needToWrite = true;
                fileNumber = random.nextInt(10);
            }

            Thread.sleep(random.nextInt(500));

            if (needToWrite) {
                writeToServers(fileNumber, i);
            }
            else {
                readFromServers(fileNumber);
            }
        }

        logger.log(String.format("%s gracefully exits", this.name));
    }

    private void writeToServers(int fileNumber, int messageCount) throws IOException {
        List<Integer> serverNumbers = getServerNumbersForObject(fileNumber);
        String fileName = String.format("File%d.txt", fileNumber);
        List<Integer> reachableServerNumbers = new ArrayList<>();

        for (int serverNumber : serverNumbers) {
            if (isServerReachable(serverNumber)) {
                reachableServerNumbers.add(serverNumber);
            }
        }

        if (reachableServerNumbers.size() >= 2) {
            for (int serverNumber : reachableServerNumbers) {
                String serverName = (String) serverSockets.keySet().toArray()[serverNumber];
                String message = String.format("%s|%s message #%d", fileName, this.name, messageCount);
                requestServer(serverName, Message.MessageType.ClientWriteRequest, message);
            }
        }
        else {
            List<Integer> unreachableServerNumbers = serverNumbers
                    .stream()
                    .filter(i -> !reachableServerNumbers.contains(i))
                    .collect(Collectors.toList());
            List<String> unreachableServerNames = new ArrayList<>();

            for (int unreachableServerNumber : unreachableServerNumbers) {
                String serverName = (String) serverSockets.keySet().toArray()[unreachableServerNumber];
                unreachableServerNames.add(serverName);
            }

            String errorMessage = String.format("%s: Cannot write to '%s' because of too many (%d) unreachable servers (%s)",
                    name, fileName, unreachableServerNames.size(), String.join(", ", unreachableServerNames));
            logger.log(errorMessage);
        }
    }

    private void readFromServers(int fileNumber) throws IOException {
        boolean didRead = false;
        List<String> unreachableServerNames = new ArrayList<>();
        String fileName = String.format("File%d.txt", fileNumber);
        List<Integer> serverNumbers = getServerNumbersForObject(fileNumber);
        Collections.shuffle(serverNumbers);

        for(int serverNumber : serverNumbers) {
            String serverName = (String) serverSockets.keySet().toArray()[serverNumber];

            if (isServerReachable(serverNumber)) {
                Message.MessageType responseType = requestServer(serverName, Message.MessageType.ClientReadRequest, fileName);

                if (responseType.equals(Message.MessageType.ReadFailureAck)) {
                    logger.log(String.format("%s: %s cannot find file '%s'", name, serverName, fileName));
                }

                didRead = true;
                break;
            }
            else {
                logger.debug(String.format("%s: %s is unreachable to read file '%s'", name, serverName, fileName));
                unreachableServerNames.add(serverName);
            }
        }

        if(!didRead) {
            logger.log(String.format("%s cannot reach any server (%s) to  reach file '%s'",
                    name, String.join(", ", unreachableServerNames) , fileName));
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

            if (serverSocket != null) {
                isReachable = serverSocket.getInetAddress().isReachable(10000);
            }
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
