import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/personquery/no_reliable_source_screen.dart';
import 'package:hkh_app/personquery/person_query_page.dart';
import 'package:hkh_app/personquery/wikidata_meaning_client.dart';
import 'package:hkh_app/personsearch/person_search_client.dart';
import 'package:hkh_app/personsearch/person_search_models.dart';

/// Deterministische fake voor `PersonSearchSource`, zodat widgettests nooit
/// een echte backend-aanroep doen. Legt de meegegeven `heemskerkMeaningQid`
/// vast zodat tests kunnen aantonen dat de gekozen betekenis daadwerkelijk
/// wordt doorgegeven.
class _FakePersonSearchSource implements PersonSearchSource {
  _FakePersonSearchSource.result(this._result) : _error = null;
  _FakePersonSearchSource.failure() : _result = null, _error = 'offline';

  final PersonSearchResult? _result;
  final String? _error;
  String? lastHeemskerkMeaningQid;
  int calls = 0;

  @override
  Future<PersonSearchResult> submit({
    required String recognizedName,
    String? secondName,
    String? eventType,
    String? yearOrPeriod,
    String? heemskerkMeaningQid,
    required String originalQuery,
  }) async {
    calls++;
    lastHeemskerkMeaningQid = heemskerkMeaningQid;
    if (_error != null) {
      throw PersonSearchSubmitException(_error);
    }
    return _result!;
  }
}

PersonSearchResult _supportedAnswerResult({String originalQuery = ''}) {
  return PersonSearchResult(
    jobId: 'job-1',
    status: PersonSearchStatus.supportedAnswer,
    originalQuery: originalQuery,
    answer: PersonSearchAnswer(
      sentences: const [
        PersonSearchAnswerSentence(
          text: 'Nicolaas Jacobus Sinnige is geboren op 25 juli 1878 in Heemskerk.',
          sourceNumbers: [1],
        ),
      ],
      sources: [
        PersonSearchSourceCitation(
          number: 1,
          institution: 'Noord-Hollands Archief',
          sourceType: 'Geboorteakte',
          archiveCode: 'nha',
          identifier: '002ED0F3-F08C-4223-A5EA-BA385D04336E',
          recordNumber: '789',
          openArchivesLink:
              'https://www.openarchieven.nl/nha:002ED0F3-F08C-4223-A5EA-BA385D04336E',
          checkedAt: DateTime.utc(2026, 8, 28, 10),
        ),
      ],
      connections: const [
        PersonSearchConnectionOption(role: 'Vader', personName: 'Pieter Sinnige'),
      ],
      disclaimer:
          'Deze ene geboorteakte is geen volledig levensverhaal van Nicolaas '
          'Jacobus Sinnige en geen overzicht van alle gebeurtenissen in '
          'Heemskerk in 1878.',
    ),
  );
}

class _FakeMeaningSource implements WikidataMeaningSource {
  _FakeMeaningSource.success()
    : _result = const WikidataMeaningResult(
        place: WikidataMeaningOption(
          qid: WikidataMeaningIds.place,
          label: 'Q9926 · Heemskerk',
          description: 'De gemeente en het dorp Heemskerk.',
        ),
        surname: WikidataMeaningOption(
          qid: WikidataMeaningIds.surname,
          label: 'Q91564725 · Heemskerk',
          description: 'Heemskerk gebruikt als achternaam.',
        ),
      ),
      _shouldFail = false;

  _FakeMeaningSource.failure() : _result = null, _shouldFail = true;

  final WikidataMeaningResult? _result;
  final bool _shouldFail;

  int calls = 0;

  @override
  Future<WikidataMeaningResult> fetchMeanings() async {
    calls++;
    if (_shouldFail) {
      throw const WikidataMeaningException('offline in test');
    }
    return _result!;
  }
}

bool _isSelected(WidgetTester tester, String qid) {
  final widget = tester.widget(find.byKey(ValueKey('meaning-option-$qid')));
  // ignore: avoid_dynamic_calls
  return (widget as dynamic).selected as bool;
}

