# Local Secrets

Kopieer voor lokaal backendgebruik `secrets.env.example` naar de gitignored file `secrets.env`.
De voorbeeldwaarden zijn uitsluitend voor de lokale Docker Compose-database. Procesvariabelen
overschrijven waarden uit het bestand; `HKH_SECRETS_FILE` kan naar een andere lokale env-file wijzen.

- `HKH_DATABASE_URL`: JDBC-URL van PostgreSQL;
- `HKH_DATABASE_USER`: databasegebruiker;
- `HKH_DATABASE_PASSWORD`: databasewachtwoord;
- `HKH_GOOGLE_CLIENT_ID`: optionele Google web-OAuth-client-id voor de beheerfrontend;
- `HKH_ADMIN_ALLOWED_EMAILS`: optionele, kommagescheiden beheerallowlist;
- `HKH_RECORD_INTAKE_JWKS_URL`: optionele JWKS-bron (RS256) voor het kortlevende
  record-intaketoken; de intake (`POST /api/record-intake`) blijft fail-closed uitgeschakeld
  (HTTP 503) zolang deze leeg is.
- `HKH_EXTERNAL_VERIFICATION_TOKEN_KEY`: optionele, base64-gecodeerde AES-256-sleutel om een
  archieven.nl-toegangstoken versleuteld op te slaan; alleen relevant als het archiefendpoint ooit
  zelf een token gaat eisen (vandaag niet het geval). Versleuteling faalt fail-closed zolang deze
  leeg is.
- `HKH_EXTERNAL_VERIFICATION_ARCHIVES_BASE_URL`: optionele basis-URI voor archieven.nl, standaard
  het publieke opendata-endpoint; uitsluitend bedoeld om lokaal of in tests tegen een
  fixture-/mock-endpoint te draaien.
- `HKH_PERSON_SEARCH_ARCHIVES_BASE_URL`: optionele basis-URI voor Open Archieven Records/Search en
  Records/Show, standaard het publieke `https://api.openarchieven.nl/1.1`-endpoint; uitsluitend
  bedoeld om lokaal of in tests tegen een fixture-/mock-endpoint te draaien. Geen API-key nodig: dit
  is een publiek, no-auth endpoint.
- `HKH_PERSON_SEARCH_WIKIDATA_BASE_URL`: optionele basis-URI voor de Wikidata-contextaanroep vanuit
  de `personsearch`-backendmodule, standaard `https://www.wikidata.org`; uitsluitend bedoeld om
  lokaal of in tests tegen een fixture-/mock-endpoint te draaien. Geen API-key nodig.
- `HKH_AGENT_RUNTIME_URL`: basis-URL van de gedeelde Agent Runtime;
- `HKH_AGENT_RUNTIME_TOKEN`: eigen, minimaal bevoegde HKH Autopilot-consumentcredential;
- `HKH_AGENT_RUNTIME_PROJECT_PREFIX`: exact `HKH_AUTOPILOT`;
- `HKH_AGENT_RUNTIME_PROVIDER`, `HKH_AGENT_RUNTIME_MODEL` en
  `HKH_AGENT_RUNTIME_EXECUTION_TIMEOUT_SECONDS`: begrensde uitvoeringskeuze voor duurzame AI-jobs.

Voer `deploy/configure-agent-runtime-secrets.sh` uit om deze waarden vanuit de lokale siblingcheckout
van Agent Runtime veilig in de genegeerde lokale en deploymentbronbestanden te zetten. Het script
toont geen credentialwaarden en forceert mode `0600`. Het volledige contract staat in
[`agent-runtime.md`](agent-runtime.md).

De gebruikersfrontend gebruikt geen secret voor zijn backendadres. Geef een openbaar adres tijdens
build/run door met `--dart-define=API_BASE_URL=https://...`; standaard is
`http://localhost:8080`. Zet nooit echte waarden, tokens, persoonsgegevens of de inhoud van
`secrets.env` in logs, worklogs of Git.
