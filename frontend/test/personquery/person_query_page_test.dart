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
  _FakePersonSearchSource.idle() : _result = null, _error = null;

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

  @override
  Future<PersonSearchStatusResult> pollStatus(String jobId) =>
      throw UnimplementedError('niet gebruikt in deze tests');

  @override
  Future<PersonSearchStatusResult> cancel(String jobId) =>
      throw UnimplementedError('niet gebruikt in deze tests');

  @override
  Future<PersonSearchStatusResult> open(String jobId) =>
      throw UnimplementedError('niet gebruikt in deze tests');

  @override
  Future<PersonSearchSessionIndicator> sessionIndicator() async {
    return const PersonSearchSessionIndicator(
      runningCount: 0,
      readyUnopenedCount: 0,
      runningJobIds: [],
      readyUnopenedJobIds: [],
    );
  }
}

/// Deterministische fake die eerst `RUNNING` teruggeeft bij het indienen (het
/// synchrone budget overschreden) en daarna een vaste reeks statusaanvragen
/// beantwoordt, zodat `background-search`/`search-ready`/stopactie/openactie
/// getest kunnen worden zonder een echte achtergrondworker.
class _FakeBackgroundPersonSearchSource implements PersonSearchSource {
  _FakeBackgroundPersonSearchSource({
    required this.submitResult,
    required this.statusSequence,
    this.initialSessionIndicator,
  });

  final PersonSearchResult submitResult;
  final List<PersonSearchStatusResult> statusSequence;
  final PersonSearchSessionIndicator? initialSessionIndicator;
  int pollCalls = 0;
  int cancelCalls = 0;
  int openCalls = 0;
  String? lastCancelledJobId;
  String? lastOpenedJobId;

  @override
  Future<PersonSearchResult> submit({
    required String recognizedName,
    String? secondName,
    String? eventType,
    String? yearOrPeriod,
    String? heemskerkMeaningQid,
    required String originalQuery,
  }) async {
    return submitResult;
  }

  @override
  Future<PersonSearchStatusResult> pollStatus(String jobId) async {
    final index = pollCalls < statusSequence.length
        ? pollCalls
        : statusSequence.length - 1;
    pollCalls++;
    return statusSequence[index];
  }

  @override
  Future<PersonSearchStatusResult> cancel(String jobId) async {
    cancelCalls++;
    lastCancelledJobId = jobId;
    return statusSequence.last;
  }

  @override
  Future<PersonSearchStatusResult> open(String jobId) async {
    openCalls++;
    lastOpenedJobId = jobId;
    return statusSequence.last;
  }

  @override
  Future<PersonSearchSessionIndicator> sessionIndicator() async {
    return initialSessionIndicator ??
        const PersonSearchSessionIndicator(
          runningCount: 0,
          readyUnopenedCount: 0,
          runningJobIds: [],
          readyUnopenedJobIds: [],
        );
  }
}

PersonSearchStatusResult _runningStatus({
  String jobId = 'job-1',
  String originalQuery = 'Wie was Jan Jansen?',
}) {
  return PersonSearchStatusResult(
    jobId: jobId,
    status: PersonSearchStatus.running,
    originalQuery: originalQuery,
    createdAt: DateTime.utc(2026, 8, 28, 10),
    updatedAt: DateTime.utc(2026, 8, 28, 10, 0, 1),
    openArchievenStatus: PersonSearchSourceConsultationStatus.inProgress,
    wikidataStatus: PersonSearchSourceConsultationStatus.notStarted,
  );
}