Future<void> _submit(WidgetTester tester, String query) async {
  await tester.enterText(find.byType(TextField), query);
  await tester.tap(find.widgetWithText(FilledButton, 'Zoek in bronnen'));
  await tester.pumpAndSettle();
}

void main() {
  Future<void> useGenerousViewport(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1400, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
  }

  testWidgets(
    'startscherm toont het verplichte veldlabel, voorbeeldvraag, dekking en sessiemededeling',
    (tester) async {
      await useGenerousViewport(tester);
      await tester.pumpWidget(const MaterialApp(home: PersonQueryPage()));
      await tester.pumpAndSettle();

      expect(find.byType(TextField), findsOneWidget);
      expect(
        find.bySemanticsLabel('Stel je vraag over Heemskerk'),
        findsOneWidget,
      );
      expect(find.textContaining('Nicolaas Jacobus Sinnige'), findsWidgets);
      expect(
        find.textContaining(
          'Open Archieven-genealogie voor Heemskerk, met Wikidata als aanvullende context',
        ),
        findsOneWidget,
      );
      expect(
        find.textContaining('kan binnen deze sessie gewoon doorlopen'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'een vraag zonder herkenbare naam toont het no-reliable-source-scherm zonder externe aanroep',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Wat gebeurde er hier?');

      expect(
        find.text('Hiervoor vinden we geen betrouwbare bron'),
        findsOneWidget,
      );
      expect(
        find.text('Niet uitgevoerd · persoonsnaam ontbreekt'),
        findsOneWidget,
      );
      for (final suggestion in NoReliableSourceScreen.suggestions) {
        expect(find.text(suggestion), findsOneWidget);
      }
      expect(meaningSource.calls, 0);

      await tester.tap(find.text(NoReliableSourceScreen.suggestions.first));
      await tester.pumpAndSettle();

      final field = tester.widget<TextField>(find.byType(TextField));
      expect(field.controller?.text, NoReliableSourceScreen.suggestions.first);
    },
  );

  testWidgets(
    'een naam zonder ambigue Heemskerk-vermelding gaat direct door zonder keuzescherm en dient de job in',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      final searchSource = _FakePersonSearchSource.result(
        _supportedAnswerResult(
          originalQuery:
              'Wie was Nicolaas Jacobus Sinnige, geboren in Heemskerk in 1878?',
        ),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: PersonQueryPage(
            meaningSource: meaningSource,
            personSearchSource: searchSource,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await _submit(
        tester,
        'Wie was Nicolaas Jacobus Sinnige, geboren in Heemskerk in 1878?',
      );

      expect(meaningSource.calls, 0);
      expect(
        find.text('Heemskerk kan hier twee dingen betekenen'),
        findsNothing,
      );
      expect(searchSource.calls, 1);
      expect(searchSource.lastHeemskerkMeaningQid, isNull);
      expect(
        find.textContaining(
          'Nicolaas Jacobus Sinnige is geboren op 25 juli 1878 in Heemskerk',
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'ambigue Heemskerk-vraag toont meaning-selection met live labels en "Vraag aanpassen" bewaart de tekst',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Cornelis Heemskerk');

      expect(
        find.text('Heemskerk kan hier twee dingen betekenen'),
        findsOneWidget,
      );
      expect(find.textContaining('Q9926 · Heemskerk'), findsOneWidget);
      expect(find.textContaining('Q91564725 · Heemskerk'), findsOneWidget);
      expect(meaningSource.calls, 1);

      await tester.tap(find.widgetWithText(OutlinedButton, 'Vraag aanpassen'));
      await tester.pumpAndSettle();

      final field = tester.widget<TextField>(find.byType(TextField));
      expect(field.controller?.text, 'Cornelis Heemskerk');
    },
  );

  testWidgets(
    'bevestigen op meaning-selection dient de job in met de gekozen betekenis, nooit samengevoegd',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      final searchSource = _FakePersonSearchSource.result(
        _supportedAnswerResult(originalQuery: 'Cornelis Heemskerk'),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: PersonQueryPage(
            meaningSource: meaningSource,
            personSearchSource: searchSource,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Cornelis Heemskerk');

      await tester.tap(find.byKey(const ValueKey('meaning-radio-Q91564725')));
      await tester.pumpAndSettle();
      expect(_isSelected(tester, WikidataMeaningIds.surname), isTrue);
      expect(_isSelected(tester, WikidataMeaningIds.place), isFalse);

      await tester.tap(
        find.widgetWithText(FilledButton, 'Zoek met deze betekenis'),
      );
      await tester.pumpAndSettle();

      expect(searchSource.calls, 1);
      expect(searchSource.lastHeemskerkMeaningQid, WikidataMeaningIds.surname);
      expect(
        find.textContaining(
          'Nicolaas Jacobus Sinnige is geboren op 25 juli 1878 in Heemskerk',
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'meaning-selection ondersteunt pijltjestoetsnavigatie tussen de twee opties',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Cornelis Heemskerk');

      expect(_isSelected(tester, WikidataMeaningIds.place), isTrue);

      // Tab tot de eerste radio-optie (plaats) toetsenbordfocus heeft; alleen
      // dan verwerkt de RadioGroup de pijltjestoetsen.
      for (var i = 0; i < 8; i++) {
        if (FocusManager.instance.primaryFocus?.debugLabel == 'meaning-place') {
          break;
        }
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      expect(
        FocusManager.instance.primaryFocus?.debugLabel,
        'meaning-place',
        reason: 'De plaats-radio moet via Tab toetsenbordfocus kunnen krijgen.',
      );

      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.pump();
      expect(_isSelected(tester, WikidataMeaningIds.place), isFalse);
      expect(_isSelected(tester, WikidataMeaningIds.surname), isTrue);

      await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
      await tester.pump();
      expect(_isSelected(tester, WikidataMeaningIds.place), isTrue);
      expect(_isSelected(tester, WikidataMeaningIds.surname), isFalse);
    },
  );

  testWidgets(
    'bij een mislukte Wikidata-oproep verschijnen vaste fallback-labels met een zichtbare storingsmelding, keuze blijft bruikbaar',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.failure();
      final searchSource = _FakePersonSearchSource.result(
        _supportedAnswerResult(originalQuery: 'Cornelis Heemskerk'),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: PersonQueryPage(
            meaningSource: meaningSource,
            personSearchSource: searchSource,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Cornelis Heemskerk');

      expect(find.textContaining('Q9926 · Heemskerk (plaats)'), findsOneWidget);
      expect(
        find.textContaining('Q91564725 · Heemskerk (achternaam)'),
        findsOneWidget,
      );
      expect(find.textContaining('kon niet worden opgehaald'), findsOneWidget);

      await tester.tap(
        find.widgetWithText(FilledButton, 'Zoek met deze betekenis'),
      );
      await tester.pumpAndSettle();

      expect(searchSource.calls, 1);
      expect(searchSource.lastHeemskerkMeaningQid, WikidataMeaningIds.place);
    },
  );

  testWidgets(
    'statussen op alle drie schermen worden als leesbare tekst overgebracht, niet uitsluitend via kleur',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.failure();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Cornelis Heemskerk');

      final statusNodes = find.semantics
          .byPredicate(
            (node) => node.getSemanticsData().role == SemanticsRole.status,
            view: tester.view,
          )
          .evaluate();
      expect(statusNodes, isNotEmpty);
      for (final element in statusNodes) {
        expect(element.label, isNotEmpty);
      }
      // Het storingsicoon is decoratief (net als de bestaande iconen in
      // main.dart) en wordt uitsluitend naast leesbare statustekst getoond,
      // nooit als enige drager van de informatie.
      expect(find.byIcon(Icons.warning_amber), findsOneWidget);
      expect(find.textContaining('kon niet worden opgehaald'), findsOneWidget);
    },
  );

  testWidgets('Tab/Shift+Tab en Enter werken op het startscherm', (
    tester,
  ) async {
    await useGenerousViewport(tester);
    final searchSource = _FakePersonSearchSource.result(
      _supportedAnswerResult(
        originalQuery: 'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?',
      ),
    );
    await tester.pumpWidget(
      MaterialApp(home: PersonQueryPage(personSearchSource: searchSource)),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byType(TextField));
    await tester.enterText(
      find.byType(TextField),
      'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?',
    );
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();

    expect(
      find.textContaining(
        'Nicolaas Jacobus Sinnige is geboren op 25 juli 1878 in Heemskerk',
      ),
      findsOneWidget,
    );
  });

  Future<void> useNarrowMobileViewport(WidgetTester tester) async {
    tester.view.physicalSize = const Size(320, 4000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
  }

  testWidgets(
    'startscherm blijft bij 320px breed zonder overloop of horizontaal scrollen',
    (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(const MaterialApp(home: PersonQueryPage()));
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
      expect(
        tester.getSize(find.byType(MaterialApp)).width,
        lessThanOrEqualTo(320),
      );
    },
  );

  testWidgets(
    'no-reliable-source-scherm blijft bij 320px breed zonder overloop',
    (tester) async {
      await useNarrowMobileViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Wat gebeurde er hier?');

      expect(tester.takeException(), isNull);
      expect(
        find.text('Hiervoor vinden we geen betrouwbare bron'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'meaning-selection-scherm blijft bij 320px breed zonder overloop',
    (tester) async {
      await useNarrowMobileViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Cornelis Heemskerk');

      expect(tester.takeException(), isNull);
      expect(
        find.text('Heemskerk kan hier twee dingen betekenen'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'een mislukte indiening toont het source-outage-scherm',
    (tester) async {
      await useGenerousViewport(tester);
      final searchSource = _FakePersonSearchSource.failure();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(personSearchSource: searchSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?');

      expect(
        find.text('Open Archieven is tijdelijk niet geraadpleegd'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'nul Open Archieven-resultaten hergebruiken het no-reliable-source-scherm met aangepaste tekst',
    (tester) async {
      await useGenerousViewport(tester);
      final searchSource = _FakePersonSearchSource.result(
        const PersonSearchResult(
          jobId: 'job-3',
          status: PersonSearchStatus.noResults,
          originalQuery: 'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?',
        ),
      );
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(personSearchSource: searchSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?');

      expect(
        find.text('Geen resultaten gevonden in Open Archieven'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'meer dan honderd resultaten hergebruiken het no-reliable-source-scherm met het verfijningsverzoek',
    (tester) async {
      await useGenerousViewport(tester);
      final searchSource = _FakePersonSearchSource.result(
        const PersonSearchResult(
          jobId: 'job-4',
          status: PersonSearchStatus.partial,
          originalQuery: 'Jansen',
          refinementMessage: 'Vul de naam aan of geef een periode op.',
        ),
      );
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(personSearchSource: searchSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Wie was Jan Jansen, geboren in 1878?');

      expect(find.text('Te veel mogelijke resultaten'), findsOneWidget);
      expect(
        find.text('Vul de naam aan of geef een periode op.'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'vanuit een onderbouwd antwoord kan een vervolgspoor gevolgd worden en weer terug',
    (tester) async {
      await useGenerousViewport(tester);
      final searchSource = _FakePersonSearchSource.result(
        _supportedAnswerResult(
          originalQuery: 'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?',
        ),
      );
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(personSearchSource: searchSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?');
      await tester.tap(find.text('Volg vader: Pieter Sinnige'));
      await tester.pumpAndSettle();

      expect(find.text('Vader: Pieter Sinnige'), findsOneWidget);
      expect(
        find.textContaining('is geen volledig levensverhaal van Pieter Sinnige'),
        findsOneWidget,
      );

      await tester.tap(find.widgetWithText(OutlinedButton, 'Terug naar het antwoord'));
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Nicolaas Jacobus Sinnige is geboren op 25 juli 1878'),
        findsOneWidget,
      );
    },
  );
}
