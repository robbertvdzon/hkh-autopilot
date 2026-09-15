# Agenttoegang

Open `/api/auth/agent-login` op dezelfde origin als de frontend, of gebruik
`POST /api/auth/agent-session`, header `X-AI-Access-Token`, JSON `{"email":"toegestane-gebruiker"}`.
De response bevat een normale applicatiesessie; rechten blijven door de applicatie gecontroleerd.

Per omgeving configureer je `AI_ACCESS_TOKEN` (minimaal 32 willekeurige tekens), `AI_ACCESS_EMAILS`
(expliciete bestaande/toegestane identiteiten) en `AI_ACCESS_ALLOWED_ORIGINS` (exacte frontendorigins; voor PR-hostnamen uitsluitend `{pr}` als numeriek gedeelte).
Zonder token staat de ingang uit. Er worden bij aanmelden geen accounts of rollen aangemaakt: het
opgegeven adres moet ook in de bestaande beheerallowlist `HKH_ADMIN_ALLOWED_EMAILS` staan, anders
wordt de sessie geweigerd. Een geslaagde aanmelding levert een gewone beheersessie van een uur;
elke afwijzing (onbekend token, niet-toegestane herkomst of onbekende identiteit) geeft dezelfde
HTTP 401 zonder nadere reden.
De herkomstcontrole geldt ook wanneer de aanmeldpagina via de frontendproxy op dezelfde origin
wordt geopend: `SameOriginRequestFilter` verbergt de `Origin`-header alleen voor de CORS-toetsing
en bewaart de waarde als requestattribuut, dat de aanmeldroute uitleest.
Gebruik op test/acceptatie aparte accounts en tokens. Alleen de testtoken mag als bijvoorbeeld
`HKH_AUTOPILOT__ACCEPTANCE_AGENT_TOKEN` in Agent Runtime komen.
Productietokens, databasecredentials en signing secrets blijven buiten de runtime. Productie-login
mag uitsluitend in een begeleide Codex/Claude-taak na Robberts expliciete toestemming. Onderzoek
standaard via alleen-lezen databasequeries en OpenShift-logs; gebruik de browser voor zichtbaar gedrag.

Tokenwaarden mogen niet in URLs, logs, terminaluitvoer, prompts of screenshots belanden. Voor
browserautomatisering leest een lokaal helperproces de geselecteerde testcredential en plaatst de
sessie in dezelfde browsercontext; geef de ruwe token nooit aan het model terug.

## Begeleid productieonderzoek

Gebruik eerst alleen-lezen databasequeries en OpenShift-logs/status. Voor expliciet geautoriseerde
productielogin staat in `robberts-infrastructure/tools/copy-agent-access-token.py` een helper die de
token alleen naar het macOS-klembord schrijft. Plak rechtstreeks in het gemaskeerde loginveld en
wis het klembord na gebruik. De applicatie-ingang verleent uitsluitend bestaande rechten.
