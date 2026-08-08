# hkh-autopilot-3 - Worklog

Story-context bij eerste pickup:
Backend recordintake-module en frontend-admin intakeformulier met foutsamenvatting

Implementeer de Spring Modulith-module nl.vdzon.hkh.recordintake (package-info met allowedDependencies = {}, opname in ModulithArchitectureTest): een token-verifier naar het patroon van NimbusGoogleIdTokenVerifier (RS256 via injecteerbare JWKS-bron, vaste issuer/audience/scope/max-levensduur-configuratie, fail-closed 401 zonder tokenwaarde/claims in de respons of logging); een domeinmodel + validator naar het patroon van LinkDossier/LinkDossierValidator (ruwe String?-velden, parse-gebaseerde validatie, alle veldfouten verzameld met vast veldpad, geen fail-fast); een geïsoleerde privacyregel-check die vóór opslag en onafhankelijk van overige veldfouten alleen 'geen persoonsgegevens' doorlaat en anders foutcode PRIVACY_CLASSIFICATION_BLOCKED zonder opslag geeft; een nieuwe Flyway-migratie (volgend V-nummer) voor het interne conceptrecord (status intern_concept, geen media/publicatievelden) en de optionele externe conceptkoppeling (status concept, alleen aangemaakt als URL, motivering en onzekerheidswaarde alle drie geldig zijn); en een REST-controller naar het patroon van LatestNewsController die de Authorization: Bearer-header leest, eerst het token verifieert en dan het record valideert, met een respons die uitsluitend metadata bevat en nooit tokenwaarden/headers/claims. Voeg unit-, integratie- en contracttests toe voor tokenverificatie en secret-redactie, de enkel-recordlimiet, verplichte-veldenvalidatie, de fail-closed blokkade van beide persoonsgegevensclassificaties, opslag als intern_concept, de optionele koppeling en afwezigheid van media/publicatieacties. Bouw in frontend-admin een intakeformulier-component met een foutsamenvatting die na validatie de toetsenbordfocus krijgt, per fout programmatisch aan het veld gekoppeld, en communiceer succes/fout/blokkeerstatus via tekst plus een aria-live-statusgebied (bewust een ander patroon dan de bestaande passieve SemanticsRole.status-conventie); geen los invoerveld voor autorisatiebewijs; nooit tokeninhoud/claims/headers tonen. Voeg widgettests toe die de semantiekboom en focusverplaatsing controleren. Werk tot slot docs/factory/technical-spec.md en development.md (en secrets-local.md indien een nieuwe JWKS-configuratievariabele nodig is) bij met de nieuwe module, het endpoint en de migratie.

Stappenplan:
[ ]: read issue and target docs
[ ]: implement requested changes
[ ]: run relevant tests
[ ]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
