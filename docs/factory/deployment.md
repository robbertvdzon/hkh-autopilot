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

Er is in deze repository geen stabiele, door de factory adresseerbare PR-preview-URL of
namespacetemplate vastgelegd; daarom blijven de previewvelden leeg. De bestaande infrastructuur kan
wel disposable previews met een eigen databasevolume en synthetische seeddata aanmaken. Zie
`deploy/README.md` voor clustercontrole, back-upbeleid en seedingvoorwaarden.

Platte clustersecrets worden nooit gecommit. `deploy/seal-secrets.sh` zet een lokale,
gitignored env-file om naar de versleutelde sealed-secretmanifesten.
