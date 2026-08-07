# hkh-autopilot-1 - Valideer Flutter-websemantiek voor statussen in de bestaande ontdekroute

## Story

Valideer Flutter-websemantiek voor statussen in de bestaande ontdekroute

<!-- refined-by-factory -->

## Samenvatting

We maken de statusmeldingen op de bestaande ontdekpagina betrouwbaar hoorbaar voor schermlezers. Ook controleren we dat opnieuw proberen volledig met het toetsenbord werkt, zonder onverwachte focusverplaatsingen of wijzigingen aan de pagina-indeling.

## Scope

- De gebruikersfrontend op de homepage (`/`) valt binnen scope.
- Beide bestaande statusstromen worden meegenomen:
  - servicecontrole: laden, fout en beschikbaar;
  - laatste nieuws: laden, fout, geladen met berichten en geladen zonder berichten.
- Elke overgang krijgt één betekenisvolle, beleefde statusmelding via Flutter-websemantiek. Een laadindicator of onderliggende tekst veroorzaakt geen tweede aankondiging.
- De twee bestaande acties met het label ‘Opnieuw proberen’ blijven op hun huidige plaats.
- Geautomatiseerde widgettests dekken de semantische status, focusvolgorde en toetsenbordactivatie.
- De daadwerkelijke Flutter-webbuild wordt daarnaast handmatig getest met minimaal één schermlezer.
- Productvisie, beheerfrontend, Android, routes, navigatie en bestaande zichtbare inhoudsvolgorde vallen buiten scope.

## Acceptance criteria

- Vóór de handmatige test zijn in het story-worklog herhaalbare scenario’s vastgelegd voor:
  - service laden naar beschikbaar;
  - service laden naar fout en na opnieuw proberen naar laden en beschikbaar;
  - laatste nieuws laden naar berichten;
  - laatste nieuws laden naar een leeg resultaat;
  - laatste nieuws laden naar fout en na opnieuw proberen naar laden en succes.
- De scenario’s beschrijven de homepage, relevante netwerkverzoeken, wijze van fout- of vertragingssimulatie en de verwachte aankondiging per overgang.
- De verwachte statusmeldingen zijn:
  - ‘De historische omgeving wordt voorbereid.’;
  - ‘De HKH-service is niet bereikbaar.’;
  - ‘Service beschikbaar.’;
  - ‘Laatste nieuws wordt geladen.’;
  - ‘Het laatste nieuws kon niet worden geladen.’;
  - ‘Laatste nieuws geladen.’ bij één of meer berichten;
  - ‘Er zijn nog geen nieuwsberichten.’ bij een leeg resultaat.
- Bij opnieuw proberen wordt eerst de bijbehorende laadstatus en daarna precies één uitkomst aangekondigd.
- Per overgang bevat de Flutter-semantiek precies één relevante live-status met de verwachte tekst. Decoratieve iconen, laadindicatoren en zichtbare kopieën leveren geen dubbele statusmelding op.
- Een statusmelding ontvangt geen focus en verplaatst de huidige toetsenbord- of toegankelijkheidsfocus niet automatisch.
- Beide acties ‘Opnieuw proberen’:
  - staan na de bijbehorende foutmelding in de natuurlijke lees- en focusvolgorde;
  - tonen een duidelijk zichtbare focusindicator;
  - zijn met Tab bereikbaar;
  - zijn afzonderlijk met Enter en met spatie te activeren.
- Geautomatiseerde tests controleren alle statusvarianten, het aantal relevante semantische statusnodes, de labels, focusvolgorde en activatie met Enter en spatie.
- Een handmatige test op een echte Flutter-webbuild vermeldt browser, besturingssysteem, schermlezer en versies, geteste build/revisie, verwachte en werkelijk gehoorde tekst, aantal aankondigingen, focusgedrag, toetsenbordresultaat en eventuele afwijkingen per scenario.
- De handmatige test gebruikt geen persoonsgegevens of nieuwe tracking en baseert de toegankelijkheidskeuzes op officiële Flutter- en W3C-richtlijnen.
- Routes, navigatie en de bestaande zichtbare inhoudsvolgorde zijn ongewijzigd.
- Het volledige vangnet uit `.factory/verification.yaml` is groen voor exact de geteste revisie.
- De developer vervangt de relevante sjabloontekst in `docs/factory/` door concrete informatie over deze repository, waaronder de frontendstructuur, build- en testcommando’s, statusstromen en Flutter-webdoelomgeving.

## Aannames

- Met de ‘bestaande ontdekroute’ wordt de homepage van de gebruikersfrontend bedoeld; de productvisiepagina is geen onderdeel van deze statusstroom.
- Een nieuwsresultaat met nul berichten is een succesvolle uitkomst met een eigen statusmelding.
- Omdat geen vaste browser-schermlezercombinatie is voorgeschreven, volstaat één gangbare desktopcombinatie mits de exacte omgeving wordt vastgelegd.
- Fouten en vertragingen mogen browserlokaal of met gecontroleerde testbronnen worden gesimuleerd; productiegegevens en serverconfiguratie hoeven niet te worden gewijzigd.
- Semantische labels mogen worden toegevoegd, maar de zichtbare teksten en hun volgorde blijven behouden.

## Eindsamenvatting

Voor de PO:

De homepage geeft nu voor alle laad-, fout-, succes- en lege nieuwssituaties precies één toegankelijke statusmelding. Retry-acties behouden hun positie, krijgen een duidelijke focusmarkering en werken met Tab, Enter en spatie zonder automatische focusverplaatsing. Routes, navigatie en zichtbare inhoudsvolgorde zijn bewust niet gewijzigd.

De widgettests dekken alle statussen, unieke meldingen, focusvolgorde en toetsenbordbediening. Het volledige ontwikkelvangnet was groen: 23 backendtests, 11 frontendtests, 4 beheertests, beide analyses en de webbuild. Browserinspectie bevestigde het status- en focusgedrag; daadwerkelijk gehoorde aankondigingen met NVDA of VoiceOver blijven, conform de PO-afspraak, expliciet onbevestigd omdat die omgevingen niet beschikbaar waren. Er zijn geen persoonsgegevens, tracking of secrets toegevoegd.

<!-- deploy-summary:start -->
Statusmeldingen op de homepage zijn nu betrouwbaarder voor schermlezers. Bij fouten kun je ‘Opnieuw proberen’ duidelijk vinden en volledig met het toetsenbord bedienen, zonder dat je focus onverwacht verspringt.
<!-- deploy-summary:end -->
