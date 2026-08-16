# Historisch Heemskerk Autopilot

Deze repository bevat de productgestuurde Historisch Heemskerk-app: een Kotlin/Spring-backend,
een Flutter-gebruikersfrontend voor web en Android, een afzonderlijke Flutter-webbeheerfrontend en
OpenShift/Kustomize-deploymentconfiguratie. De homepage van de gebruikersfrontend controleert eerst
de backendservice en toont daarna het laatste nieuws, de productvisie-ingang en de historische
zoekingang. Productvisie opent als vervolgpageroute en heeft een zichtbare terugactie naar de
bestaande homepage.

## Eerst lezen

- `.task.md`: actuele factory-taak en leidende issue-comments.
- `development.md`: repositorystructuur en het volledige verplichte vangnet.
- `technical-spec.md`: stack, statussemantiek en technische conventies.
- `functional-spec.md`: gebruikersflows en toegankelijkheidsgedrag.
- `deployment.md`: productie- en previewdeployment.
- `secrets-local.md`: namen en herkomst van lokale configuratie, nooit echte waarden.
- `agents/<rol>.md`: aanvullende instructies voor de actieve factory-rol.

Architectuurdetails buiten de factory-flow staan in `docs/architecture/`; de reguliere lokale
backendinstructies staan in `docs/development.md`.
