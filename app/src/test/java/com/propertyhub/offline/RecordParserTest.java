package com.propertyhub.offline;

import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyRecord;
import com.propertyhub.offline.storage.RecordFormatter;
import com.propertyhub.offline.storage.RecordParser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecordParserTest {

    @Test
    public void parsesMultipleRecordsPreservingVerbatimMultilineDetails() {
        PropertyRecord r1 = new PropertyRecord(
                "HOUSE_000001",
                "2026-11-26",
                "09:15 AM",
                PropertyCategory.HOUSE_FOR_RENT,
                "01712345678",
                "Floor 3 Flat\n\n2 Bedrooms, 1 Balcony\nRent: 12000",
                "2026-11-26.txt"
        );
        PropertyRecord r2 = new PropertyRecord(
                "HOUSE_000002",
                "2026-11-26",
                "11:30 AM",
                PropertyCategory.HOUSE_FOR_RENT,
                "+44 7911 123456",
                "Studio Apartment",
                "2026-11-26.txt"
        );

        String combined = RecordFormatter.appendRecordToContent("", r1);
        combined = RecordFormatter.appendRecordToContent(combined, r2);

        RecordParser.ParseResult parsed = RecordParser.parseFileContent(
                combined,
                PropertyCategory.HOUSE_FOR_RENT,
                "2026-11-26.txt"
        );

        assertEquals(2, parsed.getRecords().size());
        assertEquals(0, parsed.getInvalidCount());
        assertTrue(parsed.getWarnings().isEmpty());

        PropertyRecord parsed1 = parsed.getRecords().get(0);
        assertEquals("HOUSE_000001", parsed1.getRecordId());
        assertEquals("2026-11-26", parsed1.getDate());
        assertEquals("09:15 AM", parsed1.getTime());
        assertEquals("01712345678", parsed1.getMobile());
        assertEquals("Floor 3 Flat\n\n2 Bedrooms, 1 Balcony\nRent: 12000", parsed1.getDetails());

        PropertyRecord parsed2 = parsed.getRecords().get(1);
        assertEquals("HOUSE_000002", parsed2.getRecordId());
        assertEquals("+44 7911 123456", parsed2.getMobile());
        assertEquals("Studio Apartment", parsed2.getDetails());
    }

    @Test
    public void infersCategoryFromRecordIdWhenFallbackCategoryIsNull() {
        String content =
                "========================================\n"
                        + "PROPERTY RECORD\n"
                        + "RECORD_ID: LAND_000042\n"
                        + "DATE: 2026-11-26\n"
                        + "TIME: 02:15 PM\n"
                        + "MOBILE: 01900000042\n"
                        + "DETAILS:\n"
                        + "15 decimal plot near highway\n";

        RecordParser.ParseResult parsed = RecordParser.parseFileContent(
                content,
                null,
                "export_backup.txt"
        );

        assertEquals(1, parsed.getRecords().size());
        assertEquals(PropertyCategory.LAND_FOR_SALE, parsed.getRecords().get(0).getCategory());
        assertEquals("LAND_000042", parsed.getRecords().get(0).getRecordId());
    }

    @Test
    public void parsesLegacyHeaderRecordsAndFlagsMalformedEntries() {
        String legacyAndBroken =
                "====================================\n"
                        + "PROPERTY RECORD #001\n"
                        + "Date: 2026-11-25\n"
                        + "Time: 08:00 AM\n"
                        + "Category: Land for Sale\n"
                        + "Mobile: 01911112222\n"
                        + "Details:\n"
                        + "Legacy land parcel\n\n"
                        + "====================================\n"
                        + "PROPERTY RECORD\n"
                        + "DATE: 2026-11-25\n";

        RecordParser.ParseResult result = RecordParser.parseFileContent(
                legacyAndBroken,
                PropertyCategory.LAND_FOR_SALE,
                "2026-11-25.txt"
        );

        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getInvalidCount());
        assertEquals("LAND_LEGACY_20261125_001", result.getRecords().get(0).getRecordId());
        assertEquals("Legacy land parcel", result.getRecords().get(0).getDetails());
        assertFalse(result.getWarnings().isEmpty());
    }
}
