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

De frontend- en adminproxy zetten de laatste, door de OpenShift-router aangeleverde forwarded-hop
om naar één `X-Forwarded-For`-waarde. De backend gebruikt die header alleen voor proxy-pods; de
NetworkPolicy `backend-ingress` blokkeert overige pod- en directe route-ingress. De expliciete
proxy-CIDR in `deploy/base/backend-config.yaml` (`10.128.0.0/14`) is daarom alleen een aanvullende
peerfilter en geen publieke toegangsmethode. Bij een afwijkend OpenShift-podnetwerk moet deze
waarde worden aangepast; algemene private-adresranges of ongesaneerde forwarded headers mogen
niet worden geconfigureerd.

Image-tags beginnen op `main`. Na iedere componentbuild vervangt GitHub Actions uitsluitend de
bijbehorende tag door `sha-<commit>`, commit die manifestwijziging en laat ArgoCD de rollout doen.
