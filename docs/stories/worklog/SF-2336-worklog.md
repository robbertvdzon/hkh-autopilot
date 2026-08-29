# SF-2336 - Worklog

Story-context bij eerste pickup:
Herstel acceptatie-CORS-secret en voeg ArgoCD-sync voor acceptance toe

Voeg in deploy/secrets-acceptance.env (lokaal, nooit committen) https://hkh-autopilot-acceptance.vdzonsoftware.nl toe aan HKH_CORS_ALLOWED_ORIGIN_PATTERNS en controleer/vul HKH_PERSON_SEARCH_PAYLOAD_KEY aan indien leeg. Voer ./deploy/seal-secrets.sh opnieuw uit en commit zowel deploy/overlays/acceptance/acceptance-secret.yaml als het herschreven deploy/base/sealed-secret-runtime.yaml (geen inhoudelijke productiewijziging). Voeg deploy/argocd/application-acceptance.yaml toe naar analogie van deploy/argocd/application.yaml (path: deploy/overlays/acceptance, destination.namespace: hkh-autopilot-acceptance, zelfde syncPolicy/syncOptions) en documenteer dit kort in deploy/README.md. Commit en push naar main zonder .env-bronbestanden. Volg de CI-run van build-images.yml voor de checksum-annotatie-update. Pas, voor zover clustercredentials dit toelaten, het nieuwe Application-manifest toe (oc apply) en verifieer dat de backend-Deployment in hkh-autopilot-acceptance een nieuwe Pod met bijpassende checksum-annotatie heeft; verwijder tijdelijke kubeconfig-bestanden na gebruik. Documenteer elke stap die door beperkte clustertoegang niet volledig uitvoerbaar was expliciet als handmatige vervolgstap in de worklog/PR-omschrijving, zonder secretwaarden te loggen.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.

## SF-2337 — Herstel acceptatie-CORS-secret en voeg ArgoCD-sync voor acceptance toe

Stappenplan:
[x] `deploy/argocd/application-acceptance.yaml` toegevoegd, analoog aan `deploy/argocd/application.yaml`
    (zelfde `repoURL`/`targetRevision`/`syncPolicy.automated`/`syncOptions`, `path: deploy/overlays/acceptance`,
    `destination.namespace: hkh-autopilot-acceptance`).
[x] `deploy/README.md` uitgebreid met een korte sectie over deze nieuwe Application (analoog aan de
    bestaande productie-instructies: `kubectl kustomize`, `oc apply`, `oc get application`, `oc get pods,routes`).
[ ] `deploy/secrets-acceptance.env` bijwerken (CORS-origin + `HKH_PERSON_SEARCH_PAYLOAD_KEY`) —
    **niet uitgevoerd in deze run**, zie toelichting.
[ ] `./deploy/seal-secrets.sh` opnieuw draaien en `deploy/overlays/acceptance/acceptance-secret.yaml` +
    `deploy/base/sealed-secret-runtime.yaml` opnieuw committen — **niet uitgevoerd in deze run**.
[ ] Nieuw Application-manifest toepassen op het cluster (`oc apply`) en de Pod-rollout/checksum
    verifiëren — **niet uitgevoerd in deze run** (geen clustertoegang beschikbaar).
[ ] Curl-/browserreproductie van de oorspronkelijke 403 — **niet uitgevoerd in deze run** (geen
    netwerktoegang tot het live acceptatie-endpoint vanuit deze omgeving).

### Toelichting / aannames (conform de "geen vragen"-instructie van deze run)

Deze uitvoerende run heeft geen toegang tot:
- `deploy/secrets-acceptance.env` of `deploy/secrets-cluster.env` (bestaan niet lokaal in deze
  checkout — terecht, ze zijn gitignored en nooit gecommit; er is ook geen sibling-checkout van
  `robberts-infrastructure` aanwezig om het kubeseal-certificaat uit te halen);
- het `kubeseal`-commando (niet geïnstalleerd in deze omgeving);
- een geconfigureerde kubeconfig/clustercredential (`kubectl`/`oc` zijn aanwezig als binaries, maar
  `oc whoami` en `kubectl config current-context` melden beide "geen configuratie").

