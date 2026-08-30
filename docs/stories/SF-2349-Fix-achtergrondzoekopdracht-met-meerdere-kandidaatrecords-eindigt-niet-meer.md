# SF-2349 - Fix: achtergrondzoekopdracht met meerdere kandidaatrecords eindigt niet meer structureel in FAILED

## Story

Fix: achtergrondzoekopdracht met meerdere kandidaatrecords eindigt niet meer structureel in FAILED

<!-- refined-by-factory -->

## Scope
`PersonSearchService.handleSearchSuccess` (backend/src/main/kotlin/nl/vdzon/hkh/personsearch/PersonSearchService.kt, regels 163-193) behandelt vandaag elke `Records/Show`-mislukking als totale bronuitval: `if (shows.any { it is ArchivesShowOutcome.Failure })` gooit alle reeds succesvol opgehaalde en gevalideerde records weg en zet de job op `FAILED` (`PersonSearchOutcome.SourceOutage`), ook wanneer andere kandidaten wél een geldig Show-record opleverden.

Deze story wijzigt uitsluitend die beslislogica:
- Filter de Show-uitkomsten op `ArchivesShowOutcome.Success`; bouw het antwoord (`PersonSearchAnswerBuilder.build`) uitsluitend uit die succesvolle, gevalideerde records — ongewijzigd blijft dat alleen Person/Event/RelationEP/Source uit een gevalideerd Show-record een feitelijke zin en bronmarkering opleveren.
- Zodra er minstens één succesvol Show-record is, wordt de job `READY` (`PersonSearchOutcome.SupportedAnswer`) op basis van die deelverzameling, in plaats van `FAILED`.
- Alleen wanneer **geen enkel** kandidaatrecord een geldig Show-record oplevert (alle Show-aanroepen falen na de bestaande begrensde retries), blijft het bestaande gedrag gehandhaafd: job `FAILED` (`PersonSearchOutcome.SourceOutage`) — dit is het bestaande regressiegedrag bij algehele bronuitval en verandert niet.
- Wanneer minstens één (maar niet alle) kandidaatrecords niet verifieerbaar waren, breidt `PersonSearchAnswerBuilder.buildDisclaimer` de bestaande bewijsbegrenzing-tekst (het `disclaimer`-veld van `PersonSearchAnswer`) uit met een zichtbare, aantal-gebaseerde vermelding van het aantal niet-verifieerbare kandidaten (bijv. "1 van de 4 gevonden kandidaten kon niet worden geverifieerd en is buiten beschouwing gelaten."). Er komt geen nieuw scherm of nieuwe UX-toestand bij; dit blijft binnen het bestaande supported-answer-scherm.
- De synchrone route (2-secondendeadline in `PersonSearchService.submit`) wijzigt niet: deze fix raakt alleen de beslislogica in `handleSearchSuccess`, die zowel synchroon binnen het budget als asynchroon na de deadline wordt aangeroepen. Binnen het budget verschijnt dus nog steeds alleen een antwoord bij een uitkomst die op dat moment al terminaal is; dit gedrag volgt automatisch uit de bestaande timeout-afhandeling en hoeft niet apart gebouwd te worden.
- Er verandert niets aan `PersonSearchRateLimiter`, retries, timeouts, dedupliceren op `archive_code`+`identifier`, of aan de opslag van jobstatus/records (blijft versleuteld, live opgehaald, met `checkedAt`; geen lokale zoekindex).

## Acceptance criteria
- Een achtergrondscenario met minimaal tien kandidaatrecords waarbij één of meer (maar niet alle) `Records/Show`-aanroepen kunstmatig falen (via een stub/fake client, zoals het bestaande patroon `FakeArchivesOpenSearchClient` met per-identifier `ArchivesShowOutcome`) resulteert in status `READY` met een antwoord dat uitsluitend gebaseerd is op de overige, succesvol gevalideerde records — niet in `FAILED`.
- Een scenario waarin **alle** `Records/Show`-aanroepen voor de kandidaatrecords van een job mislukken, resulteert nog steeds in status `FAILED` (regressietest voor het bestaande source-outage-gedrag).
- Een kandidaatrecord waarvan de `Show`-aanroep mislukt levert geen feitelijke antwoordzin en geen bronmarkering op, en blokkeert niet dat andere, wel gevalideerde kandidaatrecords in het antwoord verschijnen.
- Wanneer minstens één maar niet alle kandidaatrecords niet verifieerbaar waren, vermeldt de bestaande bewijsbegrenzing-tekst (`PersonSearchAnswer.disclaimer`) dit zichtbaar (met aantal), zonder dat hiervoor een nieuw scherm of nieuwe status wordt geïntroduceerd.
- De synchrone route (2-secondenbudget in `PersonSearchService.submit`) blijft ongewijzigd getest: binnen het budget verschijnt uitsluitend een antwoord bij een op dat moment volledige, terminale uitkomst.
- Er ontstaat geen lokale zoekindex of structurele opslag van kandidaatrecords; elk in het antwoord gebruikt record blijft live opgehaald en behoudt `checkedAt`.
- Automatische tests voor beide scenario's (gedeeltelijk falende versus volledig falende kandidaatrecords) zijn toegevoegd aan de bestaande backend-testsuite (`PersonSearchServiceTest.kt` en/of `PersonSearchAnswerBuilderTest.kt`) en draaien zonder afhankelijkheid van de daadwerkelijke live beschikbaarheid van Open Archieven.

## Aannames
- De exacte formulering van de nieuwe disclaimer-toevoeging (bijv. "1 van de 4 gevonden kandidaten kon niet worden geverifieerd en is buiten beschouwing gelaten.") is niet door de story vastgelegd; de developer stelt een concrete, consistente Nederlandstalige formulering vast binnen de bestaande stijl van `PersonSearchAnswerBuilder.buildDisclaimer`, met het aantal niet-verifieerbare kandidaten als kernelement.
- "Geldig, gevalideerd Show-record" betekent hier: `ArchivesShowOutcome.Success`, ongewijzigd ten opzichte van de bestaande validatie in `RestClientArchivesOpenSearchClient`; er wordt geen aanvullende validatielaag toegevoegd.
- De volgorde van bronnummering (1..n) volgt de volgorde van de succesvolle records in de bestaande `deduped`-lijst, na het uitfilteren van de mislukte kandidaten; niet-verifieerbare kandidaten krijgen geen bronnummer.

Product-Factory-Api-Version: 2
Product-Factory-Product-Id: hkh-autopilot
Product-Factory-Source-Story-Id: 51d0bb9b-eae6-4174-9650-b0ee99da34dd
Product-Factory-Source-Story-Version: 1
Product-Factory-Idempotency-Key: product-factory:hkh-autopilot:story:51d0bb9b-eae6-4174-9650-b0ee99da34dd:v1
Product-Factory-Package-Sha256: 45956c117924a088db70c49b6b1fc2429d1c5e8c9a6c2cc4f7fa426d872f3e89

## Eindsamenvatting

Geen bestaand memory-bestand nodig hier — de relevante tip (phase-JSON conflict) staat al in `.agent-tips.md` en is niet nieuw. Geen memory-update vereist.
