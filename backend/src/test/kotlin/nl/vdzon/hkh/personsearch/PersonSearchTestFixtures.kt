package nl.vdzon.hkh.personsearch

import java.time.Clock
import java.util.Base64

/** Vaste, geldige testsleutel (nooit gebruikt buiten tests) zodat de fail-closed cipher in tests werkt. */
fun testPayloadCipher(): PersonSearchPayloadCipher =
    PersonSearchPayloadCipher(Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() }))

fun testJobStore(clock: Clock = Clock.systemUTC()): PersonSearchJobStore = PersonSearchJobStore(testPayloadCipher(), clock)
