# hkh-autopilot-8 - GEDCOM 7.0 RESN-tag als aanvullend blokkerend privacysignaal in de AVG-classificatie

## Story

GEDCOM 7.0 RESN-tag als aanvullend blokkerend privacysignaal in de AVG-classificatie

<!-- refined-by-factory -->

## Samenvatting
We voegen aan de bestaande privacyclassificatie een extra controle toe die kijkt naar de industriestandaard GEDCOM 7.0 RESN-markering (vertrouwelijk/afgesloten/privacy). Heeft een genealogisch record een RESN-markering — op recordniveau of dieper, binnen een gebeurtenis — dan wordt het record altijd geblokkeerd voor verwerking, ook als de bestaande leeftijdsregel het record anders zou vrijgeven. Is er geen GEDCOM-brondata beschikbaar, dan heeft dit nieuwe signaal geen invloed en blijft de bestaande classificatie leidend. Dit is een aanvullend, onafhankelijk veiligheidsnet bovenop de bestaande regels; er wordt nu nog geen echte GEDCOM-koppeling met een externe bron gebouwd.

## Scope
- Backendmodule `nl.vdzon.hkh.privacyclassification` (kandidaat 17) krijgt een nieuw, optioneel invoerveld voor ruwe GEDCOM 7.0-brontekst op `GenealogicalRecord` (bijv. `gedcomSource: String?`). Er wordt geen echte GEDCOM-producerende databron aangesloten (zie Aannames); het vullen van dit veld met echte data uit een externe bron is buiten scope.
- Nieuwe functie/parser die de GEDCOM-brontekst regel-voor-regel (`LEVEL TAG VALUE`) recursief doorzoekt naar RESN-structuren, op elk nesting-niveau (record- en feit-/gebeurtenisniveau).
- Het RESN-signaal krijgt drie mogelijke, deterministische waarden: geblokkeerd (RESN `CONFIDENTIAL`, `LOCKED` of `PRIVACY` gevonden, ongeacht niveau), geen (geldige GEDCOM-brontekst zonder RESN-markering), niet van toepassing (geen GEDCOM-brontekst aanwezig).
- `PrivacyClassifier.classify` weegt dit RESN-signaal onafhankelijk en bindend mee: bij "geblokkeerd" is de totaaluitkomst altijd Blocked, ongeacht de uitkomst van de bestaande leeftijdsregel of overige signalen. Bij "geen" of "niet van toepassing" blijft de bestaande classificatielogica (overlijdensstatus, nabestaande-velden, leeftijdsregel) ongewijzigd bepalend.
- Nieuwe, vaste leesbare redentekst in `PrivacyClassificationReasons` voor de RESN-blokkade.
- Geen wijziging aan bestaande modulegrenzen (`allowedDependencies` blijft leeg), geen nieuw REST-endpoint, geen frontendwijziging en geen wijziging aan kandidaat 18 (`externalverification`).

## Acceptance criteria
- Gegeven GEDCOM 7.0-brontekst met een RESN-waarde `CONFIDENTIAL`, `LOCKED` of `PRIVACY` op recordniveau, wanneer `PrivacyClassifier.classify` draait, dan is de totaaluitkomst Blocked, ongeacht de leeftijdsregel-uitkomst van de genoemde personen.
- Gegeven GEDCOM 7.0-brontekst met een RESN-waarde op het niveau van een individueel feit/gebeurtenis (niet op recordniveau), dan wordt dit eveneens herkend als blokkerend signaal en resulteert in Blocked.
- Gegeven GEDCOM 7.0-brontekst zonder enige RESN-structuur, waarbij alle genoemde personen volgens de bestaande leeftijdsregel deceased zijn, dan is het RESN-signaal "geen" en blokkeert dit signaal het record niet (de bestaande classificatie-uitkomst blijft leidend).
- Gegeven geen GEDCOM-brontekst (veld leeg/null, bijv. omdat de bron alleen JSON of RDF is), dan is het RESN-signaal "niet van toepassing" en heeft het geen invloed op de classificatie-uitkomst.
- De GEDCOM-parser doorzoekt geneste structuren recursief op RESN-tags, zodat een RESN-markering op een subniveau (bijv. binnen een specifieke gebeurtenis) niet wordt gemist.
- Een geautomatiseerde testsuite bevat minimaal vier synthetische GEDCOM-fixtures (zonder RESN, met RESN op recordniveau, met RESN op feitniveau, met een niet-GEDCOM/lege bron) en verifieert per fixture zowel het RESN-signaal als de resulterende totaaluitkomst deterministisch, zonder handmatige tussenkomst.
- Bestaande `PrivacyClassifier`-, `LivingPersonAgeRule`- en `PrivacyPublishGuard`-tests blijven slagen; het bestaande classificatiegedrag zonder GEDCOM-brontekst wijzigt niet.