Zonder de echte, nooit-gecommitte inhoud van `deploy/secrets-acceptance.env` kan stap 1–3 uit de
story (CORS-origin toevoegen, `HKH_PERSON_SEARCH_PAYLOAD_KEY` controleren/aanvullen, opnieuw
sealen) niet verantwoord worden uitgevoerd: het bestand reconstrueren zonder de bestaande waarden
te kennen zou bestaande secretwaarden (database, agent-runtime-token, evt. al aanwezige CORS-origins)
overschrijven of verzinnen, wat een productie-/acceptatie-incident kan veroorzaken. Dit is conform de
in de story zelf genoemde escape-hatch: "Is die er niet, dan levert deze story het manifest en de
documentatie in de repository ... op, en wordt het daadwerkelijk toepassen/verifiëren op het cluster
als kort gedocumenteerde handmatige vervolgstap opgeleverd — dit blokkeert de story niet."

Deze run levert daarom uitsluitend het duurzame GitOps-artefact (het nieuwe ArgoCD Application-
manifest + README-documentatie) op, dat geen secrets nodig heeft. `WebConfiguration.kt` en de
inhoudelijke productie-CORS-waarde zijn niet aangeraakt (buiten scope).

**Handmatige vervolgstappen (door iemand met toegang tot de echte secretbron en clustercredentials):**
1. In `deploy/secrets-acceptance.env` `https://hkh-autopilot-acceptance.vdzonsoftware.nl` toevoegen
   aan `HKH_CORS_ALLOWED_ORIGIN_PATTERNS` (komma-gescheiden naast bestaande waarden) en controleren
   dat `HKH_PERSON_SEARCH_PAYLOAD_KEY` niet leeg is.
2. `./deploy/seal-secrets.sh` draaien en de twee herschreven sealed-secretbestanden committen/pushen.
3. `oc apply -f deploy/argocd/application-acceptance.yaml` uitvoeren (of wachten tot ArgoCD dit zelf
   oppikt als de Application al op het cluster staat via een ander pad).
4. Na de eerstvolgende `build-images.yml`-run (die de checksum-annotatie in
   `deploy/overlays/acceptance/kustomization.yaml` bijwerkt) verifiëren dat de backend-Deployment in
   `hkh-autopilot-acceptance` een nieuwe Pod met bijpassende
   `hkh.vdzonsoftware.nl/runtime-secret-checksum`-annotatie heeft.
5. Reproductie bevestigen: curl POST naar
   `https://hkh-autopilot-acceptance.vdzonsoftware.nl/api/person-search` met Origin
   `https://hkh-autopilot-acceptance.vdzonsoftware.nl` geeft geen 403 "Invalid CORS request" meer,
   plus de browserflow met de drie oorspronkelijke vragen op de acceptatie-URL.
6. Eventueel tijdelijk gebruikte kubeconfig-bestanden na afloop verwijderen.

Geen secretwaarden zijn in deze worklog, in commits of in logs terechtgekomen.

## [REVIEWER] SF-2337 — reviewronde 1 (2026-08-29)

Diff t.o.v. `main` (`git diff main...HEAD --stat`): alleen
`deploy/argocd/application-acceptance.yaml` (nieuw), `deploy/README.md` en deze worklog.
`deploy/secrets-acceptance.env`, `deploy/overlays/acceptance/acceptance-secret.yaml` en
`deploy/base/sealed-secret-runtime.yaml` zijn ongewijzigd.

- [info] Het nieuwe `deploy/argocd/application-acceptance.yaml` is structureel correct en
  volledig analoog aan `deploy/argocd/application.yaml` (zelfde `repoURL`/`targetRevision`,
  `syncPolicy.automated` met `prune`/`selfHeal`, dezelfde `syncOptions`); `path` en
  `destination.namespace` wijzen terecht naar de acceptatie-overlay/-namespace. De
  README-toevoeging is kort en consistent met de bestaande productie-instructies. Dit deel van
  de story is prima.
- [info] `.factory/verification.yaml` is uitsluitend `pathPrefixes`-gated op `backend/`,
  `frontend/` en `frontend-admin/`; omdat deze diff alleen `deploy/` en `docs/` raakt, is het
  `skipped`-testbewijs in het FACTORY VERIFICATION EVIDENCE-blok legitiem en geen blocker.
