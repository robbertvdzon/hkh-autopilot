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