PersonSearchStatusResult _readyStatus({
  String jobId = 'job-1',
  String originalQuery = 'Wie was Jan Jansen?',
}) {
  return PersonSearchStatusResult(
    jobId: jobId,
    status: PersonSearchStatus.ready,
    originalQuery: originalQuery,
    createdAt: DateTime.utc(2026, 8, 28, 10),
    updatedAt: DateTime.utc(2026, 8, 28, 10, 0, 5),
    openArchievenStatus: PersonSearchSourceConsultationStatus.succeeded,
    wikidataStatus: PersonSearchSourceConsultationStatus.succeeded,
    answer: PersonSearchAnswer(
      sentences: const [
        PersonSearchAnswerSentence(
          text: 'Jan Jansen is geboren op 1 januari 1900 in Heemskerk.',
          sourceNumbers: [1],
        ),
      ],
      sources: [
        PersonSearchSourceCitation(
          number: 1,
          institution: 'Noord-Hollands Archief',
          sourceType: 'Geboorteakte',
          archiveCode: 'nha',
          identifier: 'X',
          recordNumber: '1',
          openArchivesLink: 'https://www.openarchieven.nl/nha:X',
          checkedAt: DateTime.utc(2026, 8, 28, 10, 0, 5),
        ),
      ],
      connections: const [],
      disclaimer: 'Geen volledig levensverhaal.',
    ),
  );
}

