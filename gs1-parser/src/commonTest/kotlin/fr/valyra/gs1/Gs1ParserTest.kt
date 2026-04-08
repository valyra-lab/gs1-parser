package fr.valyra.gs1

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Gs1ParserTest {
    @Test
    fun lenient_parsesHealthcareSample() {
        val raw = "\u001D01040462411101592111825531087847\u001D10F2LK272\u001D172609307128137589\u001D7144067781"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertTrue(result.isGs1)
        assertEquals("04046241110159", result.get("01")?.value)
        assertEquals("11825531087847", result.get("21")?.value)
        assertEquals("F2LK272", result.get("10")?.value)
        assertEquals("260930", result.get("17")?.value)
        assertEquals("8137589", result.get("712")?.value)
        assertEquals("4067781", result.get("714")?.value)
    }

    @Test
    fun lenient_allowsMissingSeparatorForVariableLength() {
        val raw = "0104046241110159" +
                "10F2LK272" +
                "17260930"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertTrue(result.isGs1)
        assertTrue(result.fields.any { it.ai == "10" })
    }

    @Test
    fun strict_rejectsMissingSeparatorForVariableLength() {
        val raw = "0104046241110159" +
                "10F2LK272" +
                "17260930"
        val result = Gs1.parse(raw, ParseMode.STRICT)
        val hasAi10 = result.fields.any { it.ai == "10" }
        val hasError = result.errors.any { it.code == "INVALID_FIELD" }
        assertTrue(!hasAi10 || hasError || !result.errors.isEmpty())
    }

    @Test
    fun invalidCheckDigit_emitsError() {
        val raw = "0104046241110150"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertTrue(result.isGs1)
        val errorCodes = result.errors.map { it.code }
        assertTrue("CHECK_DIGIT" in errorCodes)
    }

    @Test
    fun normalization_handlesPrefixesAndAltSeparators() {
        val raw = "]C10104046241110159|10F2LK272\u00E817260930"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertTrue(result.isGs1)
        assertEquals("04046241110159", result.get("01")?.value)
        assertEquals("F2LK272", result.get("10")?.value)
        assertEquals("260930", result.get("17")?.value)
    }

    @Test
    fun doubleSeparator_emitsUnexpectedGsWarning() {
        val gs = "\u001D"
        val raw = "0104046241110159" + gs + gs + "2112345"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertTrue(result.isGs1)
        val warningCodes = result.warnings.map { it.code }
        assertTrue("UNEXPECTED_GS" in warningCodes)
        assertEquals("12345", result.get("21")?.value)
    }

    @Test
    fun normalization_stripsSymbologyIdentifier_d2() {
        val raw = "]d20104046241110159"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertEquals("04046241110159", result.get("01")?.value)
    }

    @Test
    fun normalization_stripsSymbologyIdentifier_Q3() {
        val raw = "]Q30104046241110159"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertEquals("04046241110159", result.get("01")?.value)
    }

    @Test
    fun normalization_replacesPipeSeparator() {
        val raw = "0104046241110159|17260930"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertEquals("04046241110159", result.get("01")?.value)
        assertEquals("260930", result.get("17")?.value)
    }

    @Test
    fun normalization_replacesUnitSeparator_001C() {
        val raw = "0104046241110159\u001C17260930"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertEquals("04046241110159", result.get("01")?.value)
        assertEquals("260930", result.get("17")?.value)
    }

    @Test
    fun normalization_trimLeadingWhitespace() {
        val raw = "   0104046241110159"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertEquals("04046241110159", result.get("01")?.value)
    }

    @Test
    fun unknownAi_emitsErrorAndSetsRemainder() {
        val raw = "ZZZZ99999"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertTrue(result.errors.any { it.code == "UNKNOWN_AI" })
        assertEquals("ZZZZ99999", result.remainder)
    }

    @Test
    fun partiallyParsed_unknownAiMidString_setsRemainder() {
        val gs = "\u001D"
        val raw = "0104046241110159${gs}ZZZZBADINPUT"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertEquals("04046241110159", result.get("01")?.value)
        assertTrue(result.errors.any { it.code == "UNKNOWN_AI" })
        assertEquals("ZZZZBADINPUT", result.remainder)
    }

    @Test
    fun fixedLength_parsedWithoutSeparator() {
        val raw = "0104046241110159"
        val result = Gs1.parse(raw, ParseMode.STRICT)
        assertTrue(result.isGs1)
        assertEquals("04046241110159", result.get("01")?.value)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun fixedLength_truncatedValue_returnsError() {
        val raw = "010404624111"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertTrue(result.errors.any { it.code == "INVALID_FIELD" || it.code == "UNKNOWN_AI" })
    }

    @Test
    fun variableLength_atEndOfString_noSeparatorNeeded() {
        // AI 10 at end — no GS required
        val gs = "\u001D"
        val raw = "0104046241110159${gs}10BATCHXYZ"
        val result = Gs1.parse(raw, ParseMode.STRICT)
        assertEquals("BATCHXYZ", result.get("10")?.value)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun variableLength_emptyValue_isInvalid() {
        val gs = "\u001D"
        val raw = "0104046241110159${gs}10${gs}17260930"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        val ai10 = result.get("10")
        if (ai10 != null) {
            assertTrue(ai10.value.isEmpty().not() || result.errors.isNotEmpty())
        }
    }

    @Test
    fun validCheckDigit_emitsNoCheckDigitError() {
        val raw = "0104046241110159"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertTrue(result.errors.none { it.code == "CHECK_DIGIT" })
    }

    @Test
    fun get_returnsNullForAbsentAi() {
        val raw = "0104046241110159"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertEquals(null, result.get("21"))
    }

    @Test
    fun getAll_returnsAllMatchingFields() {
        val gs = "\u001D"
        val raw = "0104046241110159${gs}21AAA${gs}21BBB"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        val serials = result.getAll("21")
        assertEquals(2, serials.size)
        assertTrue(serials.any { it.value == "AAA" })
        assertTrue(serials.any { it.value == "BBB" })
    }

    @Test
    fun isGs1_falseWhenNoFieldParsed() {
        val raw = "ZZZZ99999"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertTrue(!result.isGs1)
    }

    @Test
    fun hasErrors_trueWhenCheckDigitInvalid() {
        val raw = "0104046241110150"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertTrue(result.hasErrors)
    }

    @Test
    fun multipleFields_parsedInOrder() {
        val gs = "\u001D"
        val raw = "0104046241110159${gs}17260930${gs}10BATCH01"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertEquals("04046241110159", result.get("01")?.value)
        assertEquals("260930", result.get("17")?.value)
        assertEquals("BATCH01", result.get("10")?.value)
    }

    @Test
    fun multipleGtin_emitsWarning() {
        val gs = "\u001D"
        val raw = "0104046241110159${gs}0104046241110159"
        val result = Gs1.parse(raw, ParseMode.LENIENT)
        assertTrue(result.warnings.any { it.code == "MULTIPLE_GTIN" })
    }

    @Test
    fun normalizer_noOpOnCleanString() {
        val clean = "0104046241110159"
        assertEquals(clean, Gs1Normalizer.normalize(clean))
    }

    @Test
    fun normalizer_stripsLeadingGs() {
        val raw = "\u001D0104046241110159"
        assertEquals("0104046241110159", Gs1Normalizer.normalize(raw))
    }

    @Test
    fun normalizer_unknownPrefixLeftIntact() {
        val raw = "]XX0104046241110159"
        val normalized = Gs1Normalizer.normalize(raw)
        assertTrue(normalized.startsWith("]XX"))
    }
}
