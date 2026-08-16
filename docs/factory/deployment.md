---
default_base_branch: main
branch_prefix: ai/
preview_url_template: ""
preview_namespace_template: ""
preview_db_secret_recipe: ""
---

# Deployment

Productie draait in de OpenShift-namespace `hkh-autopilot`. ArgoCD synchroniseert de manifests uit
`deploy/`; een push op `main` bouwt gewijzigde componentimages en werkt de gepinde SHA-tags bij.
De deployment bevat de Kotlin-backend, beide Flutter-webapps en PostgreSQL 16. OpenShift verzorgt
TLS-routes voor de HTTP-services.

De publieke historische zoekroute gebruikt een proceslokaal Open Archieven-cache- en verzoekbudget.
Dit is tijdelijke backendinstantiestate, niet gedeeld via PostgreSQL of een externe cache; zie
`deploy/README.md` en `docs/deployment.md` voor de proxyvertrouwensgrens en runtimeconfiguratie.

De productie-overlay `deploy/overlays/openshift` en de acceptatie-overlay
`deploy/overlays/acceptance` erven dezelfde niet-geheime ConfigMap
`deploy/base/open-archieven-config.yaml`. Dit is het canonieke contract voor endpoint
`https://api.openarchieven.nl/1.1`, pad `/records/search.json`, de parameters `name`, optioneel
`eventplace`, `number_show` en `start`, en `archive_code=hee` voor expliciete Heemskerk-zoekopdrachten.
Dezelfde ConfigMap legt ook timeout `10s`, cache `30s`, rate-limitinterval `251ms`, budget `60` per
rollende minuut, burst `10` en refill `1.0` per seconde vast. De backenddeployment importeert deze
ConfigMap via `envFrom`; overlay-specifieke patches zijn niet toegestaan. De contracttest
`Hkh195OpenArchievenConfigurationContractTest` vergelijkt de effectief gerenderde ConfigMaps van
beide overlays met het canonieke contract. Alleen lokale tests en fixtures mogen deze waarden
overschrijven.

Er is in deze repository geen stabiele, door de factory adresseerbare PR-preview-URL of
namespacetemplate vastgelegd; daarom blijven de previewvelden leeg. De bestaande infrastructuur kan
wel disposable previews met een eigen databasevolume en synthetische seeddata aanmaken. Zie
`deploy/README.md` voor clustercontrole, back-upbeleid en seedingvoorwaarden.

Platte clustersecrets worden nooit gecommit. `deploy/seal-secrets.sh` zet een lokale,
gitignored env-file om naar de versleutelde sealed-secretmanifesten.
