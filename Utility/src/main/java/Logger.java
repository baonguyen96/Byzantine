import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss.SSS z");
    private LogLevel level;

    public enum LogLevel {Debug, Release}

    public Logger() {
        this(LogLevel.Release);
    }

    public Logger(LogLevel level) {
        this.level = level;
    }

    public void debug(String message) {
        if (level == LogLevel.Debug) {
            log(message);
        }
    }

    public void log(String message) {
        Date now = new Date(System.currentTimeMillis());
        System.out.println(String.format("> %s at time: %s", message, dateTimeFormat.format(now)));
    }
}