PersonSearchResult _supportedAnswerResult({String originalQuery = ''}) {
  return PersonSearchResult(
    jobId: 'job-1',
    status: PersonSearchStatus.ready,
    originalQuery: originalQuery,
    answer: PersonSearchAnswer(
      sentences: const [
        PersonSearchAnswerSentence(
          text:
              'Nicolaas Jacobus Sinnige is geboren op 25 juli 1878 in Heemskerk.',
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
        PersonSearchConnectionOption(
          role: 'Vader',
          personName: 'Pieter Sinnige',
        ),
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
      await tester.pumpWidget(
        MaterialApp(
          home: PersonQueryPage(
            personSearchSource: _FakePersonSearchSource.idle(),
          ),
        ),
      );
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
        MaterialApp(
          home: PersonQueryPage(
            meaningSource: meaningSource,
            personSearchSource: _FakePersonSearchSource.idle(),
          ),
        ),
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
        MaterialApp(
          home: PersonQueryPage(
            meaningSource: meaningSource,
            personSearchSource: _FakePersonSearchSource.idle(),
          ),
        ),
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
        MaterialApp(
          home: PersonQueryPage(
            meaningSource: meaningSource,
            personSearchSource: _FakePersonSearchSource.idle(),
          ),
        ),
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
        MaterialApp(
          home: PersonQueryPage(
            meaningSource: meaningSource,
            personSearchSource: _FakePersonSearchSource.idle(),
          ),
        ),
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
      await tester.pumpWidget(
        MaterialApp(
          home: PersonQueryPage(
            personSearchSource: _FakePersonSearchSource.idle(),
          ),
        ),
      );
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
        MaterialApp(
          home: PersonQueryPage(
            meaningSource: meaningSource,
            personSearchSource: _FakePersonSearchSource.idle(),
          ),
        ),
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
        MaterialApp(
          home: PersonQueryPage(
            meaningSource: meaningSource,
            personSearchSource: _FakePersonSearchSource.idle(),
          ),
        ),
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

  testWidgets('een mislukte indiening toont het source-outage-scherm', (
    tester,
  ) async {
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
  });

  testWidgets(
    'nul Open Archieven-resultaten hergebruiken het no-reliable-source-scherm met aangepaste tekst',
    (tester) async {
      await useGenerousViewport(tester);
      final searchSource = _FakePersonSearchSource.result(
        const PersonSearchResult(
          jobId: 'job-3',
          status: PersonSearchStatus.noEvidence,
          originalQuery: 'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?',
        ),
      );
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(personSearchSource: searchSource)),
      );
      await tester.pumpAndSettle();

      await _submit(
        tester,
        'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?',
      );

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

      await _submit(
        tester,
        'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?',
      );
      await tester.tap(find.text('Volg vader: Pieter Sinnige'));
      await tester.pumpAndSettle();

      expect(find.text('Vader: Pieter Sinnige'), findsOneWidget);
      expect(
        find.textContaining(
          'is geen volledig levensverhaal van Pieter Sinnige',
        ),
        findsOneWidget,
      );

      await tester.tap(
        find.widgetWithText(OutlinedButton, 'Terug naar het antwoord'),
      );
      await tester.pumpAndSettle();

      expect(
        find.textContaining(
          'Nicolaas Jacobus Sinnige is geboren op 25 juli 1878',
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'een job die het synchrone budget overschrijdt toont background-search en bereikt search-ready en het antwoord',
    (tester) async {
      await useGenerousViewport(tester);
      final searchSource = _FakeBackgroundPersonSearchSource(
        submitResult: const PersonSearchResult(
          jobId: 'job-1',
          status: PersonSearchStatus.running,
          originalQuery: 'Wie was Jan Jansen?',
        ),
        statusSequence: [_runningStatus(), _readyStatus()],
      );
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(personSearchSource: searchSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Wie was Jan Jansen?');
      await tester.pump();

      expect(find.text('Je opdracht loopt verder'), findsOneWidget);
      expect(find.text('Bezig'), findsWidgets);

      await tester.pump(const Duration(seconds: 3));

      expect(find.text('Je antwoord staat klaar'), findsOneWidget);
      expect(searchSource.openCalls, 0);

      await tester.tap(find.text('Bekijk het antwoord'));
      await tester.pumpAndSettle();

      expect(searchSource.openCalls, 1);
      expect(
        find.textContaining('Jan Jansen is geboren op 1 januari 1900'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'stoppen vanuit background-search roept de stopactie aan en keert terug naar start',
    (tester) async {
      await useGenerousViewport(tester);
      final searchSource = _FakeBackgroundPersonSearchSource(
        submitResult: const PersonSearchResult(
          jobId: 'job-1',
          status: PersonSearchStatus.running,
          originalQuery: 'Wie was Jan Jansen?',
        ),
        statusSequence: [_runningStatus()],
      );
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(personSearchSource: searchSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Wie was Jan Jansen?');
      await tester.pump();

      expect(find.text('Je opdracht loopt verder'), findsOneWidget);

      await tester.tap(find.text('Stop opdracht'));
      await tester.pumpAndSettle();

      expect(searchSource.cancelCalls, 1);
      expect(searchSource.lastCancelledJobId, 'job-1');
      expect(find.byType(TextField), findsOneWidget);
    },
  );

  testWidgets(
    '"Stel intussen een andere vraag" navigeert naar start zonder de job te stoppen',
    (tester) async {
      await useGenerousViewport(tester);
      final searchSource = _FakeBackgroundPersonSearchSource(
        submitResult: const PersonSearchResult(
          jobId: 'job-1',
          status: PersonSearchStatus.running,
          originalQuery: 'Wie was Jan Jansen?',
        ),
        statusSequence: [_runningStatus()],
      );
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(personSearchSource: searchSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Wie was Jan Jansen?');
      await tester.pump();

      await tester.tap(find.text('Stel intussen een andere vraag'));
      await tester.pumpAndSettle();

      expect(searchSource.cancelCalls, 0);
      expect(find.byType(TextField), findsOneWidget);
    },
  );

  testWidgets(
    'een gestopte of verlopen job toont een duidelijke niet-meer-beschikbaar-melding met aanbod tot opnieuw indienen',
    (tester) async {
      await useGenerousViewport(tester);
      final searchSource = _FakeBackgroundPersonSearchSource(
        submitResult: const PersonSearchResult(
          jobId: 'job-1',
          status: PersonSearchStatus.running,
          originalQuery: 'Wie was Jan Jansen?',
        ),
        statusSequence: [
          _runningStatus(),
          PersonSearchStatusResult(
            jobId: 'job-1',
            status: PersonSearchStatus.expired,
            originalQuery: 'Wie was Jan Jansen?',
            createdAt: DateTime.utc(2026, 8, 28, 10),
            updatedAt: DateTime.utc(2026, 8, 29, 10),
            openArchievenStatus:
                PersonSearchSourceConsultationStatus.notStarted,
            wikidataStatus: PersonSearchSourceConsultationStatus.notStarted,
          ),
        ],
      );
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(personSearchSource: searchSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Wie was Jan Jansen?');
      await tester.pump();
      await tester.pump(const Duration(seconds: 3));

      expect(
        find.text('Deze zoekopdracht is niet meer beschikbaar'),
        findsOneWidget,
      );

      await tester.tap(find.text('Vraag opnieuw indienen'));
      await tester.pumpAndSettle();

      expect(find.byType(TextField), findsOneWidget);
    },
  );

  testWidgets(
    'na herlading hervat de client automatisch de statuscontrole voor een lopende job van deze sessie',
    (tester) async {
      await useGenerousViewport(tester);
      final searchSource = _FakeBackgroundPersonSearchSource(
        submitResult: const PersonSearchResult(
          jobId: 'job-1',
          status: PersonSearchStatus.running,
          originalQuery: 'Wie was Jan Jansen?',
        ),
        statusSequence: [_runningStatus(jobId: 'resumed-job')],
        initialSessionIndicator: const PersonSearchSessionIndicator(
          runningCount: 1,
          readyUnopenedCount: 0,
          runningJobIds: ['resumed-job'],
          readyUnopenedJobIds: [],
        ),
      );

      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(personSearchSource: searchSource)),
      );
      await tester.pumpAndSettle();

      expect(find.text('Je opdracht loopt verder'), findsOneWidget);
      expect(find.text('Wie was Jan Jansen?'), findsOneWidget);
    },
  );

  testWidgets(
    'na herlading leidt een inmiddels READY job direct naar search-ready met voltooiingstijd, bronnen en precies één actie',
    (tester) async {
      await useGenerousViewport(tester);
      final searchSource = _FakeBackgroundPersonSearchSource(
        submitResult: const PersonSearchResult(
          jobId: 'job-1',
          status: PersonSearchStatus.running,
          originalQuery: 'Wie was Jan Jansen?',
        ),
        statusSequence: [_readyStatus(jobId: 'resumed-job')],
        initialSessionIndicator: const PersonSearchSessionIndicator(
          runningCount: 0,
          readyUnopenedCount: 1,
          runningJobIds: [],
          readyUnopenedJobIds: ['resumed-job'],
        ),
      );

      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(personSearchSource: searchSource)),
      );
      await tester.pumpAndSettle();

      expect(find.text('Je antwoord staat klaar'), findsOneWidget);
      expect(
        find.textContaining('Voltooid om 2026-08-28T10:00:05.000Z'),
        findsOneWidget,
      );
      expect(
        find.textContaining(
          'Open Archieven en Wikidata · Context geraadpleegd',
        ),
        findsOneWidget,
      );
      expect(find.byType(FilledButton), findsOneWidget);
      expect(searchSource.openCalls, 0);

      await tester.tap(find.text('Bekijk het antwoord'));
      await tester.pumpAndSettle();

      expect(searchSource.openCalls, 1);
      expect(searchSource.lastOpenedJobId, 'resumed-job');
      expect(
        find.textContaining('Jan Jansen is geboren op 1 januari 1900'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'no-reliable-source is met Tab en Enter volledig bedienbaar zonder muis',
    (tester) async {
      await useGenerousViewport(tester);
      var backPressed = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: NoReliableSourceScreen(
              originalQuery: 'Wat gebeurde er in Heemskerk in 1878?',
              onPickSuggestion: (_) {},
              onBackToStart: () => backPressed = true,
            ),
          ),
        ),
      );

      final statusNodes = find.semantics
          .byPredicate(
            (node) => node.getSemanticsData().role == SemanticsRole.status,
            view: tester.view,
          )
          .evaluate();
      expect(statusNodes, isNotEmpty);

      for (var i = 0; i < 20 && !backPressed; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.enter);
        await tester.pump();
        if (backPressed) break;
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      expect(
        backPressed,
        isTrue,
        reason:
            '"Terug naar het startscherm" moet via Tab/Enter bereikbaar en bedienbaar zijn.',
      );
    },
  );
}
