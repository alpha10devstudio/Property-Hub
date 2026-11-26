package com.propertyhub.offline;

import com.propertyhub.offline.storage.StorageManager;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PosterIntegrityTest {

    @Test
    public void rejectsEmptyTruncatedAndNonJpegPayloads() {
        assertFalse(StorageManager.hasValidJpegMarkers(null));
        assertFalse(StorageManager.hasValidJpegMarkers(new byte[0]));
        assertFalse(StorageManager.hasValidJpegMarkers("not a jpeg file".getBytes()));

        // Valid SOI (FF D8) but missing EOI (FF D9) -> truncated / corrupted
        byte[] truncated = new byte[32];
        truncated[0] = (byte) 0xFF;
        truncated[1] = (byte) 0xD8;
        truncated[30] = (byte) 0x12;
        truncated[31] = (byte) 0x34;
        assertFalse(StorageManager.hasValidJpegMarkers(truncated));

        // Valid SOI + EOI markers
        byte[] validJpeg = new byte[32];
        validJpeg[0] = (byte) 0xFF;
        validJpeg[1] = (byte) 0xD8;
        validJpeg[30] = (byte) 0xFF;
        validJpeg[31] = (byte) 0xD9;
        assertTrue(StorageManager.hasValidJpegMarkers(validJpeg));
    }
}
