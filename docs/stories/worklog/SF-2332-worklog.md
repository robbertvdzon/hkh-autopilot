# SF-2332 - Worklog

Story-context bij eerste pickup:
Hotfix uitvoeren

Voer de gevraagde wijziging uit zoals beschreven in de story en draai de bestaande projecttests. Geen refine/plan/review/test/documentatie-stap: dit is een hotfix.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log met resultaten

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Root cause bevestigd: `deploy/base/backend-deployment.yaml` had geen enkel mechanisme dat het
  Pod-template van de backend-Deployment laat wijzigen wanneer alleen de inhoud van `hkh-runtime`
  (productie) resp. `hkh-runtime-acceptance` (acceptatie) wijzigt; Kubernetes herstart een lopende
  Pod niet automatisch bij een gewijzigde `envFrom.secretRef`-inhoud, en `build-images.yml` triggerde
  al helemaal niet op `deploy/**`-only wijzigingen (paths-trigger bevatte alleen backend/frontend/
  frontend-admin). Vandaar dat de al gecommitte CORS- (7ca31be) en payload-key-fix (5f494d5) niet
  effectief werden op de draaiende Pod.
- `deploy/base/backend-deployment.yaml`: pod-template krijgt een nieuwe annotatie
  `hkh.vdzonsoftware.nl/runtime-secret-checksum` (placeholder `"unset"`) op
  `spec.template.metadata.annotations`, zodat elke overlay hier een eigen, per-omgeving
  checksumwaarde overheen kan patchen.
- `deploy/overlays/openshift/kustomization.yaml` en `deploy/overlays/acceptance/kustomization.yaml`:
  nieuwe JSON6902-patch (scoped op `kind: Deployment, name: backend`) die deze annotatie op een
  sha256-checksum zet. Nu al gevuld met de actuele checksum van resp.
  `deploy/base/sealed-secret-runtime.yaml` en `deploy/overlays/acceptance/acceptance-secret.yaml` —
  dat is zelf al een wijziging van het Pod-template t.o.v. de vorige main-revisie, en forceert dus
  bij de eerstvolgende ArgoCD-sync na merge één verse rollout in zowel productie als acceptatie,
  waarmee de al gecommitte CORS-/payload-key-fix meteen effectief wordt (voldoet aan AC3) zonder een
  handmatige `oc rollout restart`.
- `.github/workflows/build-images.yml`: `deploy/**` toegevoegd aan de `push`-paths-trigger (een
  secret-only commit start de workflow nu ook), plus een nieuwe stap "Force backend rollout when the
  runtime secret content changes" die bij elke push naar main de sha256-checksum van beide gesealde
  runtime-secretmanifesten herberekent en via `sed` de patchwaarde in de betreffende
  `kustomization.yaml` bijwerkt (geen ontsleuteling nodig: een checksum over het versleutelde
  manifest is voldoende om "opnieuw geseald" te detecteren). De bestaande "Commit GitOps image
  pins"-stap dekt deze twee bestanden al in zijn diff-/commit-logica, dus een secret-only wijziging
  op main leidt voortaan altijd tot een GitOps-commit die de backend-rollout triggert.
- `deploy/overlays/preview/kustomization.yaml`: `hkh-runtime` omgezet van een los, statisch
  `Secret`-manifest (`preview-secret.yaml`, verwijderd) naar een kustomize `secretGenerator` met
  dezelfde literals. Kustomize's ingebouwde content-hash-suffix genereert bij een gewijzigde literal
  automatisch een nieuwe Secret-naam én herschrijft de `envFrom.secretRef.name` van de
  backend-Deployment ernaar (geverifieerd met `kubectl kustomize deploy/overlays/preview`), wat een
  echte Pod-rollout afdwingt zonder afhankelijkheid van deze repo's GitHub Actions-workflow — de
  preview-omgeving wordt namelijk door een externe ArgoCD ApplicationSet (`robberts-infrastructure`)
  gesynchroniseerd, niet door `build-images.yml`.
- Lokaal geverifieerd met `kubectl kustomize` voor alle vier build-doelen (`deploy/base`,
  `deploy/overlays/openshift`, `deploy/overlays/acceptance`, `deploy/overlays/preview`): alle vier
  bouwen zonder fouten en tonen de verwachte annotatie- resp. secretnaamwijziging.
- Scope: uitsluitend deploy-/CI-configuratie gewijzigd (`deploy/**`,
  `.github/workflows/build-images.yml`); geen backend-, frontend- of admin-broncode aangeraakt, geen
  nieuwe lokale kopie van externe bronnen of structurele indexering toegevoegd.
- Niet zelf gedaan (bewuste aanname, buiten scope van een SF-2333-hotfixcommit): een live
  `oc rollout restart` of live curl-reproductie op de daadwerkelijke acceptatieomgeving. Deze
  hotfix-run heeft geen cluster-/productietoegang; de rollout wordt afgedwongen doordat deze commit
  zelf het Pod-template wijzigt (nieuwe checksum-annotatie), zodat de merge/deploy-vervolgstappen
  (SF-2334/SF-2335) van deze story de daadwerkelijke rollout uitvoeren zonder extra handmatige actie.
  De reproductiestappen uit bug 7fa15a55 kunnen pas na die deploy op de echte
  acceptatieomgeving herhaald worden.
- Volledig vangnet uit `development.md` gedraaid en groen: `mvn -B --no-transfer-progress clean
  verify` (256 tests, 0 failures/errors), `flutter analyze`/`flutter test`/`flutter build web` in
  `frontend/` en `flutter analyze`/`flutter test` in `frontend-admin/` (allemaal exitcode 0, geen
  issues, alle tests slagen).
