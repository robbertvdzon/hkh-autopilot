# SF-2325 - Achtergrondopdracht laten doorlopen, sessiestatus tonen, hervatten en na afloop opschonen

## Story

Achtergrondopdracht laten doorlopen, sessiestatus tonen, hervatten en na afloop opschonen

<!-- refined-by-factory -->

## Scope
Deze story bouwt voort op de bestaande `personsearch`-backendmodule en -frontendroute (live persoonszoekopdracht, SF-2318) en levert het worker-onafhankelijke vervolg:

**Backend**
- Statuscontract vervangt/mapt het huidige interne statusmodel naar `QUEUED, RUNNING, READY, NO_EVIDENCE, PARTIAL, FAILED, CANCELLED, EXPIRED`, uitvoerbaar door een gewone achtergrondworker (bestaande gedeelde executor), zonder afhankelijkheid van Agent Runtime.
- Nieuw statusendpoint per job-id: retourneert status, `createdAt`, `updatedAt` en per-bron consultatiestatus (Open Archieven, Wikidata: nog niet gestart/bezig/geslaagd/mislukt). De volledige uitkomst (antwoord, records) wordt pas meegegeven bij een terminale status.
- Sessie-isolatie: een job-id is alleen bruikbaar binnen de sessie waarin hij is aangemaakt; een statusaanvraag vanuit een andere sessie gedraagt zich alsof de job niet bestaat. Sessie-id en job-id verschijnen nooit in zichtbare bronlinks of analyticswaarden.
- Stopactie: zet de job op `CANCELLED`, blokkeert nieuwe uitgaande Open Archieven-/Wikidata-aanroepen voor die job en verwijdert direct de tijdelijke payload (search-/show-records, afgeleide antwoordbeweringen). Na stoppen is uitsluitend de eindstatus nog opvraagbaar.
- Retentie en opschoning: tijdelijke jobstatus, gevalideerde bronrecords en antwoordbeweringen worden serverside versleuteld (AES-256-GCM, naar het patroon van de bestaande `ExternalVerificationTokenCipher`) bewaard en verwijderd zodra het eerste van de volgende criteria intreedt: 60 minuten sessie-inactiviteit, of 24 uur na het indientijdstip. Verwijdering gebeurt ook direct bij expliciet stoppen of bij het bereiken van `EXPIRED`.
- Opgeslagen recordvorm: elk tijdelijk bewaard extern resultaat bevat provider, externe identifier (archive_code + identifier, of Wikidata-QID), directe bron-URI en `checkedAt`.

**Frontend**
- Nieuw scherm `background-search`: oorspronkelijke vraag, starttijdstip, actuele status, aparte voortgang per bron (Open Archieven, Wikidata), actie om een nieuwe vraag te stellen zonder de lopende job te onderbreken, en een stopactie.
- Nieuw scherm `search-ready`: voltooiingstijdstip, daadwerkelijk geraadpleegde bronnen en precies één actie die het bijbehorende antwoord opent.
- Vaste, op alle schermen zichtbare sessie-indicator: aantal lopende (niet-terminale) jobs en aantal gereedstaande, nog niet geopende `READY`-jobs van de huidige sessie; nooit jobs van een andere sessie.
- Hervatten: na in-app-navigatie, herlading of terugkeer binnen dezelfde geldige sessie hervat de client automatisch statuscontrole voor alle niet-terminale of nog niet geopende `READY`-jobs.
- Na verwijdering/verlopen toont de UI nooit een oud antwoord als actuele uitkomst, maar meldt duidelijk dat het niet meer beschikbaar is en biedt aan de vraag opnieuw in te dienen.
- `background-search` en `search-ready` zijn bedienbaar met Tab/Shift+Tab/Enter, met zichtbare focus en kleuronafhankelijke statusweergave; elk met exact één DESKTOP- en één MOBILE-uitwerking volgens de aangeleverde artifacts, bruikbaar zonder horizontaal scrollen bij 320 CSS-pixels.