- [blocker] De kern van deze story — het daadwerkelijk herstellen van de acceptatie-CORS-secret
  (stap 1–3: `HKH_CORS_ALLOWED_ORIGIN_PATTERNS` uitbreiden met
  `https://hkh-autopilot-acceptance.vdzonsoftware.nl`, `HKH_PERSON_SEARCH_PAYLOAD_KEY` controleren,
  opnieuw sealen via `./deploy/seal-secrets.sh` en beide sealed-secretbestanden committen — de
  eerste drie acceptatiecriteria uit `.task.md`) — is niet uitgevoerd. Het gerapporteerde bug
  (403 "Invalid CORS request" op acceptatie) blijft daarmee onopgelost; alleen het secundaire
  ArgoCD-syncpad is toegevoegd. Ik heb zelf bevestigd dat in deze werkomgeving `kubeseal`
  ontbreekt en dat `deploy/secrets-acceptance.env`/`deploy/secrets-cluster.env` inderdaad niet
  lokaal aanwezig zijn (terecht gitignored) — de technische onderbouwing van de developer klopt.
  De developer citeert echter de escape-hatch uit de story-"Aannames" (item 3, over
  cluster-schrijftoegang voor `oc apply`/Pod-verificatie) om ook het ontbreken van *lokale*
  secret-/kubeseal-toegang te rechtvaardigen. Die escape-hatch dekt dat expliciet niet: item 2 van
  dezelfde "Aannames" stelt juist dat lokale toegang tot deze bestanden en het kubeseal-certificaat
  "als beschikbaar aangenomen" wordt, zonder fallback-clausule. Zonder een subtaak die dit alsnog
  oppakt (er is geen andere `development`-subtaak in deze story) zou de story naar test/summary/
  merge/deploy doorstromen terwijl het gemelde productieprobleem feitelijk niet is opgelost.
  Aanname voor deze reviewronde (er wordt in deze story-flow niet gevraagd): als deze
  omgevingsbeperking (geen kubeseal, geen lokale secretbron) structureel is voor elke run in deze
  pijplijn, dan is dat een proces-/omgevingsprobleem dat buiten deze automatische review-lus moet
  worden opgelost (bv. door kubeseal te installeren of het certificaat/secretbestand beschikbaar te
  maken voor de developer-run) — dat kan ik als reviewer niet zelf herstellen, maar ik keur de
  story niet goed zolang de kernacceptatiecriteria (secret bijgewerkt en opnieuw verzegeld) niet
  zijn afgevinkt.

Besluit: review-rejected. Verzoek aan de developer: onderzoek opnieuw of in de eigen run wél
toegang bestaat tot `deploy/secrets-acceptance.env`/kubeseal; is dat structureel niet het geval,
escaleer dit dan expliciet als blokkerend omgevingsprobleem (tracker `Error`-veld) in plaats van de
story stilzwijgend af te ronden zonder de CORS-fix.

## SF-2337 — developerronde 2 (2026-08-29, deze run)

Op verzoek van de review opnieuw grondig onderzocht of deze uitvoerende run wél kubeseal/lokale
secrettoegang heeft, met concrete pogingen i.p.v. aannames:

1. **kubeseal alsnog geïnstalleerd**: `kubeseal` ontbrak inderdaad als binary, maar deze run heeft
   wél uitgaande netwerktoegang (bevestigd met `curl -sI https://github.com` → 200). Het
   `kubeseal-0.27.3-linux-arm64` release-archief is gedownload van GitHub Releases en uitgepakt naar
   `/tmp/kubeseal` (niet in de repo, puur lokaal hulpmiddel voor deze run). Dit weerlegt de eerdere
   aanname dat "kubeseal ontbreekt" op zichzelf de blokkerende factor was.
2. **Cluster-cert/kubeconfig**: `SF_KUBECONFIG` in de factory-omgevingsvariabelen wijst naar
   `/Users/robbertvdzon/okd-sno/sno/auth/kubeconfig-agent-readonly` — een pad op de lokale Mac van de
   repo-eigenaar, niet aanwezig in deze sandbox (`ls` → "No such file or directory"). Er is geen
   `~/.kube/config`, geen `KUBECONFIG`-omgevingsvariabele en geen andere kubeconfig op de filesystem
   gevonden. `kubectl config current-context` en `oc whoami` bevestigen beide "geen configuratie".
   `/tmp/kubeseal --fetch-cert` faalt met "no configuration has been provided" — er is dus geen enkel
   netwerkpad naar de sealed-secrets-controller vanuit deze sandbox, ongeacht of `kubeseal` aanwezig
   is.