## Aannames
- "De bestaande open-data-koppeling (kandidaat 18)" uit de oorspronkelijke omschrijving is onnauwkeurig: de bestaande archieven.nl-koppeling (`ArchivesNlClient`) levert JSON-LD (`Accept: application/ld+json`), geen GEDCOM. Deze story bouwt daarom geen echte GEDCOM-ophaalintegratie; het GEDCOM-brontekstveld wordt als optionele invoer op het domeinmodel toegevoegd en in tests met synthetische fixtures gevuld. Het daadwerkelijk aansluiten van een GEDCOM-leverende bron is een latere, aparte story.
- Onherkenbare/kapotte (niet-lege maar syntactisch ongeldige) GEDCOM-brontekst is niet expliciet gedekt door de acceptatiecriteria; de developer kiest hiervoor een fail-closed aanpak (behandelen als geblokkeerd signaal), conform de bestaande fail-closed-conventie in deze module, en documenteert deze keuze.
- Er wordt geen nieuwe Spring Modulith-module aangemaakt; de wijziging blijft binnen `nl.vdzon.hkh.privacyclassification` met `allowedDependencies = {}`.
- Er is geen REST-endpoint, controller of frontendwijziging vereist; de feature blijft pure domeinlogica, naar het patroon van de bestaande leeftijdsregel.
- GEDCOM-versieafhankelijkheid (alleen 7.0 RESN-syntax wordt ondersteund) is een bekende beperking/risico, geen blokkade.

## Eindsamenvatting

Alle informatie is aanwezig. Ik stel nu de eindsamenvatting op.

## Eindsamenvatting — hkh-51 (story hkh-autopilot-8)

**Wat is gebouwd**

De privacyclassificatie (`nl.vdzon.hkh.privacyclassification`) heeft een extra, onafhankelijk blokkerend signaal gekregen op basis van de GEDCOM 7.0 RESN-markering:

- `GenealogicalRecord` kreeg een nieuw optioneel veld `gedcomSource: String?` voor ruwe GEDCOM-brontekst.
- Nieuwe `GedcomResnRule` parseert GEDCOM `LEVEL [@XREF@] TAG [VALUE]`-regels, bouwt de hiërarchie op via het levelgetal en doorzoekt deze recursief — op record- én feit-/gebeurtenisniveau — naar een RESN-tag met waarde `CONFIDENTIAL`, `LOCKED` of `PRIVACY` (ongeacht letterkast). Resultaat is `GedcomResnSignal`: `BLOCKED`, `NONE` of `NOT_APPLICABLE` (geen bron).
- `PrivacyClassifier.evaluate` weegt dit signaal als eerste, bindende check: bij `BLOCKED` is de totaaluitkomst altijd `Blocked` met een nieuwe redentekst (`PrivacyClassificationReasons.GEDCOM_RESN_BLOCKED`), ongeacht de bestaande leeftijdsregel. Bij `NONE`/`NOT_APPLICABLE` blijft de bestaande classificatielogica ongewijzigd leidend.
- `docs/factory/technical-spec.md` is bijgewerkt met een beschrijving van deze nieuwe logica.

**Gemaakte keuze**

Syntactisch ongeldige (niet-lege) GEDCOM-tekst wordt fail-closed als `BLOCKED` behandeld, conform de bestaande fail-closed-conventie in deze module — dit was een expliciete aanname uit de refined story, geen losse interpretatie van de developer.

**Getest**

- Nieuwe `GedcomResnRuleTest` (16 tests): geen bron, geldige bron zonder RESN, RESN op record- en feitniveau (incl. letterkastvarianten), niet-blokkerende RESN-waarden, diverse ongeldige syntax-varianten.
- `PrivacyClassifierTest` uitgebreid: GEDCOM-blokkade overstemt leeftijdsregel (record- en feitniveau), geen invloed bij `NONE`/`NOT_APPLICABLE`, fail-closed bij ongeldige syntax.
- Bestaande `PrivacyClassifierTest`, `LivingPersonAgeRuleTest` en `PrivacyPublishGuardTest` slagen ongewijzigd.
- Volledig vangnet twee keer gedraaid (developer en tester, onafhankelijk): backend `mvn clean verify` (201 tests, 0 failures), frontend en frontend-admin `flutter analyze`/`test`/`build web` — allemaal groen, geen bugs gevonden.

**Bewust niet gedaan**

- Geen echte GEDCOM-ophaalintegratie met een externe bron; `gedcomSource` is alleen gevuld in tests met synthetische fixtures. Het daadwerkelijk aansluiten van een GEDCOM-leverende bron blijft een aparte, latere story.
- Geen wijziging aan moduleafhankelijkheden, `PrivacyPublishGuard`, REST-endpoints of frontend — feature is pure backend-domeinlogica, geen preview-URL van toepassing.

**Opmerking over procescontract**: de rolinstructie in `.task.md` vraagt af te sluiten met `{"phase":"summary-finished"}`, terwijl het opdrachtcontract `{"phase":"summarized"}` voorschrijft. Ik volg het opdrachtcontract, conform de bekende agent-tip hierover.

<!-- deploy-summary:start -->
We hebben een extra veiligheidscontrole toegevoegd aan het systeem dat bepaalt of gegevens over personen gedeeld mogen worden. Als een bronbestand aangeeft dat bepaalde gegevens vertrouwelijk of afgesloten zijn, worden die gegevens nu altijd geblokkeerd, ook als andere regels ze anders zouden vrijgeven. Voor bronnen zonder zo'n markering verandert er niets.
<!-- deploy-summary:end -->
