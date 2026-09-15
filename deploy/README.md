# OpenShift deployment

De fase-1-baseline wordt via ArgoCD uit `deploy/overlays/openshift` naar namespace `hkh-autopilot`
gesynchroniseerd. De set bevat de Kotlin-backend, beide Flutter-webapps en PostgreSQL 16.
OpenShift maakt voor de drie HTTP-services automatisch TLS-routes aan.

De productiedatabase gebruikt een 5Gi `local-path`-PVC op de SSD. Om 02:30 (Europe/Amsterdam)
maakt `postgres-backup` een gecontroleerde custom-format dump plus SHA-256-checksum op de externe
HDD onder `/var/mnt/external-hdd/postgres-backups/hkh-autopilot`; bestanden ouder dan dertig dagen
worden opgeruimd. Een PR-preview gebruikt een eigen disposable 1Gi-PVC. Bij verwijdering van de
previewnamespace worden PVC en PV door de bestaande preview-lifecycle opgeruimd.

Alleen in een door de backend geverifieerde PR-preview worden na Flyway automatisch de
deterministische datasets uit `PreviewDataSeeder` toegepast. `preview_seed_history` houdt per
versie bij wat al is uitgevoerd, waardoor een restart en het beveiligde endpoint
`POST /api/admin/preview/test-data/ensure` idempotent zijn. Productie kan de seeder niet starten.

## Secrets

Platte clustersecrets komen nooit in Git:

```bash
cp deploy/secrets-cluster.env.example deploy/secrets-cluster.env
# neem de eigen Runtime-consumentcredential veilig over en vul overige lokale waarden in
./deploy/configure-agent-runtime-secrets.sh
./deploy/seal-secrets.sh
```

Het sealscript schrijft de versleutelde productie- en acceptatiemanifests. Het gebruikt
het publieke certificaat uit de sibling-repository `robberts-infrastructure`, of haalt het
certificaat van de huidige cluster als die repository niet beschikbaar is.

### CORS en same-origin verkeer

`HKH_CORS_ALLOWED_ORIGIN_PATTERNS` gaat uitsluitend over echte cross-origin clients. Productie
bouwt de webapp met een ingebakken `API_BASE_URL` naar de aparte backend-route en heeft die
patronen dus nodig. Preview en acceptatie serveren de webapp en `/api` same-origin via de
frontend-nginx, dus hun eigen verkeer hangt niet van deze lijst af; ze zetten de patronen in
`deploy/overlays/{preview,acceptance}/agent-access-patch.yaml` toch expliciet voor de losse
beheerapp op haar eigen host. Die expliciete `env`-waarde wint op de backendcontainer van de
gegenereerde of verzegelde secretwaarde (`env` gaat voor `envFrom`); ruim ze dus niet op.

Een browser stuurt bij een POST ook op een same-origin verzoek een `Origin`-header mee, en Spring
beschouwt sinds Framework 6 elk verzoek met zo'n header als CORS-verzoek. Een lege of verouderde
patroonlijst blokkeerde daardoor de volledige API in de browser met 403 `Invalid CORS request` -
tweemaal eerder opgelost door het secret opnieuw te verzegelen (`7ca31be`, `3a05fc8`). De backend
lost dit nu structureel op: `SameOriginRequestFilter` herkent same-origin verzoeken en laat ze
buiten de CORS-toetsing, zodat de webapp nooit meer van deze secretwaarde afhangt. Cross-origin
verzoeken blijven fail-closed: zonder patronen wijst de backend ze af.

De herkomst van een same-origin verzoek gaat niet verloren: de filter bewaart de verborgen
`Origin`-header als requestattribuut. `AI_ACCESS_ALLOWED_ORIGINS` blijft daardoor gewoon gelden bij
`POST /api/auth/agent-session`, ook wanneer de aanmeldpagina via de frontendproxy op dezelfde
origin wordt geopend.

Google-login blijft bewust uitgeschakeld zolang zowel `HKH_GOOGLE_CLIENT_ID` als
`HKH_ADMIN_ALLOWED_EMAILS` leeg zijn. Voor echte login moeten dezelfde Google web-client-ID in
het clustersecret en in de GitHub Actions-variable `GOOGLE_CLIENT_ID` staan.

## Controleren en installeren

```bash
kubectl kustomize deploy/overlays/openshift
oc apply -f deploy/argocd/application.yaml
oc get application hkh-autopilot -n argocd
oc get pods,routes -n hkh-autopilot
```

De standing acceptatieomgeving wordt beheerd door de bestaande Argo CD Application
`hkh-autopilot-acceptance` uit de sibling-repository `robberts-infrastructure`. Die Application
volgt `main` en synchroniseert `deploy/overlays/acceptance` automatisch; deze repository bevat er
daarom bewust geen eigen Application-manifest voor. Lokaal rendert de overlay met `kubectl`, en na
een merge is de synchronisatie en backend-rollout op het cluster te controleren:

```bash
kubectl kustomize deploy/overlays/acceptance
oc get application hkh-autopilot-acceptance -n argocd
oc get deployment,pods -n hkh-autopilot-acceptance
```

`./deploy/update-runtime-secret-checksums.sh` wordt bij een main-build aangeroepen en houdt de
Pod-templatechecksum gelijk aan de SHA-256 van elk versleuteld secretmanifest. Met
`./deploy/verify-runtime-secret-rollout.sh` is dit lokaal te controleren en wordt een geïsoleerde
secret-only wijziging gesimuleerd zonder secretwaarden uit te lezen of te tonen.

Een push op `main` bouwt alleen de gewijzigde componentimages. Daarna zet de workflow de SHA-tags
in de OpenShift-overlay; ArgoCD rolt alleen die gewijzigde deployments uit.
