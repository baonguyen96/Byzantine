import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FileUtil {
    public static void truncateAllFilesInDirectory(String directoryPath) throws FileNotFoundException {
        File directory = new File(directoryPath);
        String[] files = directory.list();

        if (files != null) {
            for (String file : files) {
                truncateFile(Paths.get(directoryPath, file).toString());
            }
        }
    }

    public static void truncateFile(String fileName) throws FileNotFoundException {
        PrintWriter writer = new PrintWriter(fileName);
        writer.print("");
        writer.close();
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
        StringBuilder contentBuilder = new StringBuilder();

        try (Stream<String> stream = Files.lines(Paths.get(fileName), StandardCharsets.UTF_8)) {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        }
        catch (IOException ignored) {
        }

        return contentBuilder.toString();
    }
}
