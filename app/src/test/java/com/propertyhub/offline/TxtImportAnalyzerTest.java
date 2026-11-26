package com.propertyhub.offline;

import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyRecord;
import com.propertyhub.offline.poster.TxtImportAnalyzer;
import com.propertyhub.offline.storage.RecordFormatter;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TxtImportAnalyzerTest {

    @Test
    public void skipsExistingValidPostersAndGeneratesOnlyMissingRecords() {
        StringBuilder txt = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            PropertyRecord rec = new PropertyRecord(
                    String.format("HOUSE_%06d", i),
                    "2026-11-26",
                    "10:00 AM",
                    PropertyCategory.HOUSE_FOR_RENT,
                    "0170000000" + i,
                    "House details " + i,
                    "2026-11-26.txt"
            );
            txt.append(RecordFormatter.formatRecord(rec)).append('\n');
        }

        TxtImportAnalyzer.ImportAnalysisResult result = TxtImportAnalyzer.analyze(
                Collections.singletonList(new TxtImportAnalyzer.ImportedTxtSource(
                        "2026-11-26.txt",
                        txt.toString(),
                        PropertyCategory.HOUSE_FOR_RENT
                ))
        );

        assertEquals(5, result.getUniqueCleanRecords().size());
        assertEquals(0, result.getInvalidRecordsCount());
        assertEquals(0, result.getDuplicateRecordsSkippedCount());

        // Existing valid posters: House_000001.jpg, House_000003.jpg, House_000005.jpg
        Set<String> existingValidPosters = new HashSet<>(Arrays.asList(
                "House_000001.jpg",
                "House_000003.jpg",
                "House_000005.jpg"
        ));

        List<String> toGenerate = new java.util.ArrayList<>();
        for (PropertyRecord rec : result.getUniqueCleanRecords()) {
            if (!existingValidPosters.contains(rec.getPosterFileName())) {
                toGenerate.add(rec.getRecordId());
            }
        }

        assertEquals(Arrays.asList("HOUSE_000002", "HOUSE_000004"), toGenerate);
    }

    @Test
    public void deduplicatesIdenticalDuplicatesAndFlagsConflictingRecordIds() {
        PropertyRecord house1CopyA = new PropertyRecord(
                "HOUSE_000001", "2026-11-25", "09:00 AM",
                PropertyCategory.HOUSE_FOR_RENT, "01711111111", "2 Bed Flat", "day1.txt");
        PropertyRecord house1CopyB = new PropertyRecord(
                "HOUSE_000001", "2026-11-25", "09:00 AM",
                PropertyCategory.HOUSE_FOR_RENT, "01711111111", "2 Bed Flat", "backup.txt");

        PropertyRecord land2VersionA = new PropertyRecord(
                "LAND_000002", "2026-11-25", "10:00 AM",
                PropertyCategory.LAND_FOR_SALE, "01822222222", "10 Decimal Plot", "day1.txt");
        PropertyRecord land2VersionB = new PropertyRecord(
                "LAND_000002", "2026-11-26", "11:00 AM",
                PropertyCategory.LAND_FOR_SALE, "01899999999", "20 Decimal Corner Plot", "day2.txt");

        String file1 = RecordFormatter.appendRecordToContent("", house1CopyA);
        file1 = RecordFormatter.appendRecordToContent(file1, land2VersionA);

        String file2 = RecordFormatter.appendRecordToContent("", house1CopyB);
        file2 = RecordFormatter.appendRecordToContent(file2, land2VersionB);

        TxtImportAnalyzer.ImportAnalysisResult result = TxtImportAnalyzer.analyze(Arrays.asList(
                new TxtImportAnalyzer.ImportedTxtSource("day1.txt", file1, null),
                new TxtImportAnalyzer.ImportedTxtSource("day2.txt", file2, null)
        ));

        assertEquals(4, result.getTotalRecordsScanned());
        assertEquals(1, result.getDuplicateRecordsSkippedCount());
        assertEquals(1, result.getConflicts().size());
        assertEquals("LAND_000002", result.getConflicts().get(0).getFirstRecord().getRecordId());
        // Only HOUSE_000001 is clean & auto-queued; LAND_000002 is withheld for manual conflict review
        assertEquals(1, result.getUniqueCleanRecords().size());
        assertEquals("HOUSE_000001", result.getUniqueCleanRecords().get(0).getRecordId());
        assertTrue(result.getWarnings().get(0).contains("Conflict on LAND_000002"));
    }
}
