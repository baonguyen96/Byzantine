import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ServerNode {
    private final int TIME_DIFFERENCE_BETWEEN_PROCESSES = 1;
    private Logger logger = new Logger(Logger.LogLevel.Release);
    private int localTime;
    private ServerInfo info;
    private String directoryPath;
    private PriorityQueue<Message> commandsQueue;
    private Hashtable<String, Socket> serverSockets;
    private ArrayList<ServerInfo> otherServers;
    private HashSet<String> processedMessagesToAppendToFile;

    public ServerNode(ServerInfo serverInfo, ArrayList<ServerInfo> otherServerInfos, String directoryPath) {
        this.localTime = 0;
        this.info = serverInfo;
        this.directoryPath = directoryPath;
        this.otherServers = otherServerInfos;
        this.serverSockets = new Hashtable<>();
        this.commandsQueue = new PriorityQueue<>();
        this.processedMessagesToAppendToFile = new HashSet<>();

        logger.debug(String.format("Ensure directory '%s' exists (absolute path = '%s')", directoryPath, new File(directoryPath).getAbsolutePath()));
        FileUtil.createDirectory(directoryPath);
    }

    public void up() throws IOException {
        logger.log(String.format("%s starts listening on (%s:%d)...", this.info.getName(), this.info.getIpAddress(), this.info.getPort()));

        ServerSocket serverSocket = new ServerSocket(this.info.getPort(), 100, InetAddress.getByName(this.info.getIpAddress()));

        Thread listenThread = new Thread(() -> {
            try {
                listenForIncomingMessages(serverSocket);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        });
        listenThread.start();

        Thread linkToOtherServersThread = new Thread(() -> {
            try {
                populateServerSockets();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        linkToOtherServersThread.start();
    }

    private void populateServerSockets() throws InterruptedException {
        if (this.otherServers.isEmpty()) {
            logger.debug("No servers found to connect to");
            return;
        }

        for (int trial = 0; trial < 5; trial++) {
            for (ServerInfo otherServer : this.otherServers) {
                if (serverSockets.containsKey(otherServer.getName())) {
                    continue;
                }

                logger.debug(String.format("%s tries to connect to %s...", this.info.getName(), otherServer));

                try {
                    Socket socket = new Socket(otherServer.getIpAddress(), otherServer.getPort());
                    sendMessage(socket, String.format("Server %s", this.info.getName()), otherServer.getName());
                    serverSockets.put(otherServer.getName(), socket);

                    logger.debug(String.format("%s successfully connects to %s", this.info.getName(), otherServer));
                }
                catch (IOException ignored) {
                    logger.debug(String.format("%s fails to connect to %s - attempt %d", this.info.getName(), otherServer, trial + 1));
                }
            }

            if (serverSockets.keySet().size() == otherServers.size()) {
                break;
            }
            else {
                Thread.sleep(500);
            }
        }

        if (serverSockets.size() == 0) {
            logger.debug(String.format("%s cannot connect to any other servers", this.info.getName()));
        }
        else if (serverSockets.size() < otherServers.size()) {
            String successfulServers = String.join(", ", serverSockets.keySet());
            logger.debug(String.format("%s successfully connects to %s server(s): (%s)",
                    this.info.getName(), serverSockets.size(), successfulServers));
        }
        else {
            logger.debug(String.format("%s connect to all server(s)", this.info.getName()));
        }
    }

    private void listenForIncomingMessages(ServerSocket serverSocket) throws IOException {
        Socket incomingSocket;

        //noinspection InfiniteLoopStatement
        while (true) {
            incomingSocket = serverSocket.accept();
            Socket finalSocket = incomingSocket;

            logger.debug(String.format("%s receives new request from %s", this.info.getName(), incomingSocket));

            if (isServerSocket(finalSocket)) {
                Thread thread = new Thread(() -> {
                    try {
                        handleServerServerCommunication(finalSocket);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                thread.start();
            }
            else {
                Thread thread = new Thread(() -> {
                    try {
                        handleClientServerCommunication(finalSocket);
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                thread.start();
            }
        }
    }

    private void handleServerServerCommunication(Socket socket) throws IOException {
        boolean communicationOn = true;
        DataInputStream dis = new DataInputStream(socket.getInputStream());

        while (communicationOn) {
            try {
                String receivedMessageString = dis.readUTF();
                Message receivedMessage = new Message(receivedMessageString);

                logger.log(String.format("%s receives '%s' from %s", this.info.getName(), receivedMessageString, receivedMessage.getSenderName()));

                setLocalTime(receivedMessage.getTimeStamp());
                incrementLocalTime();

                if (receivedMessage.getType() == Message.MessageType.WriteAcquireRequest) {
                    addToQueue(receivedMessage);

                    Message responseMessage = new Message(this.info.getName(), Message.MessageType.WriteAcquireResponse, localTime, receivedMessage.getPayload());
                    Socket serverSocket = serverSockets.get(receivedMessage.getSenderName());
                    sendMessage(serverSocket, responseMessage.toString(), receivedMessage.getSenderName());
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteAcquireResponse) {
                    addToQueue(receivedMessage);
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteReleaseRequest) {
                    // only remove the WriteAcquireRequest counterpart
                    removeFromQueue(m ->
                            m.getSenderName().equals(receivedMessage.getSenderName()) &&
                                    m.getType() == Message.MessageType.WriteAcquireRequest &&
                                    m.getTimeStamp() < receivedMessage.getTimeStamp());
                }
                else if (receivedMessage.getType() == Message.MessageType.WriteSyncRequest) {
                    // append to file directly since this message type can only occur when 1 and only 1 server process in critical session
                    String fileName = receivedMessage.getFileNameFromPayload();
                    String lineToAppend = receivedMessage.getDataFromPayload();
                    appendToFile(fileName, lineToAppend);
                }
            }
            catch (Exception e) {
                communicationOn = false;
                e.printStackTrace();
            }
        }

        dis.close();
    }

    private void handleClientServerCommunication(Socket socket) throws IOException {
        boolean communicationOn = true;
        DataInputStream dis = new DataInputStream(socket.getInputStream());

        while (communicationOn) {
            try {
                String receivedMessageString = dis.readUTF();
                Message receivedMessage = new Message(receivedMessageString);
                String fileName = receivedMessage.getFileNameFromPayload();
                Message responseMessage = null;

                logger.log(String.format("%s receives '%s' from %s", this.info.getName(), receivedMessageString, receivedMessage.getSenderName()));
                setLocalTime(receivedMessage.getTimeStamp());
                incrementLocalTime();

                if(receivedMessage.getType().equals(Message.MessageType.ClientWriteRequest)) {
                    Message writeAcquireRequest = new Message(this.info.getName(), Message.MessageType.WriteAcquireRequest, localTime, receivedMessage.getPayload());

                    addToQueue(writeAcquireRequest);
                    notifyAllServers(writeAcquireRequest);
                    processCriticalSession(writeAcquireRequest);
                    incrementLocalTime();

                    responseMessage = new Message(this.info.getName(), Message.MessageType.WriteSuccessAck, localTime, "");
                }
                else {
                    Path fullPath = Paths.get(directoryPath, fileName).toAbsolutePath();

                    if (FileUtil.exists(String.valueOf(fullPath))) {
                        String content = FileUtil.readFromFile(fullPath.toString());
                        responseMessage = new Message(this.info.getName(), Message.MessageType.ReadSuccessAck, localTime, content);
                    }
                    else {
                        responseMessage = new Message(this.info.getName(), Message.MessageType.ReadFailureAck, localTime, String.format("File '%s' does not exist", fileName));
                    }
                }

                sendMessage(socket, responseMessage.toString(), receivedMessage.getSenderName());
            }
            catch (Exception e) {
                communicationOn = false;
            }
        }

        dis.close();
    }

    private boolean isServerSocket(Socket socket) throws IOException {
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        String socketType = dis.readUTF();
        return socketType.toLowerCase().startsWith("server");
    }

    private void sendMessage(Socket socket, String messageText, String recipientName) throws IOException {
        logger.log(String.format("%s sends '%s' to %s", this.info.getName(), messageText, recipientName));

        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.writeUTF(messageText);
    }

    private synchronized void incrementLocalTime() {
        localTime += TIME_DIFFERENCE_BETWEEN_PROCESSES;
    }

    private synchronized void setLocalTime(int messageTimeStamp) {
        localTime = Math.max(localTime, messageTimeStamp + TIME_DIFFERENCE_BETWEEN_PROCESSES);
    }

    private synchronized void addToQueue(Message message) {
        logger.debug(String.format("Adding message '%s' to the queue", message.toString()));
        logger.debug("Queue size before add = " + commandsQueue.size());

        commandsQueue.add(message);

        logger.debug("Queue size after add = " + commandsQueue.size());
    }

    private synchronized void removeFromQueue(Predicate<Message> filter) {
        logger.debug("Removing messages off the queue");
        logger.debug("Queue size before remove = " + commandsQueue.size());

        List<Message> removingMessages = commandsQueue.stream().filter(filter).collect(Collectors.toList());
        for(Message message : removingMessages) {
            logger.debug(String.format("Removing '%s' from the queue", message.toString()));
        }

        commandsQueue.removeAll(removingMessages);

        logger.debug("Queue size after remove = " + commandsQueue.size());
    }

    private boolean isMessageFirstInQueue(Message message) {
        if (commandsQueue.isEmpty()) {
            return true;
        }

        Message top = commandsQueue.peek();

        logger.debug("Top of queue = " + top.toString());
        logger.debug("Current message = " + message.toString());

        return top.getSenderName().equals(message.getSenderName()) &&
                top.getTimeStamp() == message.getTimeStamp();
    }

    private boolean isAllConfirmToAllowEnterCriticalSession(Message writeAcquireRequest) {
        String[] allSendersAfterWriteRequest = commandsQueue
                .stream()
                .filter(message -> message.getTimeStamp() > writeAcquireRequest.getTimeStamp())
                .map(Message::getSenderName)
                .distinct()
                .toArray(String[]::new);

        logger.debug("All senders after request = " + String.join(", ", allSendersAfterWriteRequest));

        return allSendersAfterWriteRequest.length >= serverSockets.size();
    }

    private void processCriticalSession(Message writeAcquireRequest) throws InterruptedException, IOException {
        logger.debug(String.format("Checking allowance to proceed to critical session for message '%s'...", writeAcquireRequest.toString()));

        while (!isMessageFirstInQueue(writeAcquireRequest) || !isAllConfirmToAllowEnterCriticalSession(writeAcquireRequest)) {
            logger.debug("Waiting for critical session access...");
            Thread.sleep(100);
        }

        logger.debug("Going into critical session...");

        String fileName = writeAcquireRequest.getFileNameFromPayload();
        String lineToAppend = writeAcquireRequest.getDataFromPayload();
        appendToFile(fileName, lineToAppend);
        incrementLocalTime();

        Message writeSyncRequest = new Message(this.info.getName(), Message.MessageType.WriteSyncRequest, localTime, writeAcquireRequest.getPayload());
        notifyAllServers(writeSyncRequest);

        // since current writeSyncRequest must be the highest timestamped message in the queue for the current payload,
        // therefore can remove any message for this payload with lesser timestamp
        removeFromQueue(m -> m.compareTo(writeSyncRequest) < 0 && m.getPayload().equals(writeSyncRequest.getPayload()));
        incrementLocalTime();

        Message writeReleaseRequest = new Message(this.info.getName(), Message.MessageType.WriteReleaseRequest, localTime, "");
        notifyAllServers(writeReleaseRequest);
        incrementLocalTime();

        logger.debug("Going out of critical session access");
    }

    private void notifyAllServers(Message message) throws IOException {
        for(String serverName : serverSockets.keySet()) {
            Socket serverSocket = serverSockets.get(serverName);
            sendMessage(serverSocket, message.toString(), serverName);
        }
    }

    private synchronized void appendToFile(String fileName, String message) throws IOException {
        String combo = String.format("%s|%s", fileName, message);

        if(processedMessagesToAppendToFile.contains(combo)) {
            logger.debug(String.format("%s already appended '%s' to file '%s'. Skipping...", this.info.getName(), message, fileName));
        }
        else {
            logger.log(String.format("%s appends '%s' to file '%s'", this.info.getName(), message, fileName));

            Path filePath = Paths.get(directoryPath, fileName).toAbsolutePath();
            FileUtil.appendToFile(String.valueOf(filePath), message);
            processedMessagesToAppendToFile.add(combo);
        }
    }
}
