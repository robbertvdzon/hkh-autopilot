# Local Secrets

Kopieer voor lokaal backendgebruik `secrets.env.example` naar de gitignored file `secrets.env`.
De voorbeeldwaarden zijn uitsluitend voor de lokale Docker Compose-database. Procesvariabelen
overschrijven waarden uit het bestand; `HKH_SECRETS_FILE` kan naar een andere lokale env-file wijzen.

- `HKH_DATABASE_URL`: JDBC-URL van PostgreSQL;
- `HKH_DATABASE_USER`: databasegebruiker;
- `HKH_DATABASE_PASSWORD`: databasewachtwoord;
- `HKH_GOOGLE_CLIENT_ID`: optionele Google web-OAuth-client-id voor de beheerfrontend;
- `HKH_ADMIN_ALLOWED_EMAILS`: optionele, kommagescheiden beheerallowlist.

De gebruikersfrontend gebruikt geen secret voor zijn backendadres. Geef een openbaar adres tijdens
build/run door met `--dart-define=API_BASE_URL=https://...`; standaard is
`http://localhost:8080`. Zet nooit echte waarden, tokens, persoonsgegevens of de inhoud van
`secrets.env` in logs, worklogs of Git.
