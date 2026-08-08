package nl.vdzon.hkh.privacyclassification

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class GedcomResnRuleTest {

    private val rule = GedcomResnRule()

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["   ", "\n\n"])
    fun `no gedcom source is not applicable`(source: String?) {
        assertEquals(GedcomResnSignal.NOT_APPLICABLE, rule.evaluate(source))
    }

    @Test
    fun `valid gedcom without RESN is none`() {
        val source = """
            0 HEAD
            1 GEDC
            2 VERS 7.0
            0 @I1@ INDI
            1 NAME Jan /Janssen/
            1 BIRT
            2 DATE 1 JAN 1900
            0 TRLR
        """.trimIndent()

        assertEquals(GedcomResnSignal.NONE, rule.evaluate(source))
    }

    @ParameterizedTest
    @ValueSource(strings = ["CONFIDENTIAL", "LOCKED", "PRIVACY", "confidential", "  locked  "])
    fun `RESN at record level with a blocking value is blocked`(resnValue: String) {
        val source = """
            0 @I1@ INDI
            1 NAME Jan /Janssen/
            1 RESN $resnValue
        """.trimIndent()

        assertEquals(GedcomResnSignal.BLOCKED, rule.evaluate(source))
    }

    @Test
    fun `RESN nested within an individual fact is blocked`() {
        val source = """
            0 @I1@ INDI
            1 NAME Jan /Janssen/
            1 BIRT
            2 DATE 1 JAN 1990
            2 RESN CONFIDENTIAL
            1 DEAT
            2 DATE 1 JAN 2020
        """.trimIndent()

        assertEquals(GedcomResnSignal.BLOCKED, rule.evaluate(source))
    }

    @Test
    fun `RESN with a non-blocking value does not block`() {
        val source = """
            0 @I1@ INDI
            1 RESN OTHER
        """.trimIndent()

        assertEquals(GedcomResnSignal.NONE, rule.evaluate(source))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "this is not gedcom at all",
            "0",
            "abc RESN CONFIDENTIAL",
            "-1 RESN CONFIDENTIAL",
        ],
    )
    fun `syntactically invalid gedcom source is fail closed blocked`(source: String) {
        assertEquals(GedcomResnSignal.BLOCKED, rule.evaluate(source))
    }
}
