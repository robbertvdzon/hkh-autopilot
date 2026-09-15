# hkh-215 - Onderzoek, herstel en rol uit

## Status

- Rol: developer
- Onderzochte checkout-head: `d7356f2` (`ai/hkh-208`, merge van `origin/main` in de storybranch);
  eerdere rondes op `fc9b40d`, `e460e13`, `0843de2`, `0c708ba`, `82405ee`
- Live alleen-lezende controles: 2026-09-14 13:05-13:14 UTC en hercontroles 14:08-14:09, 14:19 en
  18:08-18:10 UTC op `https://hkh-autopilot-acceptance.vdzonsoftware.nl`
- Scope sinds de correctie van 2026-09-14 (issue comment 3967): alleen deel A van de acceptance
  criteria hoort bij deze subtaak. Uitrol naar acceptatie en de live controles daarna (deel B)
  gebeuren na de merge- en deploy-subtaak via CI en de bestaande Argo CD Application
  `hkh-autopilot-acceptance` uit `robberts-infrastructure`.

## Oorzaakonderzoek

Alle bevindingen hieronder zijn vastgelegd zonder secretwaarden, tokens, cookies of volledige
responsepayloads te bewaren.

- Probleembaseline `e1a4994` bevatte de nieuwe topicroute, maar het toenmalige acceptatiesecret nog
  niet de vereiste `HKH_EUROPEANA_API_KEY`. De adapter behandelt een ontbrekende key bewust als
  `OUTAGE`. Commit `c2cf1b2` heeft vervolgens een opnieuw verzegeld, acceptatiespecifiek secret met
  deze key toegevoegd; `78da801` heeft de checksumannotation bijgewerkt naar exact de SHA-256 van
  dat versleutelde manifest.
- Live geeft de topicroute niet langer structureel `OUTAGE`: de canonieke vraag gaf vijfmaal
  achtereen `EMPTY`, en de smallere controleterm `watersnood` gaf `READY` met één geldig
  Europeana-record, een niet-lege provider en een absolute Europeana-bronlink. Omdat een lege key in
  deze release vóór de HTTP-call direct `OUTAGE` geeft, bewijst dit veilig dat de actieve Pod de
  opnieuw verzegelde key heeft geladen. De code weigert voortaan bovendien expliciet de gedeelde
  testkey `api2demo`; na uitrol bewijst elke geslaagde Europeana-call daardoor tevens dat die key niet
  actief is.
- Externe bereikbaarheid is afzonderlijk vastgesteld: de publieke Europeana-host beantwoordde een
  ongeauthenticeerde request correct met `401` en Wikidata met `200`. De acceptance-Pod bereikte
  Europeana aantoonbaar via het bovengenoemde `READY`-antwoord en Wikidata via vijf opeenvolgende
  `READY`-antwoorden voor Kasteel Assumburg (`Q1967073`). Daarmee zijn DNS, TLS, egress, proxy en de
  gedeelde gzip/User-Agent-clientconfiguratie niet de actuele structurele oorzaak. Er zijn geen
  base-URL-overrides in de acceptatiespecifieke secretketen aangetroffen.
- Het resterende canonieke `EMPTY`-resultaat had een afzonderlijke, reproduceerbare queryoorzaak:
  Europeana leverde voor `watersnood van 1916 AND Heemskerk` nul records, maar voor
  `watersnood 1916 AND Heemskerk` live één geldig record (`Watersnood 1916`). Het Nederlandse
  vulwoord `van` werd door de bron als verplichte zoekterm behandeld en maakte de query te strikt.
- Het rolloutmechanisme is afzonderlijk nagetrokken. De acceptance-overlay wordt door de
  buildworkflow bijgewerkt en door de bestaande Argo CD Application `hkh-autopilot-acceptance`
  (beheerd in `robberts-infrastructure`, volgt `main` en synchroniseert
  `deploy/overlays/acceptance`) automatisch toegepast. Deze repository heeft daarvoor dus geen eigen
  Application-manifest nodig; het in een eerdere ronde toegevoegde
  `deploy/argocd/application-acceptance.yaml` was overbodig en is weer verwijderd. De ontbrekende
  rollout op het moment van onderzoek verklaart zich uit het nog niet gemergede storybranch-werk,
  niet uit een ontbrekende Application.

