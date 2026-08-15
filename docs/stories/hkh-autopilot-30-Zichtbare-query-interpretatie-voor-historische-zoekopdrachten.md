# hkh-autopilot-30 - Zichtbare query-interpretatie voor historische zoekopdrachten

## Story

Zichtbare query-interpretatie voor historische zoekopdrachten

<!-- refined-by-factory -->

## Scope

Breid de bestaande historische zoekroute uit met zichtbare querysemantiek voor Open Archieven.

De backend legt per uitgevoerde Open Archieven-aanvraag vast welke ondersteunde semantische bronvelden daadwerkelijk in het adapterverzoek zijn gebruikt, bijvoorbeeld `name`, `eventplace` of `birthplace`. De publieke zoekrespons geeft deze informatie per Open Archieven-bron door aan de frontend.

De frontend toont deze informatie tekstueel naast de resultaten en gebruikt herkenbare Nederlandse labels met de daadwerkelijke providerparameter erbij. Niet-semantische parameters zoals paginering, rate limiting of een bronfilter worden niet als inhoudelijke interpretatie gepresenteerd.

Wanneer geen Open Archieven-verzoek is uitgevoerd of de gebruikte semantiek niet betrouwbaar uit het adapterverzoek kan worden vastgesteld, toont de frontend een vaste neutrale melding zonder interpretatie.

## Acceptance criteria

- Bij iedere uitgevoerde vrije-tekstzoekopdracht toont de frontend de querysemantiek van het werkelijk verzonden Open Archieven-verzoek.
- De zichtbare informatie benoemt uitsluitend semantische bronvelden die daadwerkelijk in het adapterverzoek zijn gebruikt; de tekst wordt niet afgeleid uit de zoekterm, resultaatmetadata, titel, URL of een algemene aanname over Heemskerk.
- Voor `Heemskerk` worden alleen providerparameters gebruikt die in het Open Archieven-contract zijn toegestaan. `Heemskerk` wordt niet automatisch als plaats of historische betekenis gepresenteerd.
- De informatie is per bron beschikbaar, zodat een Open Archieven-interpretatie niet wordt voorgesteld als interpretatie van een andere bron.
- Een naaminterpretatie, plaatsinterpretatie en onbepaalde interpretatie zijn geautomatiseerd getest.
- De tests leggen per interpretatie zowel het uitgaande Open Archieven-verzoek als de zichtbare interpretatie vast en controleren dat beide overeenkomen.
- Wanneer de semantiek niet kan worden vastgesteld, toont de frontend een neutrale tekstuele status en geen naam-, plaats- of andere veldclaim.
- De informatie is tekstueel beschikbaar naast de resultaten en is niet uitsluitend afhankelijk van kleur, icoon of positionering.
- Bestaande resultaten, bronstatussen, rechten-/privacyweergave, paginering, caching, rate limiting en foutafhandeling blijven behouden.
- Er wordt geen zoekgeschiedenis, ruwe providerrespons of nieuwe persistente opslag toegevoegd.

## Aannames

- Het adapterverzoek is de enige bron van waarheid voor de zichtbare interpretatie.
- Een vrije zoekterm die werkelijk als `name` wordt verzonden, wordt als naamveld getoond; hetzelfde geldt voor daadwerkelijk gebruikte plaatsvelden zoals `eventplace` of `birthplace`.
- Een bronfilter of technische parameter wordt niet als persoons-, plaats- of gebeurtenisinterpretatie aangemerkt.
- Als meerdere semantische bronvelden werkelijk worden verzonden, worden ze allemaal tekstueel vermeld.
- De bestaande publieke route en het bestaande genormaliseerde zoekcontract worden uitgebreid; er komt geen nieuwe zoekroute.
- De wijziging vereist geen database-, secret-, deployment- of infrastructuurwijziging.

## Eindsamenvatting

Opgeleverd:

- Backend en frontend geven per bron door welke Open Archieven-velden echt zijn gebruikt, zoals `name` en `eventplace`.
- Technische parameters worden niet als zoekinterpretatie getoond.
- Bij ontbrekende informatie of een Europeana-zoekopdracht verschijnt een neutrale melding.
- Naam-, plaats-, neutrale en Europeana-scenario’s zijn getest.
- Gerichte backendtests: 46 geslaagd. Frontendtests: 83 geslaagd; beheerfrontend: 38 geslaagd. Analyse en webbuild zijn geslaagd.
- Volledige backend-verify kon niet afronden door ontbrekende Docker/Testcontainers-ondersteuning; 0 test failures, maar 7 infrastructuurgerelateerde errors.
- Er is geen zoekgeschiedenis, ruwe bronopslag of nieuwe persistente opslag toegevoegd.

<!-- deploy-summary:start -->
Bij historisch zoeken zie je nu beter welke informatie voor de zoekopdracht is gebruikt. Als dat niet betrouwbaar kan worden vastgesteld, toont de app een neutrale melding.
<!-- deploy-summary:end -->

Procesnotitie: de rolhandleiding noemt `summary-finished`, maar het opdrachtcontract vraagt `summarized`; daarom volg ik het opdrachtcontract.
