---
default_base_branch: main
branch_prefix: ai/
preview_url_template: "https://hkh-autopilot-pr-{pr_num}.vdzonsoftware.nl"
preview_namespace_template: "hkh-autopilot-pr-{pr_num}"
preview_db_secret_recipe: echo jdbc:postgresql://hkh_preview:hkh_preview_only@database:5432/hkh
---

# Deployment

Productie draait in de OpenShift-namespace `hkh-autopilot`. ArgoCD synchroniseert de manifests uit
`deploy/`; een push op `main` bouwt gewijzigde componentimages en werkt de gepinde SHA-tags bij.
De deployment bevat de Kotlin-backend, beide Flutter-webapps en PostgreSQL 16. OpenShift verzorgt
TLS-routes voor de HTTP-services.

De ArgoCD ApplicationSet `hkh-autopilot-previews` (in `robberts-infrastructure`) zet per open PR
automatisch een disposable preview neer via `deploy/overlays/preview`, in namespace
`hkh-autopilot-pr-<nummer>`, met een eigen (niet-gevoelige) preview-database en synthetische
seeddata. Zie `deploy/README.md` voor clustercontrole, back-upbeleid en seedingvoorwaarden.

Platte clustersecrets worden nooit gecommit. `deploy/seal-secrets.sh` zet een lokale,
gitignored env-file om naar de versleutelde sealed-secretmanifesten.

## Europeana testen

Een PR-preview gebruikt de echte HTTP-client tegen een lokale fixture, met uitsluitend synthetische
resultaten. De standaard zoekvraag levert een zichtbaar als testrecord gemarkeerd resultaat op.
Gebruik `test-leeg`, `test-storing`, `test-timeout` en `test-ongeldige-json` in een onderwerpzoekvraag
voor de andere schermtoestanden. Wikidata levert in previews bewust geen aanvullende context.
De fixture weigert te starten buiten een door de backend geverifieerde PR-preview.

Acceptatie en productie blijven de echte Europeana-koppeling gebruiken. De eigen API-key hoort in
het runtime-secret van elke omgeving; deze wordt nooit aan de tester doorgegeven. Controleer op
acceptatie naast de previewtests ook dat een echte API-aanroep en bronverwijzing werken.
