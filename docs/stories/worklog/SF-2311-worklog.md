# SF-2311 - Worklog

Story-context bij eerste pickup:
Instapscherm, deterministische naamherkenning en Heemskerk-disambiguatie bouwen

Implementeer in frontend/lib een nieuwe personquery-module met: (1) PersonQueryInterpreter (pure Dart) die de exacte drie-staps verwijderregel (vraagwoorden -> functiewoorden/lidwoorden -> plaats-/maandnamenlijst) en de >=2-hoofdletterwoorden-naamherkenning toepast, plus voorzetsel-gebaseerde Heemskerk-disambiguatie (in/te/uit/van direct voor 'Heemskerk' op de ORIGINELE tekst => ondubbelzinnig Q9926 zonder keuzescherm; anders ambigu naast herkende naam), met resterend jaartal/periode en gebeurtenistype als losse optionele state; (2) WikidataMeaningClient die bij meaning-selection live wbsearchentities + Special:EntityData/Q9926.json + Q91564725.json ophaalt, injecteerbaar/mockbaar voor tests, met gecontroleerde fout bij netwerk-/timeout-/parsefout; (3) drie schermen met desktop- en mobile-layout volgens de aangeleverde artifacts (start, meaning-selection, no-reliable-source): start met exact één tekstinvoerveld (label 'Stel je vraag over Heemskerk'), voorbeeldvraag, dekkingsbeschrijving en sessiemededeling; meaning-selection met radiogroep Q9926/Q91564725, live labels met vaste fallback-labels + storingsmelding bij mislukking, knoppen 'Zoek met deze betekenis' en 'Vraag aanpassen' (terug naar start met tekst behouden); no-reliable-source met letterlijke tekst 'Hiervoor vinden we geen betrouwbare bron', Open Archieven als 'Niet uitgevoerd · persoonsnaam ontbreekt', en uitsluitend verfijningsvoorstellen met herkenbare naam. Alle drie schermen: Tab/Shift+Tab logische volgorde, Enter dient in/bevestigt, pijltjestoetsen tussen radio-opties op meaning-selection, zichtbare 3px-focusrand via de bestaande gedeelde ButtonStyle-conventie, status/instructies als leesbare tekst (niet uitsluitend kleur) volgens de Semantics/SemanticsRole.status-conventie uit technical-spec.md, en responsief gedrag zodat bij 320 CSS-pixels document.scrollWidth == document.clientWidth zonder horizontaal scrollen. Ontsluit de route via een nieuwe actie op de bestaande homepage (main.dart), naar het patroon van de 'Lees onze productvisie'-knop; homepage, servicecontrole en nieuwssectie blijven ongewijzigd. Geen enkele aanroep naar Open Archieven Records/Search of Records/Show. Schrijf hierbij ook de unit- en widgettests: interpreter-tests voor de startscherm-voorbeeldvraag, de epic-vraag 'Wie was Nicolaas Jacobus Sinnige, geboren in Heemskerk in 1878?', 'Cornelis Heemskerk' (ambigu), een vraag zonder herkenbare naam, en een vraag met precies één overblijvend hoofdletterwoord; widgettests voor labels/teksten, radiogroep-gedrag inclusief Wikidata-fallback, toetsenbordnavigatie en kleuronafhankelijke statusweergave; geen enkele test roept een echte Open Archieven- of Wikidata-endpoint aan.

Stappenplan:
[x]: read issue and target docs
[x]: implement requested changes
[x]: run relevant tests
[x]: update story-log with results

Done / rationale:
- Story-log aangemaakt zodat plan, voortgang en uitvoering onderdeel worden van de PR.
- Nieuwe module `frontend/lib/personquery/` toegevoegd met `PersonQueryInterpreter` (pure
  Dart, drie-staps verwijderregel + naamherkenning + voorzetsel-gebaseerde
  Heemskerk-disambiguatie), `WikidataMeaningClient`/`WikidataMeaningSource` (injecteerbaar,
  gecontroleerde fout bij netwerk-/timeout-/parsefout) en drie schermen (`start`,
  `meaning-selection`, `no-reliable-source`) met elk exact één desktop- en één
  mobile-uitwerking (breakpoint op 700 logische pixels, geen horizontale scroll bij 320px).
- Nieuwe actie "Stel je vraag over Heemskerk" toegevoegd aan de bestaande homepage
  (`frontend/lib/main.dart`), naar het patroon van de bestaande
  "Lees onze productvisie"-knop; bestaande homepage-inhoud, servicecontrole en nieuwssectie
  blijven ongewijzigd (gedekt door een bestaande én uitgebreide test).
- Unit- en widgettests toegevoegd onder `frontend/test/personquery/`: interpreter-tests voor
  de startscherm-voorbeeldvraag, de epic-vraag over Nicolaas Jacobus Sinnige, "Cornelis
  Heemskerk" (ambigu), een vraag zonder herkenbare naam en een vraag met precies één
  overblijvend hoofdletterwoord; widgettests voor labels, radiogroep-gedrag inclusief
  Wikidata-fallback, toetsenbordnavigatie (Tab/Shift+Tab/Enter/pijltjestoetsen) en
  kleuronafhankelijke statusweergave. Geen enkele test/implementatiecode roept een echte
  Open Archieven- of Wikidata-endpoint aan.
- Belangrijkste aanname (zie ook `.task.md`-Aannames): de vaste verwijderlijst uit
  normalisatiestap (c) verwijdert "Heemskerk" alleen wanneer het in de oorspronkelijke tekst
  direct wordt voorafgegaan door `in/te/uit/van` (dan is de betekenis toch al ondubbelzinnig
  plaats). In alle overige gevallen blijft "Heemskerk" als hoofdletterwoord staan, zodat het
  kan meetellen als achternaam-kandidaat in de opeenvolgende-hoofdletterwoorden-naamherkenning
  (nodig om "Cornelis Heemskerk" — expliciet AC-testvoorbeeld — als ambigu te kunnen
  herkennen; een onvoorwaardelijke verwijdering zou hier nooit een naam opleveren en de
  disambiguatie dus nooit ambigu kunnen maken).
- Volledig vangnet uit `development.md` gedraaid (backend `mvn verify`, `flutter analyze`,
  `flutter test`, `flutter build web` voor `frontend`, en `flutter analyze`/`flutter test` voor
  `frontend-admin`); alle commando's slagen met exitcode 0.
