package nl.vdzon.hkh.news.entity

import tools.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

data class GazetteerEntry(val canonicalLabel: String, val aliases: List<String>)

data class NewsGazetteer(val entriesByType: Map<NewsEntityType, List<GazetteerEntry>>) {
    companion object {
        fun of(vararg entries: Pair<NewsEntityType, List<GazetteerEntry>>): NewsGazetteer =
            NewsGazetteer(entries.toMap())
    }
}

@Component
class NewsGazetteerLoader(private val objectMapper: ObjectMapper) {
    fun load(): NewsGazetteer = NewsGazetteer(
        entriesByType = mapOf(
            NewsEntityType.PLEK to readEntries("gazetteer/plek.json"),
            NewsEntityType.PERSOON to readEntries("gazetteer/persoon.json"),
            NewsEntityType.GEBEURTENIS to readEntries("gazetteer/gebeurtenis.json"),
        ),
    )

    private fun readEntries(classpathLocation: String): List<GazetteerEntry> {
        val resource = ClassPathResource(classpathLocation)
        return objectMapper.readValue(
            resource.inputStream,
            objectMapper.typeFactory.constructCollectionType(List::class.java, GazetteerEntry::class.java),
        )
    }
}

@Configuration
class NewsGazetteerConfiguration {
    @Bean
    fun newsGazetteer(loader: NewsGazetteerLoader): NewsGazetteer = loader.load()
}