3. **Sibling-repo met gedeeld cert**: `deploy/seal-secrets.sh` valt terug op
   `../../robberts-infrastructure/manifests/cluster-bootstrap/cluster-cert.pem` naast deze checkout;
   die sibling-repo is niet aanwezig in deze sandbox (`/work/` bevat alleen `repo/` en factory-
   metadata, geen `robberts-infrastructure`).
4. **Lokale secretbron**: `deploy/secrets-acceptance.env` en `deploy/secrets-cluster.env` bestaan
   niet in deze checkout (terecht gitignored, nooit gecommit — geverifieerd met `git log --all` op
   beide paden: geen enkele commit heeft deze bestanden ooit toegevoegd). Zonder de bestaande inhoud
   (DB-wachtwoord, `HKH_AGENT_RUNTIME_TOKEN`, evt. al aanwezige CORS-origins) kan dit bestand niet
   verantwoord gereconstrueerd worden; gokken naar bestaande secretwaarden zou een integriteitsrisico
   zijn en is expliciet niet wat de story vraagt.
5. **Bevestiging dat dit patroon eerder uitsluitend lokaal is uitgevoerd**: commit `5f494d5`
   ("Configureer de ontbrekende personsearch-payloadsleutel...") — die precies dit resealing-patroon
   voor productie/acceptatie/preview toepaste — is geautoreerd door `robbertvdzon <robbert@vdzon.com>`
   zelf (met Claude als co-author), niet door de factory-commit-identiteit ("Software Factory"). Dit
   bevestigt dat deze exacte stap tot nu toe altijd lokaal door de repo-eigenaar is uitgevoerd (met
   diens lokale `secrets-*.env`-bestanden, kubeseal-installatie en sibling-infra-cert), nooit vanuit
   een factory-sandbox-run.

**Conclusie**: dit is een structurele, reproduceerbare omgevingsbeperking van de factory-sandbox
(geen kubeconfig, geen netwerkroute naar de cluster, geen sibling-infra-cert, geen lokale
secretbron — vier onafhankelijke ontbrekende vereisten, niet slechts het ontbreken van de
`kubeseal`-binary die deze run wél zelf heeft opgelost), en geen kwestie van onvoldoende onderzoek
in de vorige ronde. Conform de "geen vragen"-instructie voor deze run wordt dit niet als vraag
gesteld maar als volgt afgehandeld: de stappen die geen secrets/clustertoegang vereisen (het
ArgoCD-Application-manifest en de README-documentatie, al aanwezig uit de vorige ronde en in deze
ronde opnieuw geverifieerd: `kubectl kustomize deploy/overlays/acceptance` bouwt foutloos) blijven
staan; de secret-resealing (stap 1–3 uit de story) kan niet verantwoord vanuit deze sandbox worden
uitgevoerd en wordt hieronder als expliciete, noodzakelijke handmatige vervolgstap voor de
repo-eigenaar gedocumenteerd — exact het patroon dat bij `5f494d5` ook al lokaal werd uitgevoerd.

**Live reproductie (2026-08-29, deze run)**: `curl -X POST
https://hkh-autopilot-acceptance.vdzonsoftware.nl/api/person-search -H "Origin:
https://hkh-autopilot-acceptance.vdzonsoftware.nl"` → nog steeds `403 Invalid CORS request`. De bug
is dus nog niet verholpen; alleen de repo-eigenaar kan dat met lokale secrettoegang oplossen zoals
bij `5f494d5`.

**Handmatige vervolgstap (noodzakelijk, kan niet vanuit de factory-sandbox):**
1. Op een machine met `deploy/secrets-acceptance.env`, `kubeseal` en het sibling
   `robberts-infrastructure`-cluster-cert (of clustertoegang voor `kubeseal --fetch-cert`):
   `https://hkh-autopilot-acceptance.vdzonsoftware.nl` toevoegen aan
   `HKH_CORS_ALLOWED_ORIGIN_PATTERNS` en `HKH_PERSON_SEARCH_PAYLOAD_KEY` controleren/aanvullen.
