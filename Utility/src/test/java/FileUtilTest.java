import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilTest {
    @Disabled
    @Test
    void appendToFile() {
        try {
            String filePath = "./src/test/resources/FileUtil/Test1.txt";
            FileUtil.appendToFile(filePath, "New line from unit test");
        }
        catch (Exception e) {

        }
    }

    @Test
    void testExistsExpectTrue() {
        String filePath = "./src/test/resources/FileUtil/Test.txt";
        assertTrue(FileUtil.exists(filePath));
    }

    @Test
    void testExistsExpectFalse() {
        String filePath = "./src/test/resources/FileUtil/Test2.txt";
        assertFalse(FileUtil.exists(filePath));
    }

    @Test
    void readFromFile() {
        String filePath = "./src/test/resources/FileUtil/Test.txt";
        String content = FileUtil.readFromFile(filePath);
        assertEquals("Test file", content);
    }
}