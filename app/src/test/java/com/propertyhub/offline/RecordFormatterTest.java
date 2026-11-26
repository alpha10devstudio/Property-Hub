package com.propertyhub.offline;

import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyRecord;
import com.propertyhub.offline.storage.RecordFormatter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecordFormatterTest {

    @Test
    public void formatsRecordWithUniqueRecordIdAndVerbatimDetails() {
        PropertyRecord record = new PropertyRecord(
                "HOUSE_000001",
                "2026-11-26",
                "09:15 AM",
                PropertyCategory.HOUSE_FOR_RENT,
                "+880 1712-345678",
                "Location: Mymensingh\nTwo Bedroom House\n\nRENT NEGOTIABLE",
                "2026-11-26.txt"
        );

        String output = RecordFormatter.appendRecordToContent("", record);
        String expected =
                "========================================\n"
                        + "PROPERTY RECORD\n"
                        + "RECORD_ID: HOUSE_000001\n"
                        + "DATE: 2026-11-26\n"
                        + "TIME: 09:15 AM\n"
                        + "CATEGORY: HOUSE_FOR_RENT\n"
                        + "MOBILE: +880 1712-345678\n"
                        + "DETAILS:\n"
                        + "Location: Mymensingh\n"
                        + "Two Bedroom House\n"
                        + "\n"
                        + "RENT NEGOTIABLE\n";

        assertEquals(expected, output);
    }

    @Test
    public void subsequentRecordAppendsWithoutOverwriting() {
        PropertyRecord first = new PropertyRecord(
                "LAND_000001",
                "2026-11-26",
                "10:00 AM",
                PropertyCategory.LAND_FOR_SALE,
                "+1 (555) 234-5678",
                "10 decimal residential plot.",
                "2026-11-26.txt"
        );
        PropertyRecord second = new PropertyRecord(
                "LAND_000002",
                "2026-11-26",
                "04:45 PM",
                PropertyCategory.LAND_FOR_SALE,
                "01887654321",
                "Main road frontage\n30 ft road",
                "2026-11-26.txt"
        );

        String content = RecordFormatter.appendRecordToContent("", first);
        content = RecordFormatter.appendRecordToContent(content, second);

        assertTrue(content.contains("RECORD_ID: LAND_000001"));
        assertTrue(content.contains("RECORD_ID: LAND_000002"));
        assertTrue(content.contains("CATEGORY: LAND_FOR_SALE"));
        assertTrue(content.contains("+1 (555) 234-5678"));
        assertTrue(content.contains("01887654321"));
    }
}