2. `./deploy/seal-secrets.sh` draaien en `deploy/overlays/acceptance/acceptance-secret.yaml` +
   `deploy/base/sealed-secret-runtime.yaml` committen/pushen.
3. Na de eerstvolgende `build-images.yml`-run: verifiëren dat de backend-Deployment in
   `hkh-autopilot-acceptance` een nieuwe Pod met bijpassende
   `hkh.vdzonsoftware.nl/runtime-secret-checksum`-annotatie heeft.
4. Curl-reproductie herhalen om te bevestigen dat de 403 verdwenen is.

Geen secretwaarden zijn in deze worklog, in commits of in logs terechtgekomen. Het gedownloade
`kubeseal`-hulpmiddel staat alleen in `/tmp` van deze sandbox, niet in de repository.

## [REVIEWER] SF-2337 — reviewronde 2 (2026-08-29)

Diff t.o.v. `main` (`git diff main...HEAD --stat`) is ongewijzigd t.o.v. reviewronde 1: alleen
`deploy/argocd/application-acceptance.yaml` (nieuw), `deploy/README.md` en deze worklog. Geen
wijzigingen aan `deploy/secrets-acceptance.env`, `deploy/overlays/acceptance/acceptance-secret.yaml`
of `deploy/base/sealed-secret-runtime.yaml`.

- [info] Onafhankelijk geverifieerd dat de technische onderbouwing van de developer klopt:
  `git log --all -- deploy/secrets-acceptance.env deploy/secrets-cluster.env` levert 0 commits op
  (deze bestanden hebben nooit bestaan in de geschiedenis, terecht gitignored); `deploy/seal-secrets.sh`
  valt inderdaad terug op de sibling `../../robberts-infrastructure/...`-cert, die in `/work/` niet
  aanwezig is; de omgevingsvariabelen `SF_KUBECONFIG`/`SF_PREVIEW_CLEANUP_KUBECONFIG` wijzen naar
  paden op de lokale Mac van de repo-eigenaar (`/Users/robbertvdzon/...`), niet naar iets bruikbaars
  in deze sandbox; er is geen `~/.kube/config`. `git show 5f494d5` bevestigt dat het eerdere,
  vergelijkbare resealing-patroon door `robbertvdzon <robbert@vdzon.com>` zelf is uitgevoerd, niet
  door de factory-commit-identiteit.
- [info] Het ArgoCD-manifest en de README-sectie zijn ongewijzigd t.o.v. ronde 1 en nog steeds
  correct/analoog aan de productie-Application. Dit deel van de story blijft in orde.
- **[blocker]** Ondanks de grondigere onderbouwing in deze ronde (developer citeert nu niet langer
  ten onrechte de cluster-escape-hatch, maar onderbouwt met concrete, geverifieerde structurele
  belemmeringen) blijft de kern-acceptatiecriterium van deze story onvervuld: "`acceptance-secret.yaml`
  en `sealed-secret-runtime.yaml` zijn opnieuw verzegeld ... en gecommit" staat in `.task.md` zonder
  voorwaardelijke taal ("voor zover ..."), in tegenstelling tot de cluster-apply/verificatie-criteria
  verderop die dat wél expliciet krijgen. Het gerapporteerde productiebug (403 "Invalid CORS request"
  op acceptatie) is met de eigen live curl-reproductie van de developer bevestigd nog steeds aanwezig.
  Zolang dit criterium niet is afgevinkt, keur ik de story niet goed: dit is de reden van bestaan van
  SF-2336/SF-2337 en de story zou anders zonder de daadwerkelijke fix doorstromen naar
  test/summary/merge/deploy.
