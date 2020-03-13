import java.util.StringTokenizer;

public class ServerInfo {
    private String name;
    private String ipAddress;
    private int port;

    public ServerInfo(String serverInfoString) {
        StringTokenizer tokenizer = new StringTokenizer(serverInfoString, ":");

        this.name = tokenizer.nextToken();
        this.ipAddress = tokenizer.nextToken();
        this.port = Integer.parseInt(tokenizer.nextToken());
    }

    public String getName() {
        return name;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return String.format("%s (%s:%d)", this.name, this.ipAddress, this.port);
    }
}
