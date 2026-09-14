# hkh-215 - Onderzoek, herstel en rol uit

## Status

- Rol: developer
- Onderzochte branch-head: `78da801` (`ai/hkh-208`)
- Live controles: 2026-09-14 13:05-13:14 UTC
- Omgeving: `https://hkh-autopilot-acceptance.vdzonsoftware.nl`

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
- De repository had alleen een Argo CD Application voor productie. De acceptance-overlay werd dus
  wel door de buildworkflow bijgewerkt, maar nergens in deze repository declaratief automatisch
  toegepast. Dat verklaart waarom een repositorywijziging alleen geen betrouwbare
  acceptance-rollout vormde.

## Wijzigingen

- De Europeana-query verwijdert uitsluitend zelfstandige Nederlandse vulwoorden (`de`, `het`,
  `een`, `van`) en behoudt altijd de `AND Heemskerk`-beperking. De canonieke term wordt zo
  deterministisch `watersnood 1916 AND Heemskerk`.
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
- `deploy/argocd/application-acceptance.yaml` maakt de acceptance-overlay declaratief
  self-healing vanaf `main`.
- De checksumlogica staat centraal in `deploy/update-runtime-secret-checksums.sh`; de buildworkflow
  gebruikt dit script. `deploy/verify-runtime-secret-rollout.sh` controleert de actuele checksums,
  rendert de acceptance-overlay en simuleert geïsoleerd dat een secret-only wijziging de
  Pod-templatechecksum verandert. Deze check is opgenomen in het Factory-verificatievangnet.

## Verificatie

- Gerichte topicsearch-tests: 32 tests, 0 failures/errors.
- Gerichte regressiecontrole van de persoonszoek-service en -controller na de verifierbevinding:
  29 tests, 0 failures/errors; de sessie-indicator/open-test is groen.
- Volledige backend-`clean verify`: 328 tests gevonden, 0 failures/errors, `BUILD SUCCESS` en een
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

## Operationele grens van deze run

De huidige checkout heeft geen bruikbare clusterroute/operatorcontext en bevat terecht geen lokale
plaintext acceptatiesecrets. Daarom zijn de nieuwe Application en backendcode niet vanuit deze run
met `oc apply` gemuteerd. De reeds verzegelde key/checksum zijn aantoonbaar live; toepassing van de
nieuwe queryfix volgt via de vastgelegde acceptance-Application zodra deze branch naar `main` is
gepubliceerd. De post-deploycontrole van vijf canonieke `READY`-antwoorden en de gecontroleerde live
invalid-key/time-outproeven horen daardoor bij de aansluitende test/deploystap; lokaal blijven die
foutpaden volledig fail-closed afgedekt.