## Wijzigingen

- De Europeana-query verwijdert uitsluitend `van` direct vóór een viercijferig jaartal en behoudt
  altijd de `AND Heemskerk`-beperking. De canonieke term wordt zo deterministisch
  `watersnood 1916 AND Heemskerk`, zonder betekenisdragende lidwoorden of naamdelen te verliezen.
  Reviewerregressies bewijzen expliciet dat `De Stijl` en `Vincent van Gogh` intact blijven en niet
  dezelfde cachekey krijgen als respectievelijk `Stijl` en `Vincent Gogh`.
- De Europeana-adapter weigert een lege key én `api2demo` fail-closed, zonder keymateriaal te loggen.
- Recordvalidatie accepteert alleen absolute HTTP(S)-bronlinks en valt van een ongeldige of relatieve
  `edmIsShownAt` terug op een geldige absolute `guid`.
- De verifier legde een bestaande race in de persoonszoekroute bloot: een synchrone `READY`-respons
  kon terugkeren voordat de gekoppelde `whenComplete`-stap de terminale job had opgeslagen. Een
  direct daaropvolgende `open` kon daardoor door een late opslag worden teruggedraaid. De service
  wacht nu binnen hetzelfde tweesecondenbudget op de afhankelijke persistentiestap; na een timeout
  blijft die stap gewoon op de bestaande executor doorlopen.
- Rechtenmapping dekt nu naast Public Domain Mark ook CC0/Zero als `Publiek domein`, alle
  CC BY-SA-versies als `CC BY-SA`, RightsStatements In Copyright en legacy Europeana
  rights-reserved-URL's als `Rechten voorbehouden`, en onbekend/ontbrekend als
  `Rechten onbekend`.
- `deploy/argocd/application-acceptance.yaml` is verwijderd en `deploy/README.md` verwijst nu naar
  de bestaande, extern beheerde Application `hkh-autopilot-acceptance` met alleen-lezende
  controlecommando's.
