package com.propertyhub.offline;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RecordFormatterTest {

    @Test
    public void firstRecordStartsWithDividerAnd001() {
        String output = RecordFormatter.appendRecordToContent(
                "",
                1,
                "2026-11-26",
                "09:15 AM",
                PropertyCategory.HOUSE_FOR_RENT,
                "01712345678",
                "2 bedroom house available for rent.\nLocation: Iswarganj, Mymensingh.\nMonthly Rent: 5000 BDT."
        );

        String expected =
                "====================================\n"
                        + "PROPERTY RECORD #001\n"
                        + "Date: 2026-11-26\n"
                        + "Time: 09:15 AM\n"
                        + "Category: House for Rent\n"
                        + "Mobile: 01712345678\n"
                        + "Details:\n"
                        + "2 bedroom house available for rent.\n"
                        + "Location: Iswarganj, Mymensingh.\n"
                        + "Monthly Rent: 5000 BDT.\n";

        assertEquals(expected, output);
    }

    @Test
    public void subsequentRecordAppendsWithoutOverwriting() {
        String first = RecordFormatter.appendRecordToContent(
                "",
                1,
                "2026-11-26",
                "09:15 AM",
                PropertyCategory.HOUSE_FOR_RENT,
                "01712345678",
                "2 bedroom house"
        );

        int nextNumber = RecordFormatter.extractHighestRecordNumber(first) + 1;
        assertEquals(2, nextNumber);

        String second = RecordFormatter.appendRecordToContent(
                first,
                nextNumber,
                "2026-11-26",
                "10:30 AM",
                PropertyCategory.HOUSE_FOR_RENT,
                "01887654321",
                "Three-room house available.\nLocation: Mymensingh."
        );

        assertTrue(second.contains("PROPERTY RECORD #001"));
        assertTrue(second.contains("PROPERTY RECORD #002"));
        assertTrue(second.contains("01712345678"));
        assertTrue(second.contains("01887654321"));
        assertEquals(2, RecordFormatter.extractHighestRecordNumber(second));
    }

    @Test
    public void landCategoryUsesLandForSaleLabel() {
        String output = RecordFormatter.appendRecordToContent(
                "",
                1,
                "2026-11-26",
                "12:00 PM",
                PropertyCategory.LAND_FOR_SALE,
                "01911112222",
                "10 decimal residential plot near main road."
        );

        assertTrue(output.contains("Category: Land for Sale"));
        assertTrue(output.contains("PROPERTY RECORD #001"));
    }
}
