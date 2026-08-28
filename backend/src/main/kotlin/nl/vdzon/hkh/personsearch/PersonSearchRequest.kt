package nl.vdzon.hkh.personsearch

import java.security.MessageDigest

/**
 * Ruwe, op zichzelf staande invoer voor een persoonszoekopdracht, naar het patroon van
 * `ExternalVerificationRequest`/`RecordIntake`: geen koppeling aan een bestaand persistent record.
 */
data class PersonSearchRequest(
    val recognizedName: String,
    val secondName: String? = null,
    val eventType: String? = null,
    val yearOrPeriod: String? = null,
    val heemskerkMeaningQid: String? = null,
    val originalQuery: String = recognizedName,
)

/** Bouwt de `name`-queryparameter voor Records/Search uit de herkende naam-, jaar-/periodevelden. */
fun PersonSearchRequest.searchNameQuery(): String =
    listOfNotNull(recognizedName, secondName, yearOrPeriod)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")

/** Normaliseert de vraagtekst voor de idempotentiesleutel: getrimd, kleine letters, enkele spaties. */
private fun normalizeQuery(query: String): String =
    query.trim().lowercase().replace(Regex("\\s+"), " ")

/**
 * Idempotentiesleutel = sessie-id + genormaliseerde vraagtekst + gekozen Heemskerk-betekenis
 * (indien van toepassing), gehasht zodat de sleutel zelf geen leesbare vraaginhoud lekt.
 */
fun personSearchIdempotencyKey(sessionId: String, request: PersonSearchRequest): String {
    val raw = "$sessionId|${normalizeQuery(request.originalQuery)}|${request.heemskerkMeaningQid.orEmpty()}"
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