- Het Context-blok op `topic-results` heet nu `Context (Wikidata)` en draagt een expliciete
  bronmarkering ("Bron: Wikidata · alleen ter duiding; geen archiefbewijs specifiek voor
  Heemskerk"), conform de normatieve storytekst en de richtinggevende UX-afbeelding. Zonder die
  markering kon het blok als bewijs voor een relatie met Heemskerk worden gelezen.
- Een cachehit presenteerde zichzelf als actuele raadpleging: `checkedAt` werd bij elk antwoord op
  "nu" gezet, ook wanneer het antwoord uit de TTL-cache kwam. De cache bewaart nu het moment van de
  geslaagde Europeana-raadpleging zelf (`EuropeanaConsultation`), zodat een gecachet antwoord nooit
  als nieuw geraadpleegd wordt getoond.
- De checksumlogica staat centraal in `deploy/update-runtime-secret-checksums.sh`; de buildworkflow
  gebruikt dit script. `deploy/verify-runtime-secret-rollout.sh` controleert de actuele checksums,
  rendert de acceptance-overlay en simuleert geïsoleerd dat een secret-only wijziging de
  Pod-templatechecksum verandert. Deze check is opgenomen in het Factory-verificatievangnet.

## Verificatie

- Gerichte topicsearch-tests: 32 tests, 0 failures/errors.
- Reviewerfix voor semantisch veilige querynormalisatie: 29 gerichte client-, mapper- en
  servicetests, 0 failures/errors; de serviceklasse bevat nu 9 tests inclusief afzonderlijke
  cachekeys voor `De Stijl`/`Stijl` en `Vincent van Gogh`/`Vincent Gogh`.
- Gerichte regressiecontrole van de persoonszoek-service en -controller na de verifierbevinding:
  29 tests, 0 failures/errors; de sessie-indicator/open-test is groen.
- Volledige backend-`clean verify` na de reviewerfix: 329 tests gevonden, 0 failures/errors,
  `BUILD SUCCESS` en een
  succesvol gerepackagede applicatie-JAR. De 19 tests in vijf bestaande Testcontainers-klassen
  detecteren met `disabledWithoutDocker` expliciet of de uitvoeromgeving Docker aanbiedt: in deze
  Runtime zonder Docker zijn ze overgeslagen; in CI en lokale omgevingen met Docker blijven ze
  ongewijzigd als echte PostgreSQL-integratietests draaien.
- Frontend: `flutter analyze` zonder issues, 125 tests groen en webbuild geslaagd.
- Frontend-admin: `flutter analyze` zonder issues en 22 tests groen.
- Checksum-/rolloutsimulatie: geslaagd; een wijziging van uitsluitend het versleutelde
  acceptatiemanifest leverde een andere, exact overeenkomende Pod-templatechecksum op.
- Live acceptance vóór uitrol van deze branchwijzigingen:
  - vijf keer canonieke topicvraag: geen `OUTAGE`, maar `EMPTY` (querydefect gereproduceerd);
  - vijf keer genormaliseerde equivalentterm `watersnood 1916`: telkens `READY`, één geldig record
    met titel, niet-lege provider en absolute HTTP(S)-bronlink;
  - vijf keer Kasteel Assumburg: `READY`, telkens `Q1967073`;
  - Open Archieven-regressie Nicolaas Jacobus Sinnige (1878): `READY`, één bron met absolute link;
  - actuator health: `UP`.
- Live hercontrole op 2026-09-14 14:08-14:09 UTC bevestigde via `/api/version` dat acceptatie nog
  release `sha-e1a4994` draait. Vijf canonieke topicvragen gaven opnieuw `EMPTY`; vijf aanroepen met
  `watersnood 1916` gaven ieder `READY` met exact één record, niet-lege titel en provider en een
  absolute HTTP(S)-bronlink. Na één afzonderlijke tijdelijke `OUTAGE` gaven vijf opeenvolgende
  plekcontroles `READY` met Assumburg `Q1967073`. Dit is bewust alleen als pre-deploybewijs
  aangemerkt en niet als bewijs dat de branchfix actief is.
- De laatste onafhankelijke hercontrole op 2026-09-14 14:19 UTC bevestigde opnieuw release
  `sha-e1a4994`: vijf canonieke topicvragen gaven `EMPTY`; vijf plekcontroles gaven `READY` met
  Assumburg `Q1967073`; de Open Archieven-regressie gaf `READY` met één bron en één absolute link.
  Ook dit is uitsluitend pre-deploybewijs.
- De hercontrole op 2026-09-14 18:08-18:10 UTC, na reviewercomment 3951, bevestigde nogmaals
  release `sha-e1a4994` en actuatorstatus `UP`. Vijf canonieke topicvragen gaven `EMPTY`; vijf
  verzoeken met de genormaliseerde term `watersnood 1916` gaven ieder `READY` met precies één
  record dat een niet-lege titel, niet-lege provider en absolute HTTP(S)-bronlink had. Vijf
  plekcontroles gaven `READY` met Assumburg `Q1967073`; de Open Archieven-regressie gaf `READY`
  met één bron en een absolute link. Een rechtstreekse ongeauthenticeerde Europeana-controle gaf
  `401`, dezelfde controle met de publiek bekende gedeelde testkey gaf `200`, en Wikidata gaf
  `200`. De actieve acceptance-key kan hierdoor zonder Pod-/secretmetadata niet veilig van de
  gedeelde testkey worden onderscheiden; een geslaagde aanroep op de oude release is daarvoor geen
  bewijs.
- De publieke repositorymetadata bevestigde tijdens dezelfde controle dat `main` nog op
  `78da801` staat en pull request 62 de storybranch-head `82405ee` aanbiedt. De PR-preview draait
  aantoonbaar backendversie `sha-82405ee`, maar heeft niet de acceptatiespecifieke secretketen en
  levert daarom fail-closed `OUTAGE` voor de topicroute; die preview kan de ontbrekende
  acceptance-key- en rolloutverificatie niet vervangen.
- Het volledige lokale verificatievangnet is op checkout-head `82405ee` opnieuw uitgevoerd:
  checksum-/rolloutsimulatie groen; backend 329 tests, 0 failures/errors (19 Docker-afhankelijke
  integratietests overgeslagen); frontend analyse groen, 125 tests groen en webbuild geslaagd;
  frontend-admin analyse groen en 22 tests groen.

### Ronde na de scopecorrectie (checkout-head `0c708ba`)

- Gerichte backend-topicsearchtests na de Context-, cache- en badgewijzigingen: 39 tests,
  0 failures/errors.
- Volledig verificatievangnet opnieuw uitgevoerd en groen:
  - `./deploy/verify-runtime-secret-rollout.sh`: checksums actueel en de secret-only simulatie
    levert een andere, exact overeenkomende Pod-templatechecksum op;
  - backend `mvn clean verify`: 331 tests, 0 failures/errors, 19 Docker-afhankelijke
    Testcontainers-tests overgeslagen in deze Runtime zonder Docker, `BUILD SUCCESS`;
  - frontend: `flutter analyze` zonder issues, 126 tests groen (inclusief de nieuwe Shift+Tab- en
    Context-bronmarkeringstests) en `flutter build web` geslaagd;
  - frontend-admin: `flutter analyze` zonder issues en 22 tests groen.
- Nieuwe of aangescherpte regressietests in deze ronde: elke CC BY-SA-versiesuffix (1.0, 2.0/nl,
  3.0, 4.0) levert exact `CC BY-SA`; een cachehit behoudt het oorspronkelijke raadplegingsmoment;
  het `Context (Wikidata)`-blok toont label, beschrijving en bronmarkering en ontbreekt volledig
  zonder context; Shift+Tab keert op het outagescherm terug naar de retry-knop en Enter activeert
  die.

### Ronde na de key-in-guid-blocker (checkout-head `0843de2`)

Reviewercommentaar 3972 stelde vast dat de Europeana-`guid` de gebruikte API-key als
`utm_campaign`-parameter meedraagt. Omdat de guid de bronlink-fallback is voor precies het
canonieke `watersnood 1916`-record, zou die key na uitrol met de eigen acceptatiesleutel in elke
publieke `POST /api/topic-search`-respons, in de DOM, in de browserhistorie en in referrer-/
proxylogs terechtkomen. Dat is in strijd met AC 2.

- `buildTopicSearchRecordOrNull` gebruikt bij de `guid`-fallback voortaan uitsluitend het
  sleutelvrije deel van de URL: querystring en fragment worden verwijderd voordat de waarde
  `sourceUrl` wordt. Het Europeana-item blijft zo gewoon bereikbaar op zijn canonieke pad. Een
  `edmIsShownAt` van de instelling zelf wordt niet aangepast; die queryparameters zijn functioneel
  en bevatten geen keymateriaal.
- Een guid die na het strippen geen absolute HTTP(S)-URL meer overhoudt, telt niet langer als
  geldige bronverwijzing en maakt het record ongeldig — hetzelfde fail-closed gedrag als voorheen.
- Nieuwe regressietests: een guid met `utm_source`/`utm_medium`/`utm_campaign=<key>` levert een
  `sourceUrl` zonder de key, zonder `utm_`-parameter en zonder querystring; een guid met fragment
  wordt eveneens geschoond; een guid die alleen uit een querystring bestaat is ongeldig; een
  absolute `edmIsShownAt` behoudt wel zijn eigen queryparameters. Dezelfde eigenschap is
  end-to-end getoetst in `RestClientArchivesEuropeanaClientTest` via een mock-Europeana-respons.
  Alle testwaarden zijn verzonnen tekenreeksen; er is geen echte key gebruikt of gelogd.
- Opgevolgde suggestie uit hetzelfde commentaar: de rechtenmapping herkent In Copyright nu op elke
  `rightsstatements.org`-URL met `InC` (dus ook `rightsstatements.org/page/InC/...`) in plaats van
  alleen `/vocab/inc`, met regressietest.
- `docs/development.md` en `docs/factory/technical-spec.md` beschrijven de geschoonde
  guid-fallback en de verbrede In Copyright-herkenning.
- Volledig verificatievangnet op deze head opnieuw uitgevoerd en groen:
  - `./deploy/verify-runtime-secret-rollout.sh`: checksums actueel en de secret-only simulatie
    levert een andere, exact overeenkomende Pod-templatechecksum op;
  - backend `mvn clean verify`: 337 tests, 0 failures/errors, 19 Docker-afhankelijke
    Testcontainers-tests overgeslagen in deze Runtime zonder Docker, `BUILD SUCCESS`;
  - frontend: `flutter analyze` zonder issues, 126 tests groen en `flutter build web` geslaagd;
  - frontend-admin: `flutter analyze` zonder issues en 22 tests groen.

### Ronde na de CORS-blocker uit testverslag hkh-216 (checkout-head `e460e13`)

Het testverslag wees het werk af op AC 8: op de PR-preview gaf **elke** `POST /api/*` vanuit de
browser 403 `Invalid CORS request`, waardoor Kasteel Assumburg, de Open Archieven-regressie en de
topicroute allemaal BRONUITVAL toonden. Dezelfde afwijzing trad op acceptatie op. Dit is de
gedeelde oorzaak die in de eerdere rondes gemist is, en hij verklaart het storysymptoom
"structurele OUTAGE ondanks extern bereikbare bronnen" voor Europeana én Wikidata tegelijk.

**Waarom het onderzoek dit niet zag.** Alle eerdere reproductie is met `curl` gedaan. Curl stuurt
standaard geen `Origin`-header; de CORS-toetsing wordt dan helemaal niet geraakt en alle routes
geven `READY`. Alleen een echte browserclient lokt het gedrag uit. Dit onderscheidt de oorzaak ook
scherp van de eerder onderzochte sporen: externe bronbereikbaarheid, de Europeana-secretketen, de
checksum-/rolloutketen en de gedeelde HTTP-client zijn alle vier aantoonbaar in orde - de fout zit
in de gedeelde runtime-CORS-configuratie vóór de routelogica.

**Bewezen oorzaak.**

- De webapp wordt op preview en acceptatie same-origin geserveerd: `API_BASE_URL` is bij die builds
  leeg en de frontend-nginx proxyt `/api/` naar de backendservice met `Host $host`.
- Browsers sturen bij een POST ook op een same-origin verzoek een `Origin`-header mee. Spring
  Framework behandelt sinds versie 6 elk verzoek met zo'n header als CORS-verzoek; de same-origin
  uitzondering bestaat daar niet meer. Same-origin verkeer werd daardoor tegen
  `hkh.cors-allowed-origin-patterns` getoetst.
- Een lege waarde van `HKH_CORS_ALLOWED_ORIGIN_PATTERNS` leverde na `split(',')` precies één
  patroon `""` op. Dat matcht op geen enkele herkomst, dus elk browserverzoek werd met 403
  afgewezen - ook al kwam het van de eigen pagina.
- Dit is geen incident maar een terugkerende storing: dezelfde 403 is eerder tweemaal opgelost door
  het secret opnieuw te verzegelen (`7ca31be` voor productie, `3a05fc8` voor acceptatie, met exact
  deze diagnose in de commitmessage). Zolang same-origin verkeer van een secretwaarde afhangt,
  breekt de webapp opnieuw zodra die waarde bij een volgende reseal afwijkt. Factory-agents kunnen
  bovendien geen secret verzegelen, dus een derde reseal is hier geen beschikbare oplossing.

**Wijzigingen.**

- Nieuw: `SameOriginRequestFilter` (`nl.vdzon.hkh.configuration`). De filter herkent een
  same-origin verzoek - herkomstschema http(s), herkomsthost exact gelijk aan de host waaraan het
  verzoek gericht is, en geen afwijkende expliciete poort - en verbergt daarvan de `Origin`-header,
  zodat Spring het weer als gewoon same-origin verzoek afhandelt. De publieke poort is achter de
  OpenShift-route niet zichtbaar voor de backend, dus een herkomst zonder expliciete poort telt als
  dezelfde poort; een herkomst mét afwijkende poort (bijvoorbeeld een lokale frontend op
  `http://localhost:3000` tegenover poort 8080) blijft een echt cross-origin verzoek dat gewoon
  langs de patronen gaat. Cross-site-bescherming verzwakt hierdoor niet: een pagina op een andere
  host houdt haar `Origin` en wordt nog steeds getoetst, en een browser stuurt cookies alleen naar
  de host waar ze bij horen.
- `WebConfiguration` negeert lege elementen in de patroonlijst en maakt de permit-all standaard van
  `CorsRegistry.addMapping` expliciet leeg. Zonder die laatste stap zou een lege patroonlijst juist
  álle cross-origin toegang toelaten: Spring wist `allowedOrigins = ["*"]` alleen wanneer er
  daadwerkelijk een patroon wordt toegevoegd. Het gedrag is nu deterministisch fail-closed - geen
  patronen betekent geen cross-origin toegang - en identiek aan voorheen zodra er wél patronen
  staan.
- Bewust niet gewijzigd: de verzegelde waarde van `HKH_CORS_ALLOWED_ORIGIN_PATTERNS` in
  `deploy/base/sealed-secret-runtime.yaml` en `deploy/overlays/acceptance/acceptance-secret.yaml`.
  Verzegelen kan alleen buiten de factory, en productie heeft die patronen echt nodig (de
  productiefrontend wordt met een ingebakken `API_BASE_URL` naar de aparte backend-route gebouwd en
  is dus wél cross-origin). De overlay overschrijft de secretwaarde daarom niet; dat zou een
  latere, correcte reseal stilzwijgend blokkeren. De preview-overlay houdt de lijst bewust leeg -
  daar bestaat geen legitieme cross-origin client - met een toelichting waarom dat nu veilig is.
- `deploy/README.md`, `docs/development.md` en `docs/factory/technical-spec.md` beschrijven het
  CORS-model: waar de patronen wél over gaan, waarom same-origin verkeer er niet meer van afhangt
  en waarom de fout met curl onzichtbaar is.

**Nieuwe regressietests.** `WebConfigurationCorsTest` bouwt een echte Spring-MVC-context met de
filter ervoor en dekt het gemelde gedrag end-to-end af: een same-origin POST met `Origin`-header
slaagt zonder patronen én met alleen andere patronen (de gemelde 403 kan niet terugkeren); een POST
zonder `Origin`-header blijft slagen; een onbekende cross-origin POST én preflight blijven 403; een
geconfigureerde cross-origin POST krijgt nog steeds de `Access-Control-Allow-Origin`-header; lege
elementen worden nooit een patroon. `SameOriginRequestFilterTest` dekt de herkenning zelf af,
inclusief afwijkende host, afwijkende poort, hoofdletterongevoelige hostvergelijking, overige
headers die ongemoeid blijven, en een opake of onparseerbare herkomst die fail-closed nooit als
same-origin telt.

**Verificatie in deze ronde.**

- gerichte nieuwe tests: 16 tests, 0 failures/errors;
- `./deploy/verify-runtime-secret-rollout.sh`: groen, "Runtime-secretwijziging vernieuwt de backend
  Pod-templatechecksum";
- `kubectl kustomize deploy/overlays/preview` en `.../acceptance` renderen ongewijzigd; de
  gegenereerde previewsecretnaam blijft gelijk, want alleen een toelichtende opmerking is
  toegevoegd;
- backend `mvn -B clean verify`: 353 tests, 0 failures/errors, 19 Docker-afhankelijke
  Testcontainers-tests overgeslagen in deze Runtime zonder Docker, `BUILD SUCCESS`;
- frontend en frontend-admin zijn in deze ronde niet gewijzigd; `flutter analyze` en `flutter test`
  zijn ter bevestiging opnieuw gedraaid en blijven groen.

**Wat deze ronde niet kan aantonen.** De previewcontrole uit AC 8 hoort bij testsubtaak hkh-216 en
kan pas op de gepubliceerde head worden gedaan; factory-agents rollen niet uit. Ook de live
acceptatiecontroles (deel B) blijven buiten deze subtaak.

### Ronde na de reviewblocker over de agenttoegangs-allowlist (checkout-head `d7356f2`)

De vervolgreview (issue comment 3996) keurde de CORS-oplossing zelf goed, maar vond een regressie
die pas door de merge met `main` ontstond. `main`-commit `a65b75a` heeft de herkomstcontrole bij
`POST /api/auth/agent-session` juist bedoeld werkend gemaakt, terwijl `SameOriginRequestFilter` uit
deze story de `Origin`-header van een same-origin verzoek voor de **volledige** filterketen
verbergt - dus ook voor `AgentAccessController`, die de header met `@RequestHeader("Origin")` zelf
uitleest.

**Bevestigde oorzaak.** De aanmeldpagina (`GET /api/auth/agent-login`) wordt op dezelfde origin als
de frontend geopend en doet een relatieve `fetch` naar `/api/auth/agent-session`; de frontend-nginx
proxyt `/api/` met `Host $host`, dus de backend ziet dezelfde host als de browser en de filter
grijpt in. `AgentAccessVerifier.verify` toetst de allowlist alleen wanneer `origin != null`, dus met
een verborgen header werd `AI_ACCESS_ALLOWED_ORIGINS` op dat pad stilzwijgend overgeslagen. Erger
dan overslaan: bij een lege of verouderde allowlist wees `main` zo'n browseraanmelding af, terwijl de
storybranch hem juist accepteerde - het gedrag klapte van fail-closed naar fail-open, in preview,
acceptatie en productie tegelijk. Geen bestaande test merkte dat, omdat `AgentAccessVerifierTest` de
verifier los van de filterketen toetst. Er is buiten deze controller geen andere plek in de backend
die de `Origin`-header zelf leest (Spring Security staat niet op het klassenpad), dus dit was de
enige geraakte consument.

**Wijziging.** De filter bewaart de verborgen waarde nu als requestattribuut
(`SameOriginRequestFilter.ORIGINAL_ORIGIN_ATTRIBUTE`), en `AgentAccessController` leest dat
attribuut met de `Origin`-header als terugval. Daarmee blijft de CORS-oplossing ongewijzigd (Spring
ziet nog steeds geen CORS-verzoek) terwijl de herkomstcontrole exact het gedrag van `main`
terugkrijgt: een same-origin aanmelding wordt weer tegen de allowlist getoetst, een aanroep zonder
`Origin`-header (curl) gedraagt zich onveranderd, en de verzegelde `AI_ACCESS_ALLOWED_ORIGINS` in
`deploy/overlays/{preview,acceptance,openshift}` is geen dode configuratie meer. De modulegrenzen
(`allowedDependencies = {}` op beide modules) staan geen directe verwijzing toe, dus de
attribuutnaam staat in `auth` als eigen constante; de nieuwe test bewaakt dat beide gelijk blijven.

**Nieuwe regressietest.** `AgentAccessOriginAllowlistTest` (6 tests) haalt een same-origin POST naar
`/api/auth/agent-session` door de echte filterketen heen: een niet-toegestane eigen origin geeft
401, een lege allowlist geeft 401 (fail-closed, precies het omgeklapte geval), een toegestane origin
geeft 200 met de verwachte identiteit, een cross-origin aanmelding blijft 401, een aanroep zonder
`Origin` behoudt haar bestaande gedrag, en beide attribuutconstanten moeten gelijk zijn. Met de
oude controllercode faalt deze test aantoonbaar op twee van die gevallen (beide 200 in plaats van
401); dat is vóór het herstel gecontroleerd.

**Opgevolgde suggesties uit hetzelfde commentaar.**

- `deploy/README.md` sprak de gemergede overlays tegen ("preview en acceptatie hebben niets aan de
  patronen"). De tekst beschrijft nu dat beide overlays de patronen sinds `a65b75a` expliciet zetten
  voor de losse beheerapp, dat die `env`-waarde van de gegenereerde/verzegelde waarde wint en dat ze
  dus niet opgeruimd moeten worden. Ook staat er nu dat de herkomstcontrole bij agentaanmelding
  gewoon blijft gelden.
- De bij de merge weggevallen toelichting in `deploy/overlays/preview/kustomization.yaml` is
  teruggezet en bijgewerkt met de precedentie tussen `env` en `envFrom`. De rendering blijft
  byte-identiek: `kubectl kustomize deploy/overlays/preview` vóór en na deze wijziging levert exact
  hetzelfde manifest, inclusief dezelfde gegenereerde secretnaam.
- De bewuste keuze om het herkomstschema niet te vergelijken staat nu expliciet in de KDoc van
  `isSameOrigin`, met de reden: achter de OpenShift-route ziet de backend altijd plain HTTP terwijl
  de browserherkomst `https` is, en `X-Forwarded-Proto` wordt door de tussenliggende nginx met
  `$scheme` overschreven. De versoepeling blijft beperkt tot dezelfde hostnaam.
- De onderzochte head in dit worklog is bijgewerkt naar `d7356f2`.

**Verificatie in deze ronde.**

- gerichte nieuwe test: `AgentAccessOriginAllowlistTest` 6 tests, 0 failures/errors, en aantoonbaar
  rood zonder het herstel;
- `kubectl kustomize deploy/overlays/{preview,acceptance,openshift}`: alle drie renderen, preview
  byte-identiek aan de rendering vóór deze ronde;
- `./deploy/verify-runtime-secret-rollout.sh`: exitcode 0, "Runtime-secretwijziging vernieuwt de
  backend Pod-templatechecksum";
- backend `mvn -B --no-transfer-progress clean verify`: 364 tests, 0 failures/errors, 19
  Docker-afhankelijke Testcontainers-tests overgeslagen in deze Runtime zonder Docker,
  `BUILD SUCCESS`;
- frontend: `flutter analyze` zonder issues, `flutter test -j 1` 126 tests groen, `flutter build
  web` geslaagd;
- frontend-admin: `flutter analyze` zonder issues en `flutter test -j 1` 22 tests groen;
- frontend en frontend-admin zijn in deze ronde niet gewijzigd; de controles zijn ter bevestiging
  gedraaid.

## Reikwijdte en grenzen van deze run

Deel B van de acceptance criteria (uitrol en live controle op acceptatie) hoort blijkens de
scopecorrectie van 2026-09-14 niet bij deze subtaak. Factory-agents hebben geen clustertoegang en
geen plaintext secrets, en de storybranch komt pas na de merge-subtaak via CI en de bestaande
Argo CD Application `hkh-autopilot-acceptance` op acceptatie. De eerdere blockers "acceptatie
draait nog `sha-e1a4994`", "acceptatie-uitrol ontbreekt" en "live key-/time-outproeven niet
uitgevoerd" zijn daarmee vervallen; ze worden na de deploy-subtaak door de operator of een
vervolgstory vastgelegd.

De live invalid-key- en time-outproeven blijven bewust achterwege: ze zouden een clustermutatie
vereisen die vanuit de Runtime niet veilig terug te draaien is. Beide paden zijn in plaats daarvan
met geautomatiseerde tests afgedekt (een lege key en `api2demo` worden vóór de HTTP-call geweigerd;
een trage bron boven het tweesecondenbudget levert `OUTAGE`), en de PR-preview toont de topicroute
bewust fail-closed omdat daar geen Europeana-key staat.
