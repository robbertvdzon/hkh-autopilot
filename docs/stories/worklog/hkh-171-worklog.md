# Worklog hkh-171 — Publieke Open Archieven-resultaatkaart koppelen aan het contract

## Stappenplan

- [x] Factory-context, story en bestaande code/teststructuur gelezen.
- [x] Bestaande historische resultaatkaart en veilige externe link aanpassen.
- [x] Contract- en widgettests toevoegen voor geldige en ongeldige resultaten.
- [x] Gerichte tests en het volledige factory-vangnet uitvoeren.
- [x] Self-review uitvoeren en worklog afronden.

## Uitvoering

Developer-run gestart. De bestaande contractmodellen en veiligheidsgrens worden eerst geïnventariseerd
voordat de kaart wordt aangepast; wijzigingen blijven beperkt tot het publieke historische zoekresultaat.

## Gedaan

- De JSON-mapping valideert voor Open Archieven `source_name`, `stable_identifier` en
  `original_source_url`, inclusief absolute HTTP(S)-URI en consistentie met legacy-identiteitsvelden.
- Ongeldige stabiele identiteit/bron-URI resulteert in geen publieke kaart en geen externe link.
- De kaart toont uitsluitend expliciete inhoudelijke metadata bij `ALLOWED` metadatarechten en `CLEAR`
  privacystatus; lege/onbekende statussen worden per veld als `Onbekend` getoond.
- Titel en primaire beschrijving worden zonder verzonnen fallback weergegeven; datering, plaats,
  bronhouder, persoon, gebeurtenis, identifier en UTC-ophaaldatum komen rechtstreeks uit het contract.
- De externe link gebruikt de contract-URI, is semantisch een link, heeft een zichtbaar nieuw-tabblad-
  label en blijft toetsenbordbedienbaar. De bestaande webopener gebruikt `noopener`.
- `hkh171_historical_result_card_test.dart` dekt een volledige fixture, ontbrekende statussen/inhoud,
  beschrijvingsfallback, ongeldige identiteit, linklabel en activatie. De bestaande fixture is aangevuld
  met de nu verplichte Open Archieven-contractvelden.

## Verificatie

- `mvn -B --no-transfer-progress clean verify` — groen, 340 tests, 0 failures/errors.
- `frontend/flutter analyze` — groen.
- `frontend/flutter test` — groen, 79 tests, 0 failures/errors.
- `frontend/flutter build web` — groen.
- `frontend-admin/flutter analyze` — groen.
- `frontend-admin/flutter test` — groen, 35 tests, 0 failures/errors.

Self-review tegen de refined story en factory-conventies uitgevoerd; geen open punten of scope-overschrijding
gevonden. Wijzigingen zijn bewust uncommitted gelaten voor de factory-handover.
