# Deployment

De OpenShift-baseline is volledig declaratief en staat onder `deploy`. ArgoCD volgt de
`deploy/overlays/openshift`-overlay op `main` en synchroniseert die naar namespace `hkh-autopilot`.

De backend, gebruikersfrontend, adminfrontend en PostgreSQL hebben eigen workloads, services,
probes en resourcegrenzen. Alleen de drie HTTP-services krijgen een OpenShift Route. De database
blijft intern. De database gebruikt de SCL-org PostgreSQL 16-image die voor OpenShift en
willekeurige niet-root-UID's is ingericht; er is geen verruimde SCC nodig.

De fase-1-database gebruikt bewust een efemeer `emptyDir`: de applicatie is nog leeg en Flyway
bouwt het technische schema bij iedere nieuwe pod herhaalbaar op. Voordat echte historische data
wordt ingevoerd moet de runtime naar een managed PostgreSQL of een persistente StorageClass met
correcte OpenShift SELinux-labeling worden omgezet. De lokale `local-path` hostPath-StorageClass
voldoet daar niet aan.

Runtimewaarden komen uit de SealedSecret `hkh-runtime`. Alleen de gitignored bronfile
`deploy/secrets-cluster.env` bevat plaintext; zie `deploy/README.md` voor generatie en installatie.
De aparte agentingang heeft een eigen SealedSecret (`ai-access`, in previews
`ai-access-preview`) met een bijbehorende `agent-access-patch.yaml` per overlay; zie
`docs/agent-access.md`.

Omdat een gewijzigd secret op zichzelf geen nieuwe Pod-template oplevert, draagt het
backend-Deployment de annotatie `hkh.vdzonsoftware.nl/runtime-secret-checksum`. Een main-build roept
`deploy/update-runtime-secret-checksums.sh` aan, dat die annotatie gelijkzet aan de SHA-256 van het
versleutelde secretmanifest; een secret-only wijziging dwingt zo een backend-rollout af.
`deploy/verify-runtime-secret-rollout.sh` controleert dat lokaal zonder secretwaarden te lezen.

Naast productie draait er een standing acceptatieomgeving uit `deploy/overlays/acceptance` in
namespace `hkh-autopilot-acceptance`. Die wordt gesynchroniseerd door de bestaande Argo CD
Application `hkh-autopilot-acceptance` uit `robberts-infrastructure`, die `main` volgt; deze
repository bevat er bewust geen eigen Application-manifest voor.

Image-tags beginnen op `main`. Na iedere componentbuild vervangt GitHub Actions uitsluitend de
bijbehorende tag door `sha-<commit>`, commit die manifestwijziging en laat ArgoCD de rollout doen.
