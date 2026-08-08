# hkh-7 - Worklog

Story-context bij eerste pickup:
Backenddomeinvalidatie realiseren voor één intern koppelingsdossier, met fail-closed metadata- en objectmediabeslissingen en deterministische blokkadepaden.

Stappenplan:
- [x] Issuecontext, factory-documentatie en verificatieconfig lezen.
- [x] Bestaande backendarchitectuur en testconventies inventariseren.
- [x] Zelfstandig dossierdomein, validator en resultaatcontract implementeren.
- [x] Unit- en architectuurtests toevoegen voor alle acceptatiecriteria.
- [x] Gerichte backendtests uitvoeren en bevindingen herstellen.
- [x] Self-review uitvoeren op gedrag, modulegrenzen en scope.
- [x] Volledig verplicht vangnet uitvoeren en resultaten vastleggen.

Gedaan / rationale:
- Worklog bij aanvang aangemaakt, zodat plan, keuzes en verificatie onderdeel van de PR blijven.
- Factory- en storycontext gelezen; er zijn geen leidende PO-comments die de refined story wijzigen.
- De validator blijft een pure, zelfstandig testbare Modulith-module zonder REST, opslag of externe bronraadpleging.
- Ontbrekende/onbekende objectrechten blokkeren als ongeldig verplicht veld ook metadata; geldige maar beperkende objectrechten blokkeren uitsluitend objectmedia.
- Pure domeincontracten en gecontroleerde Nederlandse waarden toegevoegd; het resultaat houdt metadata- en objectmediablokkades apart en sorteert/dedupliceert veldpaden via sets.
- De bestaande Modulith-architectuurtest uitgebreid met de nieuwe fail-closed `linking`-module.
- Gerichte validator- en architectuurtest: 25 tests groen, 0 failures, 0 errors na uitbreiding van beide restrictieve objectrechtenvarianten.
- Self-review vond geen transport-, opslag- of externe-validatiescope en geen resterende merge-markers; de verificatieconfig dekt nog exact het voorgeschreven vangnet.
- Volledig verplicht vangnet afgerond: backend `clean verify` (46 tests), frontend `analyze`, `test` (11 tests) en webbuild, plus frontend-admin `analyze` en `test` (4 tests), allemaal exitcode 0 met 0 failures en 0 errors.
