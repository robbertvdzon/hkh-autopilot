# OpenShift deployment

De fase-1-baseline wordt via ArgoCD uit `deploy/overlays/openshift` naar namespace `hkh-autopilot`
gesynchroniseerd. De set bevat de Kotlin-backend, beide Flutter-webapps en PostgreSQL 16.
OpenShift maakt voor de frontend en beheerfrontend twee TLS-routes aan. De Android-release gebruikt
de frontend-route als veilige API-ingang: de nginx-proxy stuurt `/api` en `/actuator` door naar de
private backendservice.

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
# vul de lokale, gitignored file in
./deploy/seal-secrets.sh
```

Het script schrijft alleen de versleutelde `deploy/base/sealed-secret-runtime.yaml`. Het gebruikt
het publieke certificaat uit de sibling-repository `robberts-infrastructure`, of haalt het
certificaat van de huidige cluster als die repository niet beschikbaar is.

Voor de publieke route `GET /api/historical-search` kan de Europeana-bron worden geactiveerd door
`HKH_EUROPEANA_WSKEY` in de gitignored cluster-env-file op te nemen voordat de sealed secret wordt
gegenereerd. Zonder deze server-side waarde blijft Open Archieven beschikbaar; zet de wskey nooit
in frontendconfiguratie, manifests, logs of API-responses.

## Open Archieven-configuratie

`deploy/base/open-archieven-config.yaml` is de gedeelde, niet-geheime ConfigMap voor Open Archieven.
Zowel `deploy/overlays/openshift` (productie) als `deploy/overlays/acceptance` neemt deze resource
zonder configuratiepatch over. De vastgelegde waarden zijn endpoint
`https://api.openarchieven.nl/1.1`, pad `/records/search.json`, parameters `name`, `eventplace`,
`number_show`, `start` en `archive_code` met `hee` voor Heemskerk, plus `10s` timeout, `30s`
cacheduur, `251ms` rate-limitinterval, maximaal `60` pogingen per rollende minuut, burst `10` en
refill `1.0` per seconde. Europeana blijft onafhankelijk: zonder `HKH_EUROPEANA_WSKEY` wordt alleen
die bron uitgeschakeld.

Controleer de effectieve deploymentconfiguratie met:

```bash
kubectl kustomize deploy/overlays/openshift
kubectl kustomize deploy/overlays/acceptance
```

De contracttest `Hkh195OpenArchievenConfigurationContractTest` voert dezelfde pariteitscontrole
geautomatiseerd uit. Lokale fixture- of mock-endpoints mogen via runtime-overrides worden gebruikt,
maar worden niet in deze overlays vastgelegd.

Google-login blijft bewust uitgeschakeld zolang zowel `HKH_GOOGLE_CLIENT_ID` als
`HKH_ADMIN_ALLOWED_EMAILS` leeg zijn. Voor echte login moeten dezelfde Google web-client-ID in
het clustersecret en in de GitHub Actions-variable `GOOGLE_CLIENT_ID` staan.

De backend heeft geen publieke OpenShift-Route. `backend-ingress` laat alleen verkeer van de
frontend- en adminproxy-pods toe; de proxies zetten de door de OpenShift-router aangeleverde laatste
forwarded-hop om naar één `X-Forwarded-For`-waarde. De backend vertrouwt die header bovendien alleen
voor directe peers binnen de expliciete proxyconfiguratie. `deploy/base/backend-config.yaml` gebruikt
hiervoor de OpenShift-pod-CIDR `10.128.0.0/14`, waarin de proxy-pods draaien; pas deze ConfigMap aan
als het werkelijke cluster-podnetwerk anders is. Gebruik geen algemene private-adresrange en
vertrouw geen forwarded headers buiten deze proxycontext.

De Open Archieven-zoekroute gebruikt per backendproces een tijdelijke cache (standaard 30 seconden)
en een per-IP-verzoekbudget (burst 10, maximaal 60 pogingen per rollende minuut). De cache en het
budget worden niet gedeeld met PostgreSQL of andere backendpods. De cacheduur kan via de runtime-
variabele `HKH_HISTORICAL_OPEN_ARCHIEVEN_CACHE_DURATION` worden aangepast; de trusted-proxy-configuratie
loopt via `HKH_HISTORICAL_TRUSTED_PROXY_ADDRESSES` in `backend-config.yaml`. Een wijziging van het
cluster-podnetwerk vereist aanpassing van die ConfigMap en controle van de NetworkPolicy.

## Controleren en installeren

```bash
kubectl kustomize deploy/overlays/openshift
oc apply -f deploy/argocd/application.yaml
oc get application hkh-autopilot -n argocd
oc get pods,routes -n hkh-autopilot
```

Een push op `main` bouwt alleen de gewijzigde componentimages. Daarna zet de workflow de SHA-tags
in de OpenShift-overlay; ArgoCD rolt alleen die gewijzigde deployments uit.