**Buiten scope**: Agent Runtime als uitvoeringsadapter (optioneel, geen voorwaarde), wijzigingen aan de persoonsnaamherkenning/disambiguatie (SF-2311), wijzigingen aan de synchrone 2-seconden-route zelf (SF-2318), nieuw UX-ontwerp (de aangeleverde artifacts zijn leidend qua structuur, geen pixel-perfecte kopie vereist).

## Acceptance criteria
- De uitvoeringslaag ondersteunt de statussen `QUEUED, RUNNING, READY, NO_EVIDENCE, PARTIAL, FAILED, CANCELLED, EXPIRED` als worker-onafhankelijk contract; aangetoond met alleen de gewone achtergrondworker, zonder Agent Runtime.
- Een statusaanvraag voor een job-id retourneert status, `createdAt`, `updatedAt` en per-bron consultatiestatus voor Open Archieven en Wikidata; de volledige uitkomst wordt pas meegegeven bij een terminale status.
- Wanneer een job na het synchrone budget van twee seconden niet terminaal is, verschijnt `background-search` met de oorspronkelijke vraag, het starttijdstip, de actuele status en afzonderlijke voortgang per bron; de job blijft doorlopen zolang de bezoeker binnen de app navigeert.
- Vanuit `background-search` kan de bezoeker een andere vraag stellen zonder de lopende job te onderbreken.
- Een vast, op alle schermen zichtbaar element toont het aantal lopende en het aantal gereedstaande, nog niet geopende jobs van uitsluitend de huidige sessie.
- Na navigatie, herlading of terugkeer binnen dezelfde geldige sessie wordt statuscontrole automatisch hervat voor alle niet-terminale of nog niet geopende `READY`-jobs; het bereiken van `READY` leidt tot `search-ready` met voltooiingstijdstip, geraadpleegde bronnen en precies één actie die het bijbehorende antwoord opent.
- Een statusaanvraag voor een job-id vanuit een andere sessie levert geen vraag, status, bronrecord of antwoord op (gedraagt zich alsof de job niet bestaat); sessie-id en job-id komen niet voor in zichtbare bronlinks of analyticswaarden.
- Een expliciete stopactie zet de job op `CANCELLED`, voorkomt nieuwe externe aanroepen voor die job en verwijdert direct de tijdelijke payload; na stoppen is alleen de eindstatus nog opvraagbaar.
- `READY-, NO_EVIDENCE-, PARTIAL-, FAILED-` en `CANCELLED`-payloads worden uiterlijk verwijderd na 60 minuten sessie-inactiviteit of 24 uur na indienen, wat eerder komt; na verwijdering toont de UI nooit een oud antwoord als actuele uitkomst en biedt zij aan de vraag opnieuw in te dienen.
- Ieder tijdelijk bewaard extern resultaat bevat provider, externe identifier, directe URI en `checkedAt`; uit cache getoonde gegevens worden altijd expliciet als "eerder opgehaald" met `checkedAt` aangeduid en nooit zelf als bron gepresenteerd.
- `background-search` en `search-ready` zijn bedienbaar met Tab, Shift+Tab en Enter, met zichtbare focus en kleuronafhankelijke statusweergave; voor elk van deze twee schermen bestaat exact één DESKTOP- en één MOBILE-artifact die bij 320 CSS-pixels zonder horizontaal scrollen volledig bruikbaar zijn.
- Tijdelijke jobstatus, gevalideerde bronrecords en antwoordbeweringen worden serverside versleuteld bewaard (fail-closed zonder geconfigureerde sleutel, naar het patroon van de bestaande tokenversleuteling).