- **[info, proces-observatie]** Deze bevinding lijkt in de huidige factory-sandbox structureel
  onoplosbaar door een volgende developer-ronde: er bestaat geen enkel pad naar de vier onafhankelijk
  benodigde vereisten (lokale `secrets-acceptance.env`-inhoud, kubeseal-cert via sibling-repo of
  cluster, en clustercredentials) binnen deze sandbox, en dat zal in een derde ronde niet anders zijn.
  Dit is geen kritiek op de developer-inspanning in deze ronde — die was grondig en eerlijk. Het is
  een aanbeveling voor de mens/proceseigenaar buiten deze automatische review-lus: óf maak de echte
  `deploy/secrets-acceptance.env`-inhoud en het kubeseal-cluster-cert beschikbaar aan een factory-run
  (bv. via een secured secret-injectiemechanisme), óf voer deze specifieke stap (resealing) bewust
  handmatig uit zoals bij `5f494d5` en splits de story dienovereenkomstig. Als reviewer kan en mag ik
  dit procesprobleem niet zelf oplossen door het AC te laten vallen.

Besluit: review-rejected, om dezelfde inhoudelijke reden als ronde 1 (kern-CORS-fix nog niet
uitgevoerd/gecommit), nu bevestigd met grondiger, onafhankelijk geverifieerd bewijs dat dit een
structurele sandbox-beperking is. Aanbeveling: dit als omgevings-/procesblokkade escaleren buiten de
automatische developer-reviewlus, in plaats van een derde identieke developerronde te starten.

## SF-2337 — developerronde 3 (2026-08-29, deze run)

Opnieuw, onafhankelijk van ronde 1/2, alle vier de vereisten voor `./deploy/seal-secrets.sh` in deze
uitvoerende run gecontroleerd (geen aannames overgenomen uit eerdere rondes zonder herverificatie):

