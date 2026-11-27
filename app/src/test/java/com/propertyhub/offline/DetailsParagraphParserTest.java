package com.propertyhub.offline;

import com.propertyhub.offline.poster.DetailsParagraphParser;
import com.propertyhub.offline.poster.DetailsParagraphParser.StyledSegment;
import com.propertyhub.offline.poster.DynamicPosterLayoutEngine;
import com.propertyhub.offline.poster.DynamicPosterLayoutEngine.GeometryMetrics;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void parsesMarkdownBoldAndItalicInsideQuotedParagraphs() {
        String input = "\"**ঠিকানাঃ** *গৌরীপুর, ময়মনসিংহ*।\", \"***বড় প্লট***, _পাকা রাস্তা সংলগ্ন_\"";

        List<List<StyledSegment>> styled = DetailsParagraphParser.parseStyledParagraphs(input);
        assertEquals(2, styled.size());

        List<StyledSegment> p0 = styled.get(0);
        assertEquals(4, p0.size());
        assertEquals("ঠিকানাঃ", p0.get(0).getText());
        assertTrue(p0.get(0).isBold());
        assertFalse(p0.get(0).isItalic());

        assertEquals(" ", p0.get(1).getText());
        assertFalse(p0.get(1).isBold());
        assertFalse(p0.get(1).isItalic());

        assertEquals("গৌরীপুর, ময়মনসিংহ", p0.get(2).getText());
        assertFalse(p0.get(2).isBold());
        assertTrue(p0.get(2).isItalic());

        assertEquals("।", p0.get(3).getText());
        assertFalse(p0.get(3).isBold());
        assertFalse(p0.get(3).isItalic());

        List<StyledSegment> p1 = styled.get(1);
        assertEquals(3, p1.size());
        assertEquals("বড় প্লট", p1.get(0).getText());
        assertTrue(p1.get(0).isBold());
        assertTrue(p1.get(0).isItalic());

        assertEquals(", ", p1.get(1).getText());
        assertEquals("পাকা রাস্তা সংলগ্ন", p1.get(2).getText());
        assertFalse(p1.get(2).isBold());
        assertTrue(p1.get(2).isItalic());
    }

    @Test
    public void preservesSnakeCaseAndUnmatchedAsterisksLiterally() {
        String input = "plot_no_12, 5 * 3 katha, \\*escaped\\*";

        List<StyledSegment> segments = DetailsParagraphParser.parseInlineFormatting(input);
        assertEquals(1, segments.size());
        assertEquals("plot_no_12, 5 * 3 katha, *escaped*", segments.get(0).getText());
        assertFalse(segments.get(0).isBold());
        assertFalse(segments.get(0).isItalic());
    }

    @Test
    public void dynamicLayoutGeometryPreservesGapsAndPreventsOverlapAcrossDetailsSizes() {
        float[] testDetailsHeights = new float[] { 340f, 520f, 780f, 1020f, 1280f, 1450f };
        for (float h : testDetailsHeights) {
            GeometryMetrics m = DynamicPosterLayoutEngine.computeGeometryMetrics(1440, 1920, h);
            assertTrue("Header overlaps outer top", m.headerTop > m.outerTop);
            assertTrue("Header overlaps Title card", m.headerBottom < m.titleTop);
            assertTrue("Title to details gap collapsed",
                    (m.detailsTop - m.titleBottom) >= DynamicPosterLayoutEngine.CARD_GAP_PX - 0.01f);
            assertTrue("Details to contact gap collapsed",
                    (m.contactTop - m.detailsBottom) >= DynamicPosterLayoutEngine.CARD_GAP_PX - 0.01f);
            assertTrue("Contact overflows outer bottom", m.contactBottom < m.outerBottom);
            assertTrue("Details box overlaps card edges", m.detailsTextTop > m.detailsTop);
            assertTrue("Details box overlaps card bottom", m.detailsTextBottom < m.detailsBottom);
        }

        GeometryMetrics compact = DynamicPosterLayoutEngine.computeGeometryMetrics(1440, 1920, 420f);
        GeometryMetrics expanded = DynamicPosterLayoutEngine.computeGeometryMetrics(1440, 1920, 960f);
        assertTrue(compact.getDetailsCardHeight() < expanded.getDetailsCardHeight());
    }

    @Test
    public void clampPosterHeightEnforcesMinAndMaxBounds() {
        assertEquals(2160, DynamicPosterLayoutEngine.MAX_POSTER_HEIGHT_PX);
        assertEquals(480, DynamicPosterLayoutEngine.clampPosterHeight(120));
        assertEquals(980, DynamicPosterLayoutEngine.clampPosterHeight(980));
        assertEquals(2160, DynamicPosterLayoutEngine.clampPosterHeight(2160));
        assertEquals(2160, DynamicPosterLayoutEngine.clampPosterHeight(4800));
    }
}
