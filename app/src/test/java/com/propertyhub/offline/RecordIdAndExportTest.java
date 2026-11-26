package com.propertyhub.offline;

import com.propertyhub.offline.export.ZipExportManager;
import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyRecord;
import com.propertyhub.offline.storage.RecordIdManager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RecordIdAndExportTest {

    @Test
    public void formatsAndExtractsMonotonicIdsAndPosterNames() {
        String houseId = RecordIdManager.formatId(PropertyCategory.HOUSE_FOR_RENT, 12);
        String landId = RecordIdManager.formatId(PropertyCategory.LAND_FOR_SALE, 105);

        assertEquals("HOUSE_000012", houseId);
        assertEquals("LAND_000105", landId);
        assertEquals(12L, RecordIdManager.extractNumericSuffix(PropertyCategory.HOUSE_FOR_RENT, houseId));
        assertEquals(105L, RecordIdManager.extractNumericSuffix(PropertyCategory.LAND_FOR_SALE, landId));

        assertEquals("House_000012.jpg",
                PropertyRecord.buildPosterFileName(PropertyCategory.HOUSE_FOR_RENT, houseId));
        assertEquals("Land_000105.jpg",
                PropertyRecord.buildPosterFileName(PropertyCategory.LAND_FOR_SALE, landId));
    }

    @Test
    public void buildsReadableDeterministicZipFileNames() {
        assertEquals(
                "Property_Hub_House_Rent_2026-11-26.zip",
                ZipExportManager.buildZipFileName(
                        PropertyCategory.HOUSE_FOR_RENT, "2026-11-26", "2026-11-26")
        );
        assertEquals(
                "Property_Hub_Land_Sale_2026-11-01_to_2026-11-26.zip",
                ZipExportManager.buildZipFileName(
                        PropertyCategory.LAND_FOR_SALE, "2026-11-01", "2026-11-26")
        );
        assertEquals(
                "Property_Hub_All_Posters_All_Dates.zip",
                ZipExportManager.buildZipFileName(null, null, null)
        );
    }
}