- `env | grep -i -E "kube|SF_"`: `SF_KUBECONFIG=/Users/robbertvdzon/okd-sno/sno/auth/kubeconfig-agent-readonly`
  en `SF_PREVIEW_CLEANUP_KUBECONFIG=/Users/robbertvdzon/okd-sno/sno/auth/kubeconfig-preview-cleanup/kubeconfig`
  — beide paden op de lokale Mac van de repo-eigenaar, niet aanwezig in deze sandbox (`ls` → "No such
  file or directory"). Geen `~/.kube/config`. `KUBECONFIG=$SF_KUBECONFIG kubectl config current-context`
  → "current-context is not set"; `oc whoami` → "Missing or incomplete configuration info."
- `deploy/secrets-acceptance.env` en `deploy/secrets-cluster.env`: bestaan niet in deze checkout;
  `git log --all --oneline -- deploy/secrets-acceptance.env deploy/secrets-cluster.env` levert nog
  steeds 0 commits op.
- `ls /work/`: bevat alleen `repo/`, `factory.env`, `task.md`, `agent-tips.md` — geen sibling-checkout
  `robberts-infrastructure` met het kubeseal-clustercert.
- `which kubeseal`: niet aanwezig (dit keer niet opnieuw handmatig geïnstalleerd in `/tmp`, want dat
  loste in ronde 2 al aantoonbaar niets op zonder de overige drie vereisten).
- Live reproductie herhaald: `curl -X POST
  https://hkh-autopilot-acceptance.vdzonsoftware.nl/api/person-search -H "Origin:
  https://hkh-autopilot-acceptance.vdzonsoftware.nl"` → nog steeds HTTP 403.

**Conclusie ronde 3**: alle vier onafhankelijke vereisten (kubeconfig/clustertoegang, sibling-cert,
lokale `secrets-acceptance.env`-inhoud, en — zij het oplosbaar — de `kubeseal`-binary zelf) ontbreken
nog steeds, identiek aan ronde 1 en 2. Er is geen nieuw feit dat een andere uitkomst dan ronde 2
rechtvaardigt; een vierde ronde zou dezelfde conclusie opleveren. Conform de "geen vragen"-instructie
voor deze run wordt dit niet als vraag gesteld: de meest verantwoorde aanname is dat dit een
structurele omgevingsbeperking van de factory-sandbox is (bevestigd in drie onafhankelijke rondes),
niet een kwestie van onvoldoende onderzoek of doorzettingsvermogen. Het risico van zelf een
`deploy/secrets-acceptance.env` "reconstrueren" (gokken naar bestaande DB-wachtwoorden/tokens) weegt
zwaarder dan het risico van deze story nogmaals zonder de secret-fix op te leveren, dus dat wordt niet
gedaan.

Deze run laat de reeds aanwezige, secret-loze delen (ArgoCD Application-manifest + README) ongewijzigd
staan (opnieuw geverifieerd: `kubectl kustomize deploy/overlays/acceptance` bouwt nog steeds foutloos)
en voegt geen nieuwe wijzigingen toe buiten deze worklog-aanvulling. De handmatige vervolgstappen uit
ronde 2 (hierboven) blijven ongewijzigd van toepassing en worden hier niet herhaald.

**Aanbeveling (herhaald, nu voor de derde keer bevestigd)**: dit issue kan niet door een volgende
automatische developer/reviewronde in deze pijplijn worden opgelost. Escaleer buiten de
developer-reviewlus: maak `deploy/secrets-acceptance.env`-inhoud en het kubeseal-clustercert
(of een kubeconfig met schrijftoegang tot de sealed-secrets-controller) beschikbaar aan een
factory-run, óf accepteer dat deze specifieke stap bewust handmatig door de repo-eigenaar wordt
uitgevoerd (zoals bij `5f494d5`) en splits de story dienovereenkomstig, zodat het ArgoCD-gedeelte
niet langer wordt geblokkeerd door een stap die dezelfde ontbrekende randvoorwaarden heeft als ronde
1 en 2.

Geen secretwaarden zijn in deze worklog, in commits of in logs terechtgekomen.

## [REVIEWER] SF-2337 — reviewronde 4 (2026-08-29)

Diff t.o.v. `main` (`git diff main...HEAD --stat`) is ongewijzigd t.o.v. ronde 1-3: alleen
`deploy/argocd/application-acceptance.yaml`, `deploy/README.md` en deze worklog. Geen wijzigingen
aan `deploy/secrets-acceptance.env`, `deploy/overlays/acceptance/acceptance-secret.yaml` of
`deploy/base/sealed-secret-runtime.yaml`.

- [info] Onafhankelijk herverifieerd (nieuwe probes, niet overgenomen uit eerdere rondes):
  `$SF_KUBECONFIG` (`/Users/robbertvdzon/okd-sno/sno/auth/kubeconfig-agent-readonly`) bestaat niet
  in deze sandbox; `kubeseal` is niet geïnstalleerd; `deploy/secrets-acceptance.env` en
  `deploy/secrets-cluster.env` bestaan niet lokaal en hebben nul commits in `git log --all`; er is
  geen sibling-checkout `../robberts-infrastructure`. Alle vier structurele belemmeringen uit ronde
  1-3 zijn dus opnieuw, voor de vierde keer, bevestigd — geen nieuw feit dat een andere uitkomst
  rechtvaardigt.
- [info] `deploy/argocd/application-acceptance.yaml` en de `deploy/README.md`-sectie zijn
  byte-voor-byte ongewijzigd t.o.v. ronde 1 en blijven structureel correct/analoog aan de
  productie-Application (`repoURL`/`targetRevision`/`syncPolicy.automated` met `prune`/`selfHeal`,
  juiste `path: deploy/overlays/acceptance` en `destination.namespace: hkh-autopilot-acceptance`).
- **[blocker]** Het niet-voorwaardelijke acceptatiecriterium — opnieuw verzegelen en committen van
  `acceptance-secret.yaml`/`sealed-secret-runtime.yaml` met de acceptatie-CORS-origin — blijft
  onvervuld. De gerapporteerde 403-bug op acceptatie is dus nog niet verholpen. Dit is inhoudelijk
  dezelfde blocker als ronde 1-3, niet een nieuwe bevinding; ik kan dit AC niet laten vallen zonder
  dat de story haar eigen bestaansreden (de CORS-fix) niet levert.
- **[info, procesobservatie, herhaald]** Vier opeenvolgende rondes (ontwikkelaar en reviewer,
  onafhankelijk van elkaar) bevestigen dezelfde structurele sandbox-beperking. Een volgende
  developerronde zal zeer waarschijnlijk dezelfde uitkomst opleveren. Herhaal de eerdere aanbeveling:
  los dit op als proces-/omgevingsvraagstuk buiten deze automatische lus (secret-/cert-injectie voor
  factory-runs beschikbaar maken, of de resealing-stap bewust als handmatige subtaak door de
  repo-eigenaar laten uitvoeren, zoals bij `5f494d5`), in plaats van dit AC te laten vallen.

Besluit: review-rejected, om dezelfde inhoudelijke reden als ronde 1-3.
