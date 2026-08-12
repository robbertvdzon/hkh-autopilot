# hkh-93 - Worklog

Story-context bij eerste pickup:
Backend en frontend bronstatussen implementeren

Stappenplan:
[x]: issue, factory-docs en bestaande historische zoekroute gelezen
[x]: backendcontract, adapters, service en controller aanpassen
[x]: Flutter-modellen en HistoricalSearchPage aanpassen
[x]: backend- en frontendtests schrijven/bijwerken
[x]: volledig vangnet draaien en resultaten vastleggen

Voortgang:
- Worklog aangemaakt aan het begin van de developer-run; wijzigingen blijven uncommitted voor de factory.
- Het API-contract heeft `state` met `RESULTS`, `NO_RESULTS`, `PARTIAL_AVAILABILITY` en `SOURCE_FAILURE` gekregen.
- De service telt alleen nog beschikbare bronnen mee, markeert uitval tijdens paginering en schermt bronmeldingen af tegen ruwe providerinformatie.
- De frontend toont partial results met bronmeldingen en gebruikt bij volledige uitval uitsluitend de bronprobleemstatus met retry.
- Backend- en widgettests dekken de nieuwe toestanden, totalen, pagineringsuitval en semantische statusweergave.
- Volledig vangnet groen: backend 296 tests; frontend analyze, 45 tests en webbuild; frontend-admin analyze en 35 tests.
