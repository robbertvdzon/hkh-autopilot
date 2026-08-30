package nl.vdzon.hkh.personsearch

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

/** Vaste, geldige testsleutel (nooit gebruikt buiten tests) zodat de fail-closed cipher in tests werkt. */
fun testPayloadCipher(): PersonSearchPayloadCipher =
    PersonSearchPayloadCipher(Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }))

fun testJobStore(clock: Clock = Clock.systemUTC()): PersonSearchJobStore = PersonSearchJobStore(testPayloadCipher(), clock)

/** Handmatig verzetbare klok voor retentie-/verlooptests met een gesimuleerde tijdlijn. */
class MutablePersonSearchClock(var instant: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = instant
}
