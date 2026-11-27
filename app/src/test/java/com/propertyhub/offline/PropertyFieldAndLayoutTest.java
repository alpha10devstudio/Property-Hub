package com.propertyhub.offline;

import com.propertyhub.offline.model.PropertyCategory;
import com.propertyhub.offline.model.PropertyFieldCatalog;
import com.propertyhub.offline.model.PropertyFieldConfig;
import com.propertyhub.offline.model.PropertyFieldValue;
import com.propertyhub.offline.model.PropertyRecord;
import com.propertyhub.offline.poster.DetailsParagraphParser.StyledSegment;
import com.propertyhub.offline.storage.RecordFormatter;
import com.propertyhub.offline.storage.RecordParser;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PropertyFieldAndLayoutTest {

    @Test
    public void catalogDefinesIndependentFieldsForHouseAndLand() {
        List<PropertyFieldConfig> houseFields = PropertyFieldCatalog.getFieldsForCategory(PropertyCategory.HOUSE_FOR_RENT);
        List<PropertyFieldConfig> landFields = PropertyFieldCatalog.getFieldsForCategory(PropertyCategory.LAND_FOR_SALE);

        assertEquals(4, houseFields.size());
        assertEquals(9, landFields.size());

        assertEquals("ফ্ল্যাট / বাসা ভাড়া দেওয়া হবে",
                PropertyFieldCatalog.getDefaultTitle(PropertyCategory.HOUSE_FOR_RENT));
        assertEquals("জমি বিক্রি করা হবে",
                PropertyFieldCatalog.getDefaultTitle(PropertyCategory.LAND_FOR_SALE));
    }

    @Test
    public void validatesBangladeshiElevenDigitMobileNumbers() {
        assertTrue(PropertyFieldCatalog.isValidBangladeshiMobile("01712345678"));
        assertTrue(PropertyFieldCatalog.isValidBangladeshiMobile("01987-654321"));
        assertTrue(PropertyFieldCatalog.isValidBangladeshiMobile("+8801812345678"));
        assertTrue(PropertyFieldCatalog.isValidBangladeshiMobile("০১৭১২৩৪৫৬৭৮"));

        assertFalse(PropertyFieldCatalog.isValidBangladeshiMobile("0171234567")); // 10 digits
        assertFalse(PropertyFieldCatalog.isValidBangladeshiMobile("017123456789")); // 12 digits
        assertFalse(PropertyFieldCatalog.isValidBangladeshiMobile("01112345678")); // invalid prefix 011
        assertFalse(PropertyFieldCatalog.isValidBangladeshiMobile("02712345678")); // doesn't start with 01
        assertFalse(PropertyFieldCatalog.isValidBangladeshiMobile(""));
    }

    @Test
    public void roundTripsStructuredRichFieldValuesThroughTxtFormatterAndParser() {
        List<PropertyFieldValue> fields = Arrays.asList(
                new PropertyFieldValue("title", "টাইটেল", "জমি বিক্রি করা হবে"),
                new PropertyFieldValue("land_address", "জমির ঠিকানা", "গৌরীপুর, ময়মনসিংহ",
                        Arrays.asList(
                                new StyledSegment("গৌরীপুর", true, false),
                                new StyledSegment(", ", false, false),
                                new StyledSegment("ময়মনসিংহ", false, true)
                        )),
                new PropertyFieldValue("mouza", "মৌজা", "কিসমত বড় ভাগ"),
                new PropertyFieldValue("land_area", "জমির পরিমাণ", ""),
                new PropertyFieldValue("mobile", "যোগাযোগ নম্বর", "01712345678")
        );

        PropertyRecord record = new PropertyRecord(
                "PH_LAND_20261126_0002",
                "2026-11-26",
                "03:45 PM",
                PropertyCategory.LAND_FOR_SALE,
                "জমি বিক্রি করা হবে",
                "01712345678",
                PropertyRecord.buildHumanReadableSummary(PropertyCategory.LAND_FOR_SALE, fields),
                fields,
                "2026-11-26.txt"
        );

        // Empty fields are excluded from poster-visible fields, alongside title/mobile
        List<PropertyFieldValue> visible = record.getPosterVisibleFieldValues();
        assertEquals(2, visible.size());
        assertEquals("land_address", visible.get(0).getFieldId());
        assertEquals("mouza", visible.get(1).getFieldId());

        String txtContent = RecordFormatter.appendRecordToContent("", record);
        assertTrue(txtContent.contains("TITLE: জমি বিক্রি করা হবে"));
        assertTrue(txtContent.contains("FIELDS_JSON:"));

        RecordParser.ParseResult parsed = RecordParser.parseFileContent(
                txtContent,
                PropertyCategory.LAND_FOR_SALE,
                "2026-11-26.txt"
        );
        assertEquals(1, parsed.getRecords().size());
        PropertyRecord restored = parsed.getRecords().get(0);
        assertEquals("PH_LAND_20261126_0002", restored.getRecordId());
        assertEquals("জমি বিক্রি করা হবে", restored.getTitle());
        assertEquals(5, restored.getFieldValues().size());

        List<StyledSegment> addressSegs = restored.getFieldValues().get(1).getSegments();
        assertEquals(3, addressSegs.size());
        assertTrue(addressSegs.get(0).isBold());
        assertFalse(addressSegs.get(0).isItalic());
        assertFalse(addressSegs.get(2).isBold());
        assertTrue(addressSegs.get(2).isItalic());
    }

    @Test
    public void deletesSingleRecordFromTxtWithoutAffectingRemainingRecords() {
        PropertyRecord r1 = new PropertyRecord(
                "PH_RENT_20261126_0001", "2026-11-26", "09:00 AM",
                PropertyCategory.HOUSE_FOR_RENT, "ফ্ল্যাট ভাড়া হবে",
                "01711111111", "Details 1", Collections.emptyList(), "2026-11-26.txt");
        PropertyRecord r2 = new PropertyRecord(
                "PH_RENT_20261126_0002", "2026-11-26", "10:00 AM",
                PropertyCategory.HOUSE_FOR_RENT, "বাসা ভাড়া হবে",
                "01822222222", "Details 2", Collections.emptyList(), "2026-11-26.txt");

        String content = RecordFormatter.appendRecordToContent("", r1);
        content = RecordFormatter.appendRecordToContent(content, r2);

        RecordFormatter.RemoveResult removeResult =
                RecordFormatter.removeRecordFromContent(content, "PH_RENT_20261126_0001");

        assertTrue(removeResult.isRemoved());
        RecordParser.ParseResult remaining = RecordParser.parseFileContent(
                removeResult.getUpdatedContent(),
                PropertyCategory.HOUSE_FOR_RENT,
                "2026-11-26.txt"
        );
        assertEquals(1, remaining.getRecords().size());
        assertEquals("PH_RENT_20261126_0002", remaining.getRecords().get(0).getRecordId());
        assertEquals("01822222222", remaining.getRecords().get(0).getMobile());
    }

    @Test
    public void resolvesCustomFieldsAndDefaultOverridesWithoutDroppingClearedDefaults() {
        com.propertyhub.offline.model.PropertyFieldConfig custom =
                new com.propertyhub.offline.model.PropertyFieldConfig(
                        "custom_floor_no",
                        PropertyCategory.HOUSE_FOR_RENT,
                        "ফ্লোর নম্বর",
                        "৩য় তলা",
                        "ফ্লোর নম্বর লিখুন",
                        com.propertyhub.offline.model.PropertyFieldInputType.SINGLE_LINE,
                        true,
                        true,
                        100,
                        true,
                        true
                );

        String serializedCustom = PropertyFieldCatalog.serializeCustomFields(Collections.singletonList(custom));
        List<PropertyFieldConfig> parsedCustom = PropertyFieldCatalog.parseCustomFields(
                serializedCustom, PropertyCategory.HOUSE_FOR_RENT);
        assertEquals(1, parsedCustom.size());
        assertEquals("custom_floor_no", parsedCustom.get(0).getFieldId());
        assertTrue(parsedCustom.get(0).isCustom());
        assertTrue(parsedCustom.get(0).isRequired());

        java.util.Map<String, String> overrides = new java.util.HashMap<>();
        overrides.put("title", ""); // explicit cleared default
        overrides.put("address", "ময়মনসিংহ সদর");

        String serializedOverrides = PropertyFieldCatalog.serializeDefaultOverrides(overrides);
        java.util.Map<String, String> parsedOverrides =
                PropertyFieldCatalog.parseDefaultOverrides(serializedOverrides);

        List<PropertyFieldConfig> resolved = PropertyFieldCatalog.resolveEffectiveFields(
                PropertyCategory.HOUSE_FOR_RENT, parsedCustom, parsedOverrides);

        assertEquals(5, resolved.size());
        assertEquals("title", resolved.get(0).getFieldId());
        assertEquals("", resolved.get(0).getDefaultValue());
        assertEquals("address", resolved.get(2).getFieldId());
        assertEquals("ময়মনসিংহ সদর", resolved.get(2).getDefaultValue());
        // Custom field is placed cleanly before mobile
        assertEquals("custom_floor_no", resolved.get(3).getFieldId());
        assertEquals("mobile", resolved.get(4).getFieldId());
    }
}
