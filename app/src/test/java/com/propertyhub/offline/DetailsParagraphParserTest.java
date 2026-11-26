package com.propertyhub.offline;

import com.propertyhub.offline.poster.DetailsParagraphParser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class DetailsParagraphParserTest {

    @Test
    public void parsesQuotedBengaliParagraphsAndPreservesInnerCommas() {
        String input = "\"ঠিকানাঃ কিসমত বড় ভাগ, গৌরীপুর, ময়মনসিংহ।\",\n"
                + "\"দুটি রুম, কিচেন বাথরুম আছে।\"";

        List<String> paragraphs = DetailsParagraphParser.parse(input);
        assertEquals(2, paragraphs.size());
        assertEquals("ঠিকানাঃ কিসমত বড় ভাগ, গৌরীপুর, ময়মনসিংহ।", paragraphs.get(0));
        assertEquals("দুটি রুম, কিচেন বাথরুম আছে।", paragraphs.get(1));
    }

    @Test
    public void parsesSingleLineCommaSeparatedQuotedParagraphs() {
        String input = "\"Plot 12, Road 4, Uttara\", \"3 Beds, 2 Baths, Gas & Lift\",";

        List<String> paragraphs = DetailsParagraphParser.parse(input);
        assertEquals(2, paragraphs.size());
        assertEquals("Plot 12, Road 4, Uttara", paragraphs.get(0));
        assertEquals("3 Beds, 2 Baths, Gas & Lift", paragraphs.get(1));
    }

    @Test
    public void parsesSmartCurlyQuotesCleanly() {
        String input = "“বাড়ি ভাড়া, দোতলা”, “সকাল ৯টা - রাত ১০টা”";

        List<String> paragraphs = DetailsParagraphParser.parse(input);
        assertEquals(2, paragraphs.size());
        assertEquals("বাড়ি ভাড়া, দোতলা", paragraphs.get(0));
        assertEquals("সকাল ৯টা - রাত ১০টা", paragraphs.get(1));
    }

    @Test
    public void preservesPlainMultilineTextWithoutQuotes() {
        String input = "Location: Mymensingh\nTwo Bedroom House, Rent Negotiable";

        List<String> paragraphs = DetailsParagraphParser.parse(input);
        assertEquals(2, paragraphs.size());
        assertEquals("Location: Mymensingh", paragraphs.get(0));
        assertEquals("Two Bedroom House, Rent Negotiable", paragraphs.get(1));
    }

    @Test
    public void handlesUnclosedTrailingQuoteWithoutDataLoss() {
        String input = "\"ঠিকানাঃ ঢাকা।\", \"২ রুম, ১ বাথ আছে।";

        List<String> paragraphs = DetailsParagraphParser.parse(input);
        assertEquals(2, paragraphs.size());
        assertEquals("ঠিকানাঃ ঢাকা।", paragraphs.get(0));
        assertEquals("২ রুম, ১ বাথ আছে।", paragraphs.get(1));
    }
}
