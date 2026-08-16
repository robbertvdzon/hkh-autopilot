# Deployment

De OpenShift-baseline is volledig declaratief en staat onder `deploy`. ArgoCD volgt de
`deploy/overlays/openshift`-overlay op `main` en synchroniseert die naar namespace `hkh-autopilot`.

De backend, gebruikersfrontend, adminfrontend en PostgreSQL hebben eigen workloads, services,
probes en resourcegrenzen. Alleen de gebruikersfrontend en adminfrontend krijgen een OpenShift
Route; de backend blijft uitsluitend via de frontend-proxies bereikbaar. De database blijft
intern. De database gebruikt de SCL-org PostgreSQL 16-image die voor OpenShift en willekeurige
niet-root-UID's is ingericht; er is geen verruimde SCC nodig.

De fase-1-database gebruikt bewust een efemeer `emptyDir`: de applicatie is nog leeg en Flyway
bouwt het technische schema bij iedere nieuwe pod herhaalbaar op. Voordat echte historische data
wordt ingevoerd moet de runtime naar een managed PostgreSQL of een persistente StorageClass met
correcte OpenShift SELinux-labeling worden omgezet. De lokale `local-path` hostPath-StorageClass
voldoet daar niet aan.

Runtimewaarden komen uit de SealedSecret `hkh-runtime`. Alleen de gitignored bronfile
`deploy/secrets-cluster.env` bevat plaintext; zie `deploy/README.md` voor generatie en installatie.
Voor de publieke historische zoekroute moet `HKH_EUROPEANA_WSKEY` server-side in die runtimeconfiguratie
worden gezet om Europeana te activeren. Zonder die waarde blijft de route beschikbaar met Open
Archieven als bron; de wskey wordt niet naar de frontend of API-responses doorgegeven.

## Open Archieven-configuratie

De niet-geheime, versioneerde configuratie staat centraal in
[`deploy/base/open-archieven-config.yaml`](../deploy/base/open-archieven-config.yaml). Zowel de
productie-overlay (`deploy/overlays/openshift`) als de acceptatie-overlay
(`deploy/overlays/acceptance`) erft deze ConfigMap; geen van beide overlays mag de effectieve
configuratie afzonderlijk overschrijven. De pariteit omvat endpoint
`https://api.openarchieven.nl/1.1`, pad `/records/search.json`, parameter-mapping
`name`/`eventplace`/`number_show`/`start`, Heemskerk-mapping `archive_code=hee`, timeout `10s`,
cacheduur `30s`, rate-limitinterval `251ms`, en budgetwaarden `60` per rollende minuut, burst `10`
en refill `1.0` per seconde.

De backend laadt deze waarden via de ConfigMap-variabelen met prefix
`HKH_HISTORICAL_OPEN_ARCHIEVEN_`. De configuratie bevat geen secrets, zoekwaarden of
providerpayloads. Lokale fixture- en mock-overrides blijven toegestaan voor tests en lokaal draaien,
maar horen niet in de productie- of acceptatie-overlay. De pariteitstest
`Hkh195OpenArchievenConfigurationContractTest` rendert beide effectieve overlays en vergelijkt ze
afzonderlijk met het canonieke contract.

De frontend- en adminproxy zetten de laatste, door de OpenShift-router aangeleverde forwarded-hop
om naar één `X-Forwarded-For`-waarde. De backend gebruikt die header alleen voor proxy-pods; de
NetworkPolicy `backend-ingress` blokkeert overige pod- en directe route-ingress. De expliciete
proxy-CIDR in `deploy/base/backend-config.yaml` (`10.128.0.0/14`) is daarom alleen een aanvullende
peerfilter en geen publieke toegangsmethode. Bij een afwijkend OpenShift-podnetwerk moet deze
waarde worden aangepast; algemene private-adresranges of ongesaneerde forwarded headers mogen
niet worden geconfigureerd.

Het Open Archieven-verzoekbudget en de responsecache zijn proceslokaal en tijdelijk: ze worden niet
gedeeld tussen backendpods en slaan geen zoekgeschiedenis of ruwe providerpayloads op. De cacheduur
is standaard 30 seconden en kan via `HKH_HISTORICAL_OPEN_ARCHIEVEN_CACHE_DURATION` worden ingesteld.
De per-IP-identiteit gebruikt alleen forwarded informatie vanuit de expliciet vertrouwde proxy-peers
in `HKH_HISTORICAL_TRUSTED_PROXY_ADDRESSES`; buiten die grens blijft het directe connection-IP
leidend.

Image-tags beginnen op `main`. Na iedere componentbuild vervangt GitHub Actions uitsluitend de
bijbehorende tag door `sha-<commit>`, commit die manifestwijziging en laat ArgoCD de rollout doen.
