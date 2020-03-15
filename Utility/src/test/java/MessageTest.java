import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageTest {

    private static Message messageFromObject;
    private static Message messageFromString;

    @BeforeAll
    static void setUp() {
        messageFromObject = new Message("client0", Message.MessageType.WriteAcquireRequest, 0, "File0.txt");
        messageFromString = new Message("client1|ClientWriteRequest|1|File1.txt|Something");
    }

    @Test
    void testGetSenderNameObjectObject() {
        assertEquals("client0", messageFromObject.getSenderName());
    }

    @Test
    void testGetTypeObject() {
        assertEquals(Message.MessageType.WriteAcquireRequest, messageFromObject.getType());
    }

    @Test
    void testGetTimeStampObject() {
        assertEquals(0, messageFromObject.getTimeStamp());
    }

    @Test
    void testGetPayloadObject() {
        assertEquals("File0.txt", messageFromObject.getPayload());
    }

    @Test
    void testGetFileNameFromPayloadObject() {
        assertEquals("File0.txt", messageFromObject.getFileNameFromPayload());
    }

    @Test
    void testGetDataFromPayloadObject() {
        assertEquals("", messageFromObject.getDataFromPayload());
    }

    @Test
    void testGetSenderNameString() {
        assertEquals("client1", messageFromString.getSenderName());
    }

    @Test
    void testGetTypeString() {
        assertEquals(Message.MessageType.ClientWriteRequest, messageFromString.getType());
    }

    @Test
    void testGetTimeStampString() {
        assertEquals(1, messageFromString.getTimeStamp());
    }

    @Test
    void testGetPayloadString() {
        assertEquals("File1.txt|Something", messageFromString.getPayload());
    }

    @Test
    void testGetFileNameFromPayloadString() {
        assertEquals("File1.txt", messageFromString.getFileNameFromPayload());
    }

    @Test
    void testGetDataFromPayloadString() {
        assertEquals("Something", messageFromString.getDataFromPayload());
    }

    @Test
    void testCompareTo() {
        assertEquals(-1, messageFromObject.compareTo(messageFromString));
    }

    @Test
    void testToString() {
        assertEquals("client0|WriteAcquireRequest|0|File0.txt", messageFromObject.toString());
    }

}