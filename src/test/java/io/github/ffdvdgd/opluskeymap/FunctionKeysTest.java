package io.github.ffdvdgd.opluskeymap;

import org.junit.Test;

import static org.junit.Assert.*;

public class FunctionKeysTest {
    @Test
    public void allTwelveFunctionKeysHaveDistinctNativeLabelsAndCodes() {
        int[] codes = {131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142};
        String[] labels = {"F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"};
        for (int i = 0; i < codes.length; i++) {
            assertTrue(FunctionKeys.contains(codes[i]));
            assertEquals(labels[i], FunctionKeys.label(codes[i]));
            assertEquals(Integer.valueOf(codes[i]), FunctionKeys.code(labels[i]));
        }
    }

    @Test
    public void otherKeysAndNonCanonicalLabelsRemainNative() {
        for (int code : new int[] {0, 29, 130, 143, 144}) {
            assertFalse(FunctionKeys.contains(code));
            assertNull(FunctionKeys.label(code));
        }
        for (String label : new String[] {null, "", "F", "F0", "F13", "F01", "f1", "F1 ", "A"}) {
            assertNull(FunctionKeys.code(label));
        }
    }
}
