package jake2.qcommon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestINFO {

    @Test
    public void testValueForKeyReturnsValue() {
        String info = "\\name\\player\\skin\\male/grunt";

        assertEquals("player", Info.Info_ValueForKey(info, "name"));
        assertEquals("male/grunt", Info.Info_ValueForKey(info, "skin"));
        assertEquals("", Info.Info_ValueForKey(info, "missing"));
    }

    @Test
    public void testRemoveKeyRemovesOnlyRequestedEntry() {
        String info = "\\key1\\value1\\key2\\value2\\key3\\value3";

        assertEquals("\\key2\\value2\\key3\\value3", Info.Info_RemoveKey(info, "key1"));
        assertEquals("\\key1\\value1\\key3\\value3", Info.Info_RemoveKey(info, "key2"));
        assertEquals(info, Info.Info_RemoveKey(info, "missing"));
    }

    @Test
    public void testSetValueForKeyAppendsAndReplaces() {
        String info = "\\name\\player";

        assertEquals("\\name\\player\\skin\\male/grunt", Info.Info_SetValueForKey(info, "skin", "male/grunt"));
        assertEquals("\\name\\player\\skin\\female/athena", Info.Info_SetValueForKey("\\skin\\male/grunt\\name\\player", "skin", "female/athena"));
    }

    @Test
    public void testSetValueForKeyRejectsInvalidInputs() {
        String info = "\\name\\player";

        assertEquals(info, Info.Info_SetValueForKey(info, "bad;key", "value"));
        assertEquals(info, Info.Info_SetValueForKey(info, "bad\\key", "value"));
        assertEquals(info, Info.Info_SetValueForKey(info, "key", "bad\"value"));
        assertEquals(info, Info.Info_SetValueForKey(info, "key", ""));
    }

    @Test
    public void testValidateRejectsReservedCharacters() {
        assertTrue(Info.Info_Validate("\\name\\player"));
        assertFalse(Info.Info_Validate("\\name\\bad;value"));
        assertFalse(Info.Info_Validate("\\name\\bad\"value"));
    }
}