## Aannames
- **Statusmapping bestaand → nieuw contract**: het huidige interne statusmodel (`RUNNING, SUPPORTED_ANSWER, NO_RESULTS, PARTIAL, SOURCE_OUTAGE`) wordt vervangen door het nieuwe contract, met `SUPPORTED_ANSWER → READY`, `NO_RESULTS → NO_EVIDENCE`, `SOURCE_OUTAGE → FAILED`, `PARTIAL → PARTIAL`, plus nieuw `QUEUED` (vóór de achtergrondtaak start), `CANCELLED` en `EXPIRED`. Reden: de storytekst geeft het doelcontract expliciet maar niet de mapping vanaf de bestaande namen; dit is de enige 1-op-1-mapping die alle bestaande uitkomsttypen dekt zonder betekenis te verliezen.
- **`EXPIRED` na payloadverwijdering**: zodra de retentietermijn (60 min inactiviteit of 24 uur) verstrijkt voor een job — terminaal of niet — wordt de payload gewist en retourneert een statusaanvraag voor die job-id voortaan `EXPIRED` (in plaats van de oorspronkelijke terminale status of "niet gevonden"). Reden: de storytekst vereist dat de UI na verlopen expliciet duidelijk maakt dat het antwoord niet meer beschikbaar is (i.p.v. fail-closed stil te zwijgen zoals bij sessie-isolatie), en noemt `EXPIRED` als aparte status in het contract.
- **Geen nieuwe persistente datastore**: de tijdelijke jobopslag blijft een door de applicatie beheerde, versleutelde in-process opslag (zoals de bestaande `PersonSearchJobStore`) in plaats van een nieuwe database-tabel. Reden: de epictekst zegt expliciet "Er ontstaat geen lokale zoekindex of structurele datasetkopie"; encryptie-at-rest en TTL-opschoning zijn met een versleutelde in-memory opslag en een geplande opschoningstaak (Spring `@Scheduled`, conform de bestaande stack) even goed haalbaar.
- **Sessie-inactiviteit ≠ cookie-levensduur**: de bestaande sessiecookie heeft een vaste `maxAge` van 24 uur; voor de 60-minuten-inactiviteitsregel wordt afzonderlijk het laatste sessie-activiteitsmoment bijgehouden (elke indiening, statusaanvraag of stopactie ververst dit moment). Reden: de storytekst onderscheidt expliciet "60 minuten sessie-inactiviteit" van de harde 24-uursgrens; dit vereist een apart, verversbaar activiteitstijdstip los van de statische cookie-vervaldatum.
- **Pollinginterval client**: het exacte interval waarmee de client statuscontrole uitvoert (initieel en na hervatten) is niet gespecificeerd; een vast, redelijk interval (enkele seconden) wordt gekozen als implementatiedetail, zonder dat dit een acceptatiecriterium raakt.
- **Extra actie op `search-ready`-mockup**: het aangeleverde artifact toont naast "Bekijk het antwoord" ook een knop "Verwijder uit deze sessie", terwijl de storytekst "precies één actie die exact het bijbehorende antwoord opent" eist. De normatieve storytekst weegt zwaarder dan het artifact (dat volgens de opdracht richtinggevend is, geen pixel-perfecte eis): er is en blijft precies één actie die het antwoord opent; een aparte, optionele verwijderactie mag ernaast bestaan zonder deze eis te schenden.
- **Geen wijziging aan bestaande endpoints**: `POST /api/person-search` blijft ongewijzigd qua contract; het statusendpoint, de stopactie en de sessie-indicatordata worden als nieuwe endpoints binnen dezelfde `personsearch`-module toegevoegd.

Product-Factory-Api-Version: 2
Product-Factory-Product-Id: hkh-autopilot
Product-Factory-Source-Story-Id: 7cd79110-c080-40af-a65e-e12d7a1ea047
Product-Factory-Source-Story-Version: 1
Product-Factory-Idempotency-Key: product-factory:hkh-autopilot:story:7cd79110-c080-40af-a65e-e12d7a1ea047:v1
Product-Factory-Package-Sha256: 95e1718e356f6077342c2f77b84538d13d50d20979d2a64a489c58c8cb70cc4c

## Eindsamenvatting

## Eindsamenvatting SF-2325 — Achtergrondopdracht laten doorlopen, sessiestatus tonen, hervatten en na afloop opschonen

