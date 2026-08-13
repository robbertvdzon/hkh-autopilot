import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/backend/backend_client.dart';
import 'package:hkh_app/backend/backend_status.dart';
import 'package:hkh_app/historical/historical_search.dart';
import 'package:hkh_app/historical/historical_context_detail.dart';
import 'package:hkh_app/historical/historical_rights_explanation.dart';
import 'package:hkh_app/main.dart';
import 'package:hkh_app/news/latest_news.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _responseJson = '''
{"results":[{"source":"OPEN_ARCHIEVEN","sourceRecordId":"a-1",
"stableUrl":"https://example.test/record/a-1","title":"Kasteel","description":"Beschrijving",
"person":"Jan","event":"Huwelijk","dateStart":"1900","dateEnd":null,
"institution":"Historisch Archief","rights":null,"privacy":null,
"retrievedAt":"2026-08-12T00:00:00Z","technicalStatus":"AVAILABLE",
"metadataRights":"UNKNOWN","objectMediaRights":"UNKNOWN","privacyStatus":"UNKNOWN"}],
"total":1,"start":0,"limit":100,
"sources":[{"source":"OPEN_ARCHIEVEN","status":"AVAILABLE","message":null}]}
''';

class _HistoricalSource implements HistoricalSearchSource {
  _HistoricalSource(this.result);

  final Future<HistoricalSearchResponse> result;
  int calls = 0;
  final starts = <int>[];

  @override
  Future<HistoricalSearchResponse> loadHistoricalSearch({
    String? text,
    String? place,
    String? person,
    String? event,
    String? fromYear,
    String? toYear,
    HistoricalSourceChoice? source,
    int start = 0,
    int limit = 100,
  }) {
    calls++;
    starts.add(start);
    return result;
  }
}

class _SequencedHistoricalSource implements HistoricalSearchSource {
  _SequencedHistoricalSource(this.responses);

  final List<Future<HistoricalSearchResponse>> responses;
  int calls = 0;

  @override
  Future<HistoricalSearchResponse> loadHistoricalSearch({
    String? text,
    String? place,
    String? person,
    String? event,
    String? fromYear,
    String? toYear,
    HistoricalSourceChoice? source,
    int start = 0,
    int limit = 100,
  }) {
    final response = responses[calls];
    calls++;
    return response;
  }
}

void main() {
  test('sends normalized historical filters and pagination', () async {
    final client = BackendClient(
      'https://example.test',
      client: MockClient((request) async {
        expect(request.url.path, '/api/historical-search');
        expect(request.url.queryParameters['q'], 'kasteel');
        expect(request.url.queryParameters['place'], 'Heemskerk');
        expect(request.url.queryParameters['person'], 'Jan');
        expect(request.url.queryParameters['event'], 'Huwelijk');
        expect(request.url.queryParameters['fromYear'], '1800');
        expect(request.url.queryParameters['toYear'], '1900');
        expect(request.url.queryParameters['source'], 'OPEN_ARCHIEVEN');
        expect(request.url.queryParameters['start'], '100');
        expect(request.url.queryParameters['limit'], '100');
        return http.Response(_responseJson, 200);
      }),
    );

    final response = await client.loadHistoricalSearch(
      text: 'kasteel',
      place: 'Heemskerk',
      person: 'Jan',
      event: 'Huwelijk',
      fromYear: '1800',
      toYear: '1900',
      source: HistoricalSourceChoice.openArchieven,
      start: 100,
    );

    expect(
      response.results.single.stableUrl,
      'https://example.test/record/a-1',
    );
    expect(response.results.single.metadataRights, 'UNKNOWN');
  });

  test('parses per-source page and Heemskerk indication counts', () {
    final response = HistoricalSearchResponse.fromJson(const <String, dynamic>{
      'results': [],
      'total': 0,
      'start': 0,
      'limit': 100,
      'state': 'NO_RESULTS',
      'sources': [
        {
          'source': 'EUROPEANA',
          'status': 'AVAILABLE',
          'message': null,
          'resultCount': 0,
          'heemskerkCount': 0,
        },
        {
          'source': 'OPEN_ARCHIEVEN',
          'status': 'TEMPORARILY_UNAVAILABLE',
          'message': 'Bron is tijdelijk niet beschikbaar.',
          'resultCount': null,
          'heemskerkCount': null,
        },
      ],
    });

    expect(response.sources[0].resultCount, 0);
    expect(response.sources[0].heemskerkCount, 0);
    expect(response.sources[1].resultCount, isNull);
    expect(response.sources[1].heemskerkCount, isNull);
  });

  testWidgets(
    'shows loading, success, fail-closed statuses and external action',
    (tester) async {
      final completer = Completer<HistoricalSearchResponse>();
      final source = _HistoricalSource(completer.future);
      await tester.pumpWidget(
        MaterialApp(home: HistoricalSearchPage(source: source)),
      );

      await tester.enterText(find.bySemanticsLabel('Vrije tekst'), 'kasteel');
      await tester.ensureVisible(
        find.byKey(const Key('historical-search-submit')),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pump();
      await tester.pump();
      expect(
        find.bySemanticsLabel('Historische zoekresultaten worden geladen.'),
        findsOneWidget,
      );

      completer.complete(
        HistoricalSearchResponse.fromJson(const <String, dynamic>{
          'results': [
            {
              'source': 'OPEN_ARCHIEVEN',
              'sourceRecordId': 'a-1',
              'stableUrl': 'https://example.test/record/a-1',
              'title': 'Kasteel',
              'description': 'Beschrijving',
              'person': 'Jan',
              'event': 'Huwelijk',
              'dateStart': '1900',
              'dateEnd': null,
              'institution': 'Historisch Archief',
              'rights': null,
              'privacy': null,
              'retrievedAt': '2026-08-12T00:00:00Z',
              'technicalStatus': 'AVAILABLE',
              'metadataRights': 'UNKNOWN',
              'objectMediaRights': 'UNKNOWN',
              'privacyStatus': 'UNKNOWN',
            },
          ],
          'total': 1,
          'start': 0,
          'limit': 100,
          'state': 'RESULTS',
          'sources': [
            {
              'source': 'OPEN_ARCHIEVEN',
              'status': 'AVAILABLE',
              'message': null,
            },
          ],
        }),
      );
      await tester.pumpAndSettle();

      expect(find.text('Kasteel'), findsNothing);
      expect(find.text('Beschrijving'), findsNothing);
      expect(find.text('Persoon: Jan'), findsNothing);
      expect(find.text('Gebeurtenis: Huwelijk'), findsNothing);
      expect(find.text('Datering: 1900'), findsNothing);
      expect(find.text('Metadatarechten: Onbekend'), findsOneWidget);
      expect(find.text('Object-/mediarechten: Onbekend'), findsOneWidget);
      expect(find.text('Privacy: Onbekend'), findsOneWidget);
      expect(find.text('Externe bron openen in nieuw tabblad'), findsOneWidget);
    },
  );

  testWidgets(
    'shows content metadata only when rights and privacy are explicit',
    (tester) async {
      final response = HistoricalSearchResponse(
        results: [
          HistoricalSearchResult(
            source: 'EUROPEANA',
            sourceRecordId: 'safe-1',
            stableUrl: 'https://example.test/safe-1',
            title: 'Veilige titel',
            description: 'Veilige beschrijving',
            person: 'Jan',
            event: 'Huwelijk',
            dateStart: '1900',
            institution: 'Archief',
            retrievedAt: DateTime.utc(2026, 8, 12),
            metadataRights: 'ALLOWED',
            privacyStatus: 'CLEAR',
          ),
        ],
        total: 1,
        start: 0,
        limit: 100,
        sources: [
          HistoricalSourceStatus(source: 'EUROPEANA', status: 'AVAILABLE'),
        ],
      );
      await tester.pumpWidget(
        MaterialApp(
          home: HistoricalSearchPage(
            source: _HistoricalSource(Future.value(response)),
          ),
        ),
      );
      await tester.ensureVisible(
        find.byKey(const Key('historical-search-submit')),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Veilige titel').first);

      expect(find.text('Veilige titel'), findsOneWidget);
      expect(find.text('Veilige beschrijving'), findsOneWidget);
      expect(find.text('Persoon: Jan'), findsOneWidget);
      expect(find.text('Datering: 1900'), findsOneWidget);
    },
  );

  testWidgets(
    'shows separate rights statuses and a keyboard accessible explanation',
    (tester) async {
      final response = HistoricalSearchResponse(
        results: [
          HistoricalSearchResult(
            source: 'EUROPEANA',
            sourceRecordId: 'rights-1',
            stableUrl: 'https://example.test/rights-1',
            retrievedAt: DateTime.utc(2026, 8, 12),
            metadataRights: 'ALLOWED',
            objectMediaRights: 'RESTRICTED',
            privacyStatus: 'CLEAR',
          ),
        ],
        total: 1,
        start: 0,
        limit: 100,
        sources: const [
          HistoricalSourceStatus(source: 'EUROPEANA', status: 'AVAILABLE'),
        ],
      );
      await tester.pumpWidget(
        MaterialApp(
          home: HistoricalSearchPage(
            source: _HistoricalSource(Future.value(response)),
          ),
        ),
      );
      await tester.ensureVisible(
        find.byKey(const Key('historical-search-submit')),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pumpAndSettle();

      expect(find.text('Metadatarechten: Toegestaan'), findsOneWidget);
      expect(find.text('Object-/mediarechten: Beperkt'), findsOneWidget);
      final toggle = find.byKey(
        const Key('historical-rights-explanation-rights-1-toggle'),
      );
      expect(toggle, findsOneWidget);
      final semantics = tester.getSemantics(toggle).getSemanticsData();
      expect(semantics.flagsCollection.isButton, isTrue);
      expect(semantics.hasAction(SemanticsAction.tap), isTrue);
      await tester.ensureVisible(toggle);

      for (
        var index = 0;
        index < 12 && !_hasPrimaryFocusWithin(toggle);
        index++
      ) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      expect(_hasPrimaryFocusWithin(toggle), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(find.text(historicalRightsExplanation), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pump();
      expect(find.text(historicalRightsExplanation), findsNothing);
    },
  );

  testWidgets('shows empty and retryable error states', (tester) async {
    final error = Completer<HistoricalSearchResponse>();
    final source = _HistoricalSource(error.future);
    await tester.pumpWidget(
      MaterialApp(home: HistoricalSearchPage(source: source)),
    );
    await tester.ensureVisible(
      find.byKey(const Key('historical-search-submit')),
    );
    await tester.tap(find.byKey(const Key('historical-search-submit')));
    await tester.pump();
    error.completeError(StateError('offline'));
    await tester.pumpAndSettle();
    expect(
      find.text('Geen historische bronnen konden worden geraadpleegd.'),
      findsOneWidget,
    );
    expect(find.byKey(const Key('historical-search-retry')), findsOneWidget);

    final empty = _HistoricalSearchResponseFactory.empty();
    await tester.pumpWidget(
      MaterialApp(
        home: HistoricalSearchPage(
          source: _HistoricalSource(Future.value(empty)),
        ),
      ),
    );
    await tester.ensureVisible(
      find.byKey(const Key('historical-search-submit')),
    );
    await tester.tap(find.byKey(const Key('historical-search-submit')));
    await tester.pumpAndSettle();
    expect(
      find.text('Geen historische resultaten gevonden.', skipOffstage: false),
      findsOneWidget,
    );
  });

  testWidgets('shows source coverage and labels the local indication', (
    tester,
  ) async {
    final response = HistoricalSearchResponse(
      results: [
        HistoricalSearchResult(
          source: 'EUROPEANA',
          sourceRecordId: 'coverage-1',
          stableUrl: 'https://example.test/coverage-1',
          retrievedAt: DateTime.utc(2026, 8, 12),
          metadataRights: 'ALLOWED',
          privacyStatus: 'CLEAR',
          title: 'Dekkingstest',
        ),
      ],
      total: 1,
      start: 0,
      limit: 100,
      sources: const [
        HistoricalSourceStatus(
          source: 'EUROPEANA',
          status: 'AVAILABLE',
          resultCount: 1,
          heemskerkCount: 1,
        ),
        HistoricalSourceStatus(
          source: 'OPEN_ARCHIEVEN',
          status: 'INVALID_RESPONSE',
        ),
      ],
    );
    await tester.pumpWidget(
      MaterialApp(
        home: HistoricalSearchPage(
          source: _HistoricalSource(Future.value(response)),
        ),
      ),
    );
    await tester.ensureVisible(
      find.byKey(const Key('historical-search-submit')),
    );
    await tester.tap(find.byKey(const Key('historical-search-submit')));
    await tester.pumpAndSettle();

    expect(
      find.text(
        'Europeana: beschikbaar, 1 resultaten. '
        'Lokale Heemskerk-indicatie op basis van plaatsmetadata: 1 '
        '(geen historisch bewijs).',
      ),
      findsOneWidget,
    );
    expect(find.text('Open Archieven: ongeldige bronrespons.'), findsOneWidget);
  });

  testWidgets(
    'uses the server page limit when a short page is paginated backwards',
    (tester) async {
      final source = _HistoricalSource(
        Future.value(
          HistoricalSearchResponse(
            results: [
              HistoricalSearchResult(
                source: 'OPEN_ARCHIEVEN',
                sourceRecordId: 'second-page',
                stableUrl: 'https://example.test/second-page',
                retrievedAt: DateTime.utc(2026, 8, 12),
                metadataRights: 'ALLOWED',
                privacyStatus: 'CLEAR',
                title: 'Tweede beschikbare pagina',
              ),
            ],
            total: 200,
            start: 100,
            limit: 100,
            state: 'PARTIAL_AVAILABILITY',
            sources: [
              HistoricalSourceStatus(
                source: 'EUROPEANA',
                status: 'TEMPORARILY_UNAVAILABLE',
              ),
              HistoricalSourceStatus(
                source: 'OPEN_ARCHIEVEN',
                status: 'AVAILABLE',
              ),
            ],
          ),
        ),
      );
      await tester.pumpWidget(
        MaterialApp(home: HistoricalSearchPage(source: source)),
      );
      await tester.ensureVisible(
        find.byKey(const Key('historical-search-submit')),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pumpAndSettle();

      expect(source.starts, [0]);
      await tester.ensureVisible(find.text('Tweede beschikbare pagina'));
      expect(find.text('Tweede beschikbare pagina'), findsOneWidget);
      final previousButton = tester.widget<OutlinedButton>(
        find
            .ancestor(
              of: find.text('Vorige resultaten'),
              matching: find.byType(OutlinedButton),
            )
            .first,
      );
      previousButton.onPressed!();
      await tester.pumpAndSettle();

      expect(source.starts, [0, 0]);
    },
  );

  testWidgets('shows validation errors without offering a retry', (
    tester,
  ) async {
    final completer = Completer<HistoricalSearchResponse>();
    await tester.pumpWidget(
      MaterialApp(
        home: HistoricalSearchPage(source: _HistoricalSource(completer.future)),
      ),
    );
    await tester.ensureVisible(
      find.byKey(const Key('historical-search-submit')),
    );
    await tester.tap(find.byKey(const Key('historical-search-submit')));
    await tester.pump();
    completer.completeError(
      const HistoricalSearchValidationException(
        'vanafjaar en eindjaar moeten samen worden opgegeven',
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('vanafjaar en eindjaar moeten samen worden opgegeven'),
      findsOneWidget,
    );
    expect(find.byKey(const Key('historical-search-retry')), findsNothing);
  });

  testWidgets(
    'shows retryable error when backend reports a source failure with HTTP 200',
    (tester) async {
      final response = HistoricalSearchResponse(
        results: [],
        total: 0,
        start: 0,
        limit: 100,
        sources: [
          HistoricalSourceStatus(
            source: 'OPEN_ARCHIEVEN',
            status: 'INVALID_RESPONSE',
            message: 'Bronrespons is ongeldig.',
          ),
        ],
      );
      final source = _HistoricalSource(Future.value(response));
      await tester.pumpWidget(
        MaterialApp(home: HistoricalSearchPage(source: source)),
      );
      await tester.ensureVisible(
        find.byKey(const Key('historical-search-submit')),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('historical-search-retry')), findsOneWidget);
      expect(find.text('Geen historische resultaten gevonden.'), findsNothing);
      expect(find.text('historische resultaten'), findsNothing);
    },
  );

  testWidgets(
    'keeps available results visible for partial source availability',
    (tester) async {
      final response = HistoricalSearchResponse(
        results: [
          HistoricalSearchResult(
            source: 'OPEN_ARCHIEVEN',
            sourceRecordId: 'partial-1',
            stableUrl: 'https://example.test/partial-1',
            retrievedAt: DateTime.utc(2026, 8, 12),
            metadataRights: 'ALLOWED',
            privacyStatus: 'CLEAR',
            title: 'Beschikbaar resultaat',
          ),
        ],
        total: 1,
        start: 0,
        limit: 100,
        state: 'PARTIAL_AVAILABILITY',
        sources: [
          HistoricalSourceStatus(source: 'EUROPEANA', status: 'DISABLED'),
          HistoricalSourceStatus(
            source: 'OPEN_ARCHIEVEN',
            status: 'AVAILABLE',
            resultCount: 1,
            heemskerkCount: 0,
          ),
        ],
      );
      await tester.pumpWidget(
        MaterialApp(
          home: HistoricalSearchPage(
            source: _HistoricalSource(Future.value(response)),
          ),
        ),
      );
      await tester.ensureVisible(
        find.byKey(const Key('historical-search-submit')),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Beschikbaar resultaat'));

      expect(find.text('Beschikbaar resultaat'), findsOneWidget);
      expect(find.text('Europeana: niet geconfigureerd.'), findsOneWidget);
      expect(find.byKey(const Key('historical-search-retry')), findsNothing);
      expect(find.text('1 historische resultaten'), findsOneWidget);
      final statusNodes = find.semantics
          .byPredicate(
            (node) => node.getSemanticsData().role == SemanticsRole.status,
          )
          .evaluate()
          .toList();
      expect(statusNodes, hasLength(1));
      expect(
        statusNodes.single.getSemanticsData().label,
        allOf(
          contains('Europeana: niet geconfigureerd.'),
          contains('Open Archieven: beschikbaar, 1 resultaten.'),
        ),
      );
    },
  );

  testWidgets(
    'reports complete source failure safely and focuses the unchanged search form',
    (tester) async {
      final source = _HistoricalSource(
        Future.value(
          const HistoricalSearchResponse(
            results: [],
            total: 0,
            start: 0,
            limit: 100,
            state: 'SOURCE_FAILURE',
            sources: [
              HistoricalSourceStatus(
                source: 'EUROPEANA',
                status: 'TEMPORARILY_UNAVAILABLE',
                message: 'provider-payload-must-not-appear',
              ),
              HistoricalSourceStatus(
                source: 'OPEN_ARCHIEVEN',
                status: 'INVALID_RESPONSE',
              ),
            ],
          ),
        ),
      );
      await tester.pumpWidget(
        MaterialApp(home: HistoricalSearchPage(source: source)),
      );
      await tester.enterText(find.bySemanticsLabel('Vrije tekst'), 'kasteel');
      await tester.enterText(
        find.bySemanticsLabel('Plek (optioneel)'),
        'Heemskerk',
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pumpAndSettle();

      expect(
        find.text(
          'Geen historische bronnen konden worden geraadpleegd.',
          skipOffstage: false,
        ),
        findsOneWidget,
      );
      expect(
        find.text(
          'Europeana: tijdelijk niet beschikbaar.',
          skipOffstage: false,
        ),
        findsOneWidget,
      );
      expect(
        find.text(
          'Open Archieven: ongeldige bronrespons.',
          skipOffstage: false,
        ),
        findsOneWidget,
      );
      expect(
        find.text('provider-payload-must-not-appear', skipOffstage: false),
        findsNothing,
      );
      expect(
        find.text('Geen historische resultaten gevonden.', skipOffstage: false),
        findsNothing,
      );
      expect(
        find.text('historische resultaten geladen.', skipOffstage: false),
        findsNothing,
      );
      expect(
        find.byKey(const Key('historical-search-retry'), skipOffstage: false),
        findsOneWidget,
      );
      expect(
        find.byKey(const Key('historical-search-adjust'), skipOffstage: false),
        findsOneWidget,
      );
      final retrySemantics = tester.getSemantics(
        find
            .byKey(const Key('historical-search-retry'), skipOffstage: false)
            .last,
      );
      final adjustSemantics = tester.getSemantics(
        find
            .byKey(const Key('historical-search-adjust'), skipOffstage: false)
            .last,
      );
      expect(
        retrySemantics.getSemanticsData().hasAction(SemanticsAction.tap),
        isTrue,
      );
      expect(
        adjustSemantics.getSemanticsData().hasAction(SemanticsAction.tap),
        isTrue,
      );

      final statusNodes = find.semantics
          .byPredicate(
            (node) => node.getSemanticsData().role == SemanticsRole.status,
          )
          .evaluate()
          .toList();
      expect(statusNodes, hasLength(1));
      expect(
        statusNodes.single.getSemanticsData().label,
        allOf(
          contains('Europeana: tijdelijk niet beschikbaar.'),
          contains('Open Archieven: ongeldige bronrespons.'),
        ),
      );

      final adjustButton = find
          .byKey(const Key('historical-search-adjust'), skipOffstage: false)
          .last;
      tester.widget<OutlinedButton>(adjustButton).onPressed!.call();
      await tester.pump();
      expect(
        _hasPrimaryFocusWithin(find.bySemanticsLabel('Vrije tekst')),
        isTrue,
      );
      expect(find.text('kasteel', skipOffstage: false), findsOneWidget);
      expect(find.text('Heemskerk', skipOffstage: false), findsOneWidget);
    },
  );

  testWidgets(
    'retry announces loading and then the new result without extra status nodes',
    (tester) async {
      final nextResponse = Completer<HistoricalSearchResponse>();
      final source = _SequencedHistoricalSource([
        Future.value(
          const HistoricalSearchResponse(
            results: [],
            total: 0,
            start: 0,
            limit: 100,
            state: 'SOURCE_FAILURE',
            sources: [
              HistoricalSourceStatus(
                source: 'OPEN_ARCHIEVEN',
                status: 'TEMPORARILY_UNAVAILABLE',
              ),
            ],
          ),
        ),
        nextResponse.future,
      ]);
      await tester.pumpWidget(
        MaterialApp(home: HistoricalSearchPage(source: source)),
      );
      await tester.tap(find.byKey(const Key('historical-search-submit')));
      await tester.pumpAndSettle();
      final retryButton = find
          .byKey(const Key('historical-search-retry'), skipOffstage: false)
          .last;
      tester.widget<OutlinedButton>(retryButton).onPressed!.call();
      await tester.pump();

      expect(
        find.bySemanticsLabel(
          'Historische zoekresultaten worden geladen.',
          skipOffstage: false,
        ),
        findsOneWidget,
      );
      var statusNodes = find.semantics
          .byPredicate(
            (node) => node.getSemanticsData().role == SemanticsRole.status,
          )
          .evaluate()
          .toList();
      expect(statusNodes, hasLength(1));
      expect(source.calls, 2);

      nextResponse.complete(
        const HistoricalSearchResponse(
          results: [],
          total: 0,
          start: 0,
          limit: 100,
          state: 'NO_RESULTS',
          sources: [
            HistoricalSourceStatus(
              source: 'OPEN_ARCHIEVEN',
              status: 'AVAILABLE',
              resultCount: 0,
              heemskerkCount: 0,
            ),
          ],
        ),
      );
      await tester.pumpAndSettle();
      statusNodes = find.semantics
          .byPredicate(
            (node) =>
                node.getSemanticsData().role == SemanticsRole.status &&
                node.getSemanticsData().label.contains('geen resultaten'),
          )
          .evaluate()
          .toList();
      expect(statusNodes, hasLength(1));
    },
  );

  testWidgets(
    'exposes status semantics and activates search and external link by keyboard',
    (tester) async {
      final response = HistoricalSearchResponse(
        results: [
          HistoricalSearchResult(
            source: 'OPEN_ARCHIEVEN',
            sourceRecordId: 'keyboard-1',
            stableUrl: 'https://example.test/keyboard-1',
            title: 'Toegankelijk resultaat',
            retrievedAt: DateTime.utc(2026, 8, 12),
            metadataRights: 'ALLOWED',
            privacyStatus: 'CLEAR',
          ),
        ],
        total: 1,
        start: 0,
        limit: 100,
        sources: [
          HistoricalSourceStatus(source: 'OPEN_ARCHIEVEN', status: 'AVAILABLE'),
        ],
      );
      final source = _HistoricalSource(Future.value(response));
      await tester.pumpWidget(
        MaterialApp(home: HistoricalSearchPage(source: source)),
      );

      for (var index = 0; index < 8; index++) {
        if (_hasPrimaryFocusWithin(
          find.byKey(const Key('historical-search-submit')),
        )) {
          break;
        }
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      expect(
        _hasPrimaryFocusWithin(
          find.byKey(const Key('historical-search-submit')),
        ),
        isTrue,
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();

      final statusNodes = find.semantics
          .byPredicate(
            (node) => node.label == '1 historische resultaten geladen.',
          )
          .evaluate()
          .toList();
      expect(statusNodes, hasLength(1));
      expect(statusNodes.single.getSemanticsData().role, SemanticsRole.status);
      final externalLink = find.byKey(const Key('historical-external-link'));
      for (
        var index = 0;
        index < 6 && !_hasPrimaryFocusWithin(externalLink);
        index++
      ) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      expect(externalLink, findsOneWidget);
      expect(
        find.bySemanticsLabel('Externe bron openen in nieuw tabblad'),
        findsOneWidget,
      );
      expect(
        tester
            .getSemantics(externalLink)
            .getSemanticsData()
            .hasAction(SemanticsAction.tap),
        isTrue,
      );
      expect(_hasPrimaryFocusWithin(externalLink), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(source.calls, 1);
    },
  );

  testWidgets('homepage exposes the independent historical search entry', (
    tester,
  ) async {
    final source = _HistoricalSource(
      Future.value(_HistoricalSearchResponseFactory.empty()),
    );
    await tester.pumpWidget(
      HkhApp(
        statusSource: _StatusSource(),
        newsSource: _NewsSource(),
        historicalSource: source,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Laatste nieuws'), findsOneWidget);
    expect(find.text('Historisch zoeken'), findsOneWidget);
  });

  testWidgets('opens context detail from an available result', (tester) async {
    final result = HistoricalSearchResult(
      source: 'OPEN_ARCHIEVEN',
      sourceRecordId: 'context-1',
      stableUrl: 'https://example.test/context-1',
      retrievedAt: DateTime.utc(2026, 8, 12),
      title: 'Kasteel',
      place: 'Heemskerk',
      person: 'Jan de Vries',
      event: 'Huwelijk',
      dateStart: '1900',
      metadataRights: 'ALLOWED',
      privacyStatus: 'CLEAR',
    );
    await tester.pumpWidget(
      MaterialApp(
        home: HistoricalSearchPage(
          source: _HistoricalSource(
            Future.value(
              HistoricalSearchResponse(
                results: [result],
                total: 1,
                start: 0,
                limit: 100,
                sources: const [
                  HistoricalSourceStatus(
                    source: 'OPEN_ARCHIEVEN',
                    status: 'AVAILABLE',
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
    await tester.ensureVisible(
      find.byKey(const Key('historical-search-submit')),
    );
    await tester.tap(find.byKey(const Key('historical-search-submit')));
    await tester.pumpAndSettle();
    await tester.ensureVisible(
      find.byKey(const Key('historical-context-action-context-1')),
    );
    tester
        .widget<TextButton>(
          find.byKey(const Key('historical-context-action-context-1')),
        )
        .onPressed!
        .call();
    await tester.pumpAndSettle();

    expect(find.text('Context van historisch zoekresultaat'), findsOneWidget);
    expect(find.text('Plaats: Heemskerk'), findsOneWidget);
    expect(find.text('Bron: Open Archieven'), findsOneWidget);
    expect(find.text('Zoekstatus: Resultaten beschikbaar'), findsOneWidget);
  });

  testWidgets('shows uncertain and missing context explicitly', (tester) async {
    final result = HistoricalSearchResult(
      source: 'OPEN_ARCHIEVEN',
      sourceRecordId: 'uncertain-1',
      stableUrl: 'https://example.test/uncertain-1',
      retrievedAt: DateTime.utc(2026, 8, 12),
      place: 'Heemskerk',
      placeStatus: HistoricalContextStatus.uncertain,
    );
    await tester.pumpWidget(
      MaterialApp(
        home: HistoricalContextDetailPage(
          result: result,
          visibleResults: [result],
          searchState: 'PARTIAL_AVAILABILITY',
          sourceStatuses: const [
            HistoricalSourceStatus(
              source: 'OPEN_ARCHIEVEN',
              status: 'TEMPORARILY_UNAVAILABLE',
            ),
          ],
        ),
      ),
    );

    expect(find.text('Plaats: Onzeker'), findsOneWidget);
    expect(find.text('Persoon: Niet beschikbaar'), findsOneWidget);
    expect(find.text('Gebeurtenis: Niet beschikbaar'), findsOneWidget);
    expect(
      find.text('Zoekstatus: Gedeeltelijke bronbeschikbaarheid'),
      findsOneWidget,
    );
    expect(find.text('Bronstatus: Tijdelijk niet beschikbaar'), findsOneWidget);
  });
}

class _HistoricalSearchResponseFactory {
  static HistoricalSearchResponse empty() => const HistoricalSearchResponse(
    results: [],
    total: 0,
    start: 0,
    limit: 100,
    sources: [
      HistoricalSourceStatus(source: 'OPEN_ARCHIEVEN', status: 'AVAILABLE'),
    ],
  );
}

class _StatusSource implements BackendStatusSource {
  @override
  Future<BackendStatus> load() async =>
      const BackendStatus(application: 'hkh', version: 'test', commit: 'test');
}

class _NewsSource implements LatestNewsSource {
  @override
  Future<NewsSearchResult> loadLatestNews({String? q, String? entity}) async =>
      const NewsSearchResult(items: [], total: 0, entities: []);
}

bool _hasPrimaryFocusWithin(Finder finder) {
  final focusContext = FocusManager.instance.primaryFocus?.context;
  if (focusContext == null) return false;
  return find
      .descendant(of: finder, matching: find.byWidget(focusContext.widget))
      .evaluate()
      .isNotEmpty;
}
