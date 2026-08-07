# Technical Spec

## Stack en componenten

- Backend: Kotlin op JDK 21, Spring Boot/Spring Modulith, Maven, PostgreSQL 16 en Flyway.
- Gebruikersfrontend: Flutter stable 3.44.7, Dart 3.12.2, Material 3, `http`; web en Android.
- Beheerfrontend: afzonderlijke Flutter-webapp.
- Deployment: containers, Kustomize/OpenShift en ArgoCD.

De backendservicecontrole combineert `GET /actuator/health` en `GET /api/version`; beide moeten
binnen tien seconden met een geldige 200-respons slagen. Nieuws komt van `GET /api/news` en heeft
dezelfde clienttimeout. `API_BASE_URL` is een compile-time Dart-define.

## Flutter-webstatussemantiek

Statussen gebruiken een eigen `Semantics`-container met `SemanticsRole.status` en exact één
betekenisvol label. Op Flutter web wordt dit de ARIA-statusrol. Die rol is volgens WAI-ARIA een
beleefde, atomische live region en hoort bij een statuswijziging geen focus te krijgen. Daarom wordt
geen aanvullende `liveRegion`-vlag, focuscallback of programmatische focus gebruikt.

Bij een zichtbare kopie vervangt `excludeSemantics: true` alleen de semantiek van die kopie; gewone
uitleg, versiegegevens, nieuwsinhoud en retryknoppen blijven afzonderlijk leesbaar. De geladen
nieuwsgroep gebruikt `explicitChildNodes: true`, zodat het statuslabel één node blijft terwijl de
berichten toegankelijk blijven. Decoratieve statusiconen zijn uitgesloten.

Bronnen voor deze keuze:

- [Flutter `Semantics`](https://api.flutter.dev/flutter/widgets/Semantics-class.html): een container
  introduceert een eigen node en `excludeSemantics` vervangt kindsemantiek;
- [WAI-ARIA 1.2 `status`](https://www.w3.org/TR/wai-aria-1.2/#status): status is impliciet
  `aria-live="polite"`, atomisch en mag door een wijziging geen focus ontvangen.

De Material-knoppen behouden de standaard Enter- en spatieactivering. Een gedeelde `ButtonStyle`
voegt voor `WidgetState.focused` een contrasterende rand van drie pixels toe: `onPrimary` op de
gevulde knop en `primary` op de omlijnde knop. De natuurlijke widgetvolgorde bepaalt lees- en
focusvolgorde; er worden geen aangepaste sort keys gebruikt.

## Verificatieconfig

`.factory/verification.yaml` gebruikt schema 1. Iedere opdracht heeft een stabiele id, een directe
`argv` zonder shell, een bestaande relatieve working directory en een begrensde timeout. Het vangnet
bestaat uit Maven `clean verify`, analyze en tests voor beide Flutter-apps en een release-webbuild
van de gebruikersfrontend. De factory voert dit na de agentrun opnieuw uit en koppelt resultaten aan
HEAD plus de worktree-tree.

Bekende valkuil: een expressiecallback als `setState(() => future = load())` retourneert de toegewezen
`Future` in debugmodus. Retrycallbacks gebruiken daarom een block-body die synchroon `void` blijft.