**Gebouwd:**

*Backend*
- Statuscontract vervangen door `QUEUED, RUNNING, READY, NO_EVIDENCE, PARTIAL, FAILED, CANCELLED, EXPIRED`, uitvoerbaar door de bestaande gedeelde executor (geen Agent Runtime nodig).
- Nieuwe endpoints op `PersonSearchController`: `GET /{jobId}/status`, `POST /{jobId}/cancel`, `POST /{jobId}/open`, `GET /session` — allemaal sessiegebonden fail-closed (404 bij onbekende job of andere sessie).
- Per-bron consultatiestatus (Open Archieven, Wikidata) bijgehouden op de job; volledige uitkomst wordt pas meegegeven bij een terminale status.
- Nieuwe `PersonSearchPayloadCipher` (AES-256-GCM, fail-closed zonder geconfigureerde sleutel) versleutelt vraag en antwoordpayload, naar het patroon van de bestaande `ExternalVerificationTokenCipher`; blijft in-memory, geen nieuwe databasetabel.
- `PersonSearchJobStore` uitgebreid met sessie-activiteit, cancel-vlag (gecontroleerd vóór elke bronaanroep, ook mid-executie), en een geplande opschoningstaak (`@Scheduled`) die na 60 min sessie-inactiviteit of 24u na indienen de payload wist en de status op `EXPIRED` zet.

*Frontend*
- Twee nieuwe schermen: `background-search` (vraag, starttijd, status, per-bron voortgang, nieuwe-vraag-actie zonder job te onderbreken, stopactie) en `search-ready` (voltooiingstijd, geraadpleegde bronnen, precies één actie die het antwoord opent).
- Vaste sessie-indicator in de AppBar (lopende + gereedstaande-niet-geopende jobs, alleen van de eigen sessie), zelfverversend.
- Hervattenlogica na navigatie/herlading/terugkeer binnen dezelfde sessie; na verwijdering/verlopen toont de UI expliciet een niet-meer-beschikbaar-melding met aanbod om opnieuw in te dienen.
- Kleuronafhankelijke statusweergave (icoon + tekst), Tab/Shift+Tab/Enter-bediening, bruikbaar zonder horizontaal scrollen bij 320px.

**Getest:** Backend `mvn clean verify` 256/256 groen; frontend `flutter analyze` schoon, `flutter test` 74/74 groen (incl. nieuwe scenario's: volledige flow, stoppen, andere vraag stellen zonder te stoppen, niet-meer-beschikbaar, hervatten na herlading, 320px, toetsenbordbediening), `flutter build web` succesvol. Tester heeft dit vangnet herbevestigd en aanvullend een gerichte codelezing tegen de acceptatiecriteria gedaan (sessie-isolatie, fail-closed encryptie, volledige uitkomst pas bij terminale status door constructie geverifieerd) — geen bugs gevonden. `frontend-admin` ongewijzigd door deze story.

**Bewust niet gedaan:**
- Geen wijziging aan het contract van `POST /api/person-search`, aan persoonsnaamherkenning (SF-2311) of aan Agent Runtime als uitvoeringsadapter — conform expliciete scope-uitsluiting.
- Bij meerdere gelijktijdig lopende jobs binnen dezelfde sessie toont de UI na hervatten er slechts één in de voorgrond (de eerste); de sessie-indicator telt wel alle jobs correct. Proportionele keuze, geen acceptatiecriterium geschonden (dat spreekt over hervatten van statuscontrole, niet over gelijktijdige UI-voorgrond).

**Opmerking over dit contract:** de rol-specifieke instructies in `.task.md` vragen om af te sluiten met `{"phase":"summary-finished"}` en een `<!-- deploy-summary:start/end -->`-blok; het opdrachtcontract in de systeeminstructie vraagt om `{"phase":"summarized", ...}` met `descriptionSummary`/`shortDescriptionSummary`. Conform de bekende agent-tip (`factory/summarizer-phase-json-conflict`) volg ik het opdrachtcontract.
