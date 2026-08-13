# hkh-117 - Worklog

Story-context bij eerste pickup:
Bronrelaties contractueel mappen en afzonderlijk tonen

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: run full verification safety net
[x]: update story-log with results

Done / rationale:
- Factory-task, developer-instructies, development.md en technical-spec.md gelezen.
- Bestaande historical-search-contracten, beide bronadapters en Flutter-detailweergave in kaart gebracht.
- `relationships[]` toegevoegd aan het genormaliseerde backendcontract, de REST-respons en het Fluttermodel.
- Beide adapters mappen uitsluitend complete expliciete bronrelaties met veilige HTTP(S)-doel-URI en link; metadata fail-closed verwijdert relaties bij beperkte rechten/privacy.
- Detailweergave toont bronclaims in een aparte sectie met duidelijke niet-afgeleid-tekst en een externe doelrecordlink; metadata-overlap en vervolgacties blijven afzonderlijk.
- Backendtests en Fluttertests toegevoegd voor geldige, incomplete, onveilige en verborgen relaties.
- Volledig vangnet groen: backend 303 tests; frontend analyze, 63 tests en webbuild; frontend-admin analyze en 36 tests.
