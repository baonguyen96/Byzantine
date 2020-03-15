import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FileUtil {
    public static boolean createDirectory(String path) {
        File directory = new File(path);
        boolean hasDirectory;

        if(directory.exists()) {
            hasDirectory = true;
        }
        else {
            hasDirectory = directory.mkdir();
        }

        return hasDirectory;
    }

    public static void appendToFile(String fileName, String line) throws IOException {
        FileWriter fileWriter = new FileWriter(fileName, true);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.println(line);
        printWriter.close();
    }

    public static boolean exists(String fileName) {
        File file = new File(fileName);
        return file.exists();
    }

    public static String readFromFile(String fileName) {
        String content = "";

        try (Stream<String> stream = Files.lines(Paths.get(fileName), StandardCharsets.UTF_8)) {
            content = String.join("{newLine}", stream.toArray(String[]::new));
        }
        catch (IOException ignored) {
        }

        return content;
    }
}
