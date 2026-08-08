package nl.vdzon.hkh.privacyclassification

/** Machineleesbare uitkomst van [GedcomResnRule] voor de ruwe GEDCOM-brontekst van een record. */
enum class GedcomResnSignal {
    BLOCKED,
    NONE,
    NOT_APPLICABLE,
}

/**
 * Doorzoekt optionele, ruwe GEDCOM 7.0-brontekst ([GenealogicalRecord.gedcomSource]) recursief op
 * een blokkerende RESN-markering (`CONFIDENTIAL`, `LOCKED` of `PRIVACY`), op elk nestingniveau -
 * zowel op recordniveau als binnen een geneste gebeurtenis/feit.
 *
 * GEDCOM-brontekst bestaat uit regels in het formaat `LEVEL [@XREF@] TAG [VALUE]`; het `LEVEL`-getal
 * codeert de hiërarchie (een hoger getal is een nakomeling van de voorgaande regel met een lager of
 * gelijk niveau). Deze regel bouwt die hiërarchie op tot een boom en doorzoekt die boom recursief.
 *
 * Resultaat is altijd een van drie deterministische waarden: [GedcomResnSignal.NOT_APPLICABLE]
 * wanneer er geen brontekst is (leeg/`null`, bijvoorbeeld omdat de bron alleen JSON of RDF is),
 * [GedcomResnSignal.NONE] wanneer de brontekst geldig is maar geen blokkerende RESN-markering
 * bevat, en [GedcomResnSignal.BLOCKED] zodra ergens in de boom een blokkerende RESN-markering
 * gevonden wordt. Syntactisch ongeldige (niet-lege maar niet als `LEVEL TAG VALUE` te lezen)
 * brontekst wordt fail-closed als [GedcomResnSignal.BLOCKED] behandeld, naar het patroon van de
 * overige fail-closed-conventies in deze module.
 */
class GedcomResnRule {

    fun evaluate(source: String?): GedcomResnSignal {
        val trimmed = source?.trim()
        if (trimmed.isNullOrEmpty()) return GedcomResnSignal.NOT_APPLICABLE

        val roots = runCatching { buildTree(parseLines(trimmed)) }.getOrElse { return GedcomResnSignal.BLOCKED }
        return if (containsBlockingResn(roots)) GedcomResnSignal.BLOCKED else GedcomResnSignal.NONE
    }

    private fun parseLines(source: String): List<GedcomLine> =
        source.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map(::parseLine)

    private fun parseLine(line: String): GedcomLine {
        val tokens = line.split(Regex("\\s+"))
        require(tokens.size >= 2) { "GEDCOM-regel mist een level of tag: '$line'" }

        val level = tokens[0].toIntOrNull()
        require(level != null && level >= 0) { "GEDCOM-regel heeft geen geldig levelgetal: '$line'" }

        val rest = tokens.drop(1)
        val isPointer = rest[0].length >= 2 && rest[0].startsWith("@") && rest[0].endsWith("@")
        val tag = if (isPointer) rest.getOrNull(1) else rest[0]
        require(!tag.isNullOrEmpty()) { "GEDCOM-regel mist een tag: '$line'" }

        val valueTokens = if (isPointer) rest.drop(2) else rest.drop(1)
        val value = valueTokens.joinToString(" ").ifBlank { null }

        return GedcomLine(level = level, tag = tag, value = value)
    }

    private fun buildTree(lines: List<GedcomLine>): List<GedcomNode> {
        val roots = mutableListOf<GedcomNode>()
        val ancestors = ArrayDeque<GedcomNode>()

        for (line in lines) {
            val node = GedcomNode(line)
            while (ancestors.isNotEmpty() && ancestors.last().line.level >= line.level) {
                ancestors.removeLast()
            }
            if (ancestors.isEmpty()) {
                roots.add(node)
            } else {
                ancestors.last().children.add(node)
            }
            ancestors.addLast(node)
        }

        return roots
    }

    private fun containsBlockingResn(nodes: List<GedcomNode>): Boolean =
        nodes.any { node -> node.isBlockingResn() || containsBlockingResn(node.children) }

    private fun GedcomNode.isBlockingResn(): Boolean =
        line.tag.equals("RESN", ignoreCase = true) &&
            line.value?.trim()?.uppercase() in BLOCKING_RESN_VALUES

    private data class GedcomLine(val level: Int, val tag: String, val value: String?)

    private class GedcomNode(val line: GedcomLine, val children: MutableList<GedcomNode> = mutableListOf())

    private companion object {
        val BLOCKING_RESN_VALUES = setOf("CONFIDENTIAL", "LOCKED", "PRIVACY")
    }
}
