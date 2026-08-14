# hkh-autopilot-28 - Worklog

Story-context bij eerste pickup:
Publieke Open Archieven-resultaatkaart koppelen aan het contract

Pas de historische Flutter-weergave en contractmapping aan rond frontend/lib/historical/historical_search.dart, met external_link_launcher_web.dart als veiligheidsgrens. Valideer voor Open Archieven source_name, stabiele identifier en absolute HTTP(S)-original_source_url; ongeldige resultaten krijgen geen kaart of link. Toon bron-, inhouds-, rechten-, privacy- en ophaalmetadata uitsluitend volgens het bestaande contract, met Onbekend voor ontbrekende of niet-herkende statussen en zonder afgeleide inhoud. Behoud bestaande bronstatussen, gedeeltelijke resultaten, nulresultaten en retrycontext. Voeg alle benodigde fixtures en Flutter-contract-/widgettests toe, inclusief toetsenbordbediening, zichtbaar linklabel en veilig openen. Voer aansluitend een self-review uit tegen de refined story en factory-conventies.

Stappenplan:
[ ]: read issue and target docs
[ ]: implement requested changes
[ ]: run relevant tests
[ ]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
