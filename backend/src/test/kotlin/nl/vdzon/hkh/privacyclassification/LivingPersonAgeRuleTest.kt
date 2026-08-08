package nl.vdzon.hkh.privacyclassification

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class LivingPersonAgeRuleTest {

    private val referenceDate: LocalDate = LocalDate.of(2026, 8, 8)
    private val clock: Clock = Clock.fixed(referenceDate.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val rule = LivingPersonAgeRule(clock)

    @Test
    fun `birth date just under the 110 year boundary is likely living`() {
        val person = NamedPerson(birthDate = referenceDate.minusYears(109).toString())

        assertEquals(PersonAgeStatus.LIKELY_LIVING, rule.evaluate(person))
    }

    @Test
    fun `birth date exactly on the 110 year boundary is likely living`() {
        val person = NamedPerson(birthDate = referenceDate.minusYears(110).toString())

        assertEquals(PersonAgeStatus.LIKELY_LIVING, rule.evaluate(person))
    }

    @Test
    fun `birth date just over the 110 year boundary without marriage or child signal is deceased`() {
        val person = NamedPerson(birthDate = referenceDate.minusYears(111).toString())

        assertEquals(PersonAgeStatus.DECEASED, rule.evaluate(person))
    }

    @Test
    fun `marriage date just under the 95 year boundary is likely living`() {
        val person = NamedPerson(marriageDate = referenceDate.minusYears(94).toString())

        assertEquals(PersonAgeStatus.LIKELY_LIVING, rule.evaluate(person))
    }

    @Test
    fun `marriage date exactly on the 95 year boundary is likely living`() {
        val person = NamedPerson(marriageDate = referenceDate.minusYears(95).toString())

        assertEquals(PersonAgeStatus.LIKELY_LIVING, rule.evaluate(person))
    }

    @Test
    fun `marriage date just over the 95 year boundary is deceased`() {
        val person = NamedPerson(marriageDate = referenceDate.minusYears(96).toString())

        assertEquals(PersonAgeStatus.DECEASED, rule.evaluate(person))
    }

    @Test
    fun `child birth date just under the 95 year boundary is likely living`() {
        val person = NamedPerson(childBirthDate = referenceDate.minusYears(94).toString())

        assertEquals(PersonAgeStatus.LIKELY_LIVING, rule.evaluate(person))
    }

    @Test
    fun `child birth date just over the 95 year boundary is deceased`() {
        val person = NamedPerson(childBirthDate = referenceDate.minusYears(96).toString())

        assertEquals(PersonAgeStatus.DECEASED, rule.evaluate(person))
    }

    @Test
    fun `old birth date with a recent marriage is likely living`() {
        val person = NamedPerson(
            birthDate = referenceDate.minusYears(111).toString(),
            marriageDate = referenceDate.minusYears(94).toString(),
        )

        assertEquals(PersonAgeStatus.LIKELY_LIVING, rule.evaluate(person))
    }

    @Test
    fun `a present death date is deceased regardless of other fields`() {
        val person = NamedPerson(
            birthDate = referenceDate.minusYears(30).toString(),
            deathDate = referenceDate.minusYears(1).toString(),
        )

        assertEquals(PersonAgeStatus.DECEASED, rule.evaluate(person))
    }

    @Test
    fun `a present burial date is deceased regardless of other fields`() {
        val person = NamedPerson(
            birthDate = referenceDate.minusYears(30).toString(),
            burialDate = referenceDate.minusYears(1).toString(),
        )

        assertEquals(PersonAgeStatus.DECEASED, rule.evaluate(person))
    }

    @Test
    fun `year-only date is parsed with 1 january as implicit day`() {
        val birthYear = referenceDate.minusYears(109).year
        val person = NamedPerson(birthDate = birthYear.toString())

        assertEquals(PersonAgeStatus.LIKELY_LIVING, rule.evaluate(person))
    }

    @Test
    fun `a person without any usable date field is unknown fail closed`() {
        val person = NamedPerson()

        assertEquals(PersonAgeStatus.UNKNOWN_FAILCLOSED, rule.evaluate(person))
    }

    @Test
    fun `an unparsable birth date is unknown fail closed`() {
        val person = NamedPerson(birthDate = "not-a-date")

        assertEquals(PersonAgeStatus.UNKNOWN_FAILCLOSED, rule.evaluate(person))
    }

    @Test
    fun `an unparsable death date is unknown fail closed even with a valid birth date`() {
        val person = NamedPerson(
            birthDate = referenceDate.minusYears(50).toString(),
            deathDate = "not-a-date",
        )

        assertEquals(PersonAgeStatus.UNKNOWN_FAILCLOSED, rule.evaluate(person))
    }

    @Test
    fun `an unparsable marriage date is unknown fail closed`() {
        val person = NamedPerson(
            birthDate = referenceDate.minusYears(120).toString(),
            marriageDate = "not-a-date",
        )

        assertEquals(PersonAgeStatus.UNKNOWN_FAILCLOSED, rule.evaluate(person))
    }

    @Test
    fun `blank date fields are treated as missing, not unparsable`() {
        val person = NamedPerson(birthDate = "   ", deathDate = "")

        assertEquals(PersonAgeStatus.UNKNOWN_FAILCLOSED, rule.evaluate(person))
    }
}
