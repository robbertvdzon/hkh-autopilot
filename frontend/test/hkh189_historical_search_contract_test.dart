import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/backend/backend_client.dart';
import 'package:hkh_app/historical/historical_search.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _hkh189ValidRouteResponse = '''
{
  "results": [{
    "source": "OPEN_ARCHIEVEN",
    "sourceRecordId": "hee:synthetic-189",
    "stableUrl": "https://synthetic.example/items/record-189",
    "source_name": "Synthetisch Archief",
    "stable_identifier": "hee:synthetic-189",
    "original_source_url": "https://synthetic.example/items/record-189",
    "title": "Synthetisch Heemskerk-resultaat",
    "retrievedAt": "2026-08-14T00:00:00Z",
    "technicalStatus": "AVAILABLE",
    "metadataRights": "ALLOWED",
    "privacyStatus": "CLEAR"
  }],
  "total": 1,
  "start": 0,
  "limit": 100,
  "state": "RESULTS",
  "sources": [{
    "source": "OPEN_ARCHIEVEN",
    "status": "AVAILABLE",
    "message": null,
    "resultCount": 1,
    "heemskerkCount": 0
  }]
}
''';

class _Hkh189StaticSource implements HistoricalSearchSource {
  const _Hkh189StaticSource(this.response);

  final HistoricalSearchResponse response;

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
  }) async => response;
}

void main() {
  test(
    'valid route fixture preserves source identity, counts and exact link',
    () async {
      final client = BackendClient(
        'https://synthetic.example',
        client: MockClient((request) async {
          expect(request.url.path, '/api/historical-search');
          return http.Response(_hkh189ValidRouteResponse, 200);
        }),
      );

      final response = await client.loadHistoricalSearch(text: 'Heemskerk');
      final result = response.results.single;

      expect(response.state, 'RESULTS');
      expect(response.total, 1);
      expect(response.sources.single.status, 'AVAILABLE');
      expect(response.sources.single.resultCount, 1);
      expect(result.normalizedSourceName, 'Synthetisch Archief');
      expect(result.normalizedStableIdentifier, 'hee:synthetic-189');
      expect(
        result.normalizedOriginalSourceUrl,
        'https://synthetic.example/items/record-189',
      );
      expect(result.isPubliclyDisplayable, isTrue);
    },
  );

  testWidgets(
    'frontend contract matrix maps statuses, counts and card visibility',
    (tester) async {
      final cases = <_Hkh189UiCase>[
        _Hkh189UiCase(
          name: 'valid Open Archieven response',
          response: _hkh189Response(
            results: [_hkh189Result(title: 'Synthetisch Heemskerk-resultaat')],
            total: 1,
            state: 'RESULTS',
            sources: const [
              HistoricalSourceStatus(
                source: 'OPEN_ARCHIEVEN',
                status: 'AVAILABLE',
                resultCount: 1,
                heemskerkCount: 0,
              ),
            ],
          ),
          expectedState: 'RESULTS',
          expectedTotal: 1,
          expectedSources: const [
            _Hkh189ExpectedSource(
              source: 'OPEN_ARCHIEVEN',
              status: 'AVAILABLE',
              resultCount: 1,
              heemskerkCount: 0,
            ),
          ],
          cardVisible: true,
          expectedText: 'Bronnaam: Synthetisch Archief',
        ),
        _Hkh189UiCase(
          name: 'valid zero result',
          response: _hkh189Response(
            total: 0,
            state: 'NO_RESULTS',
            sources: const [
              HistoricalSourceStatus(
                source: 'OPEN_ARCHIEVEN',
                status: 'AVAILABLE',
                resultCount: 0,
                heemskerkCount: 0,
              ),
            ],
          ),
          expectedState: 'NO_RESULTS',
          expectedTotal: 0,
          expectedSources: const [
            _Hkh189ExpectedSource(
              source: 'OPEN_ARCHIEVEN',
              status: 'AVAILABLE',
              resultCount: 0,
              heemskerkCount: 0,
            ),
          ],
          cardVisible: false,
          expectedText: 'Geen historische resultaten gevonden.',
        ),
        _Hkh189UiCase(
          name: 'partial availability',
          response: _hkh189Response(
            results: [_hkh189Result(title: 'Beschikbaar deelresultaat')],
            total: 1,
            state: 'PARTIAL_AVAILABILITY',
            sources: const [
              HistoricalSourceStatus(
                source: 'EUROPEANA',
                status: 'TEMPORARILY_UNAVAILABLE',
              ),
              HistoricalSourceStatus(
                source: 'OPEN_ARCHIEVEN',
                status: 'AVAILABLE',
                resultCount: 1,
                heemskerkCount: 1,
              ),
            ],
          ),
          expectedState: 'PARTIAL_AVAILABILITY',
          expectedTotal: 1,
          expectedSources: const [
            _Hkh189ExpectedSource(
              source: 'EUROPEANA',
              status: 'TEMPORARILY_UNAVAILABLE',
            ),
            _Hkh189ExpectedSource(
              source: 'OPEN_ARCHIEVEN',
              status: 'AVAILABLE',
              resultCount: 1,
              heemskerkCount: 1,
            ),
          ],
          cardVisible: true,
          expectedText: 'Europeana: tijdelijk niet beschikbaar.',
        ),
        _Hkh189UiCase(
          name: 'missing rights and privacy metadata',
          response: _hkh189Response(
            results: [_hkh189Result(title: 'provider-secret-title')],
            total: 1,
            state: 'RESULTS',
            sources: const [
              HistoricalSourceStatus(
                source: 'OPEN_ARCHIEVEN',
                status: 'AVAILABLE',
                resultCount: 1,
              ),
            ],
          ),
          expectedState: 'RESULTS',
          expectedTotal: 1,
          expectedSources: const [
            _Hkh189ExpectedSource(
              source: 'OPEN_ARCHIEVEN',
              status: 'AVAILABLE',
              resultCount: 1,
            ),
          ],
          cardVisible: true,
          expectedText: 'Metadatarechten: Onbekend',
          forbiddenText: 'provider-secret-title',
        ),
      ];

      for (final matrixCase in cases) {
        await tester.pumpWidget(
          MaterialApp(
            home: HistoricalSearchPage(
              source: _Hkh189StaticSource(matrixCase.response),
            ),
          ),
        );
        await tester.ensureVisible(
          find.byKey(const Key('historical-search-submit')),
        );
        await tester.tap(find.byKey(const Key('historical-search-submit')));
        await tester.pumpAndSettle();

        final diagnostic =
            '${matrixCase.name}: '
            'verwachte state=${matrixCase.expectedState}, '
            'totaal=${matrixCase.expectedTotal}, '
            'bronnen=${matrixCase.expectedSources}, '
            'kaartzichtbaarheid=${matrixCase.cardVisible}';
        _expectHkh189ResponseContract(
          matrixCase.response,
          matrixCase,
          diagnostic,
        );
        expect(
          find.text(matrixCase.expectedText, skipOffstage: false),
          findsOneWidget,
          reason: diagnostic,
        );
        expect(
          find.byKey(
            const Key('historical-result-card-hee:synthetic-189'),
            skipOffstage: false,
          ),
          matrixCase.cardVisible ? findsOneWidget : findsNothing,
          reason: diagnostic,
        );
        if (matrixCase.expectedState == 'NO_RESULTS') {
          expect(
            find.text(
              'Geen historische resultaten gevonden.',
              skipOffstage: false,
            ),
            findsOneWidget,
            reason: diagnostic,
          );
        } else {
          expect(
            find.text(
              '${matrixCase.expectedTotal} historische resultaten',
              skipOffstage: false,
            ),
            findsOneWidget,
            reason: diagnostic,
          );
        }
        for (final expectedSource in matrixCase.expectedSources) {
          expect(
            find.text(
              _hkh189VisibleSourceSummary(expectedSource),
              skipOffstage: false,
            ),
            findsOneWidget,
            reason: '$diagnostic; zichtbare bronstatus/telling',
          );
        }
        if (matrixCase.cardVisible) {
          expect(
            find.text('Bronidentifier: hee:synthetic-189', skipOffstage: false),
            findsOneWidget,
          );
          expect(
            find.text(
              'Externe bron openen in nieuw tabblad',
              skipOffstage: false,
            ),
            findsOneWidget,
          );
        } else {
          expect(
            find.text(
              'Externe bron openen in nieuw tabblad',
              skipOffstage: false,
            ),
            findsNothing,
          );
        }
        if (matrixCase.forbiddenText != null) {
          expect(
            find.text(matrixCase.forbiddenText!, skipOffstage: false),
            findsNothing,
            reason: diagnostic,
          );
          expect(
            find.text('Object-/mediarechten: Onbekend', skipOffstage: false),
            findsOneWidget,
          );
          expect(
            find.text('Privacy: Onbekend', skipOffstage: false),
            findsOneWidget,
          );
        }
      }
    },
  );

  testWidgets(
    'frontend renders safe messages for every source failure status',
    (tester) async {
      const failureCases = <_Hkh189FailureCase>[
        _Hkh189FailureCase(
          status: 'TIMEOUT',
          message: 'Open Archieven reageerde niet op tijd.',
        ),
        _Hkh189FailureCase(
          status: 'HTTP_ERROR',
          message: 'Open Archieven gaf een fout bij het opvragen.',
        ),
        _Hkh189FailureCase(
          status: 'INVALID_JSON',
          message: 'Open Archieven stuurde een onleesbaar antwoord.',
        ),
        _Hkh189FailureCase(
          status: 'MISSING_REQUIRED_FIELDS',
          message: 'Open Archieven stuurde een onvolledig antwoord.',
        ),
      ];

      for (final failureCase in failureCases) {
        final response = _hkh189Response(
          total: 0,
          state: 'SOURCE_FAILURE',
          sources: [
            HistoricalSourceStatus(
              source: 'OPEN_ARCHIEVEN',
              status: failureCase.status,
              message: 'provider-secret-diagnostic',
            ),
          ],
        );
        await tester.pumpWidget(
          MaterialApp(
            home: HistoricalSearchPage(source: _Hkh189StaticSource(response)),
          ),
        );
        await tester.tap(find.byKey(const Key('historical-search-submit')));
        await tester.pumpAndSettle();

        final diagnostic =
            '${failureCase.status}: verwachte state=SOURCE_FAILURE, '
            'totaal=0, bronstatus=${failureCase.status}, telling=null, '
            'kaartzichtbaarheid=false';
        _expectHkh189ResponseContract(
          response,
          _Hkh189UiCase(
            name: failureCase.status,
            response: response,
            expectedState: 'SOURCE_FAILURE',
            expectedTotal: 0,
            expectedSources: [
              _Hkh189ExpectedSource(
                source: 'OPEN_ARCHIEVEN',
                status: failureCase.status,
              ),
            ],
            cardVisible: false,
            expectedText: failureCase.message,
          ),
          diagnostic,
        );
        expect(
          find.text(failureCase.message, skipOffstage: false),
          findsOneWidget,
          reason: diagnostic,
        );
        expect(
          find.text('provider-secret-diagnostic', skipOffstage: false),
          findsNothing,
          reason: diagnostic,
        );
        expect(
          find.byKey(const Key('historical-search-retry'), skipOffstage: false),
          findsOneWidget,
        );
        expect(
          find.byKey(
            const Key('historical-result-card-hee:synthetic-189'),
            skipOffstage: false,
          ),
          findsNothing,
          reason: diagnostic,
        );
      }
    },
  );
}

class _Hkh189UiCase {
  const _Hkh189UiCase({
    required this.name,
    required this.response,
    required this.expectedState,
    required this.expectedTotal,
    required this.expectedSources,
    required this.cardVisible,
    required this.expectedText,
    this.forbiddenText,
  });

  final String name;
  final HistoricalSearchResponse response;
  final String expectedState;
  final int expectedTotal;
  final List<_Hkh189ExpectedSource> expectedSources;
  final bool cardVisible;
  final String expectedText;
  final String? forbiddenText;
}

class _Hkh189ExpectedSource {
  const _Hkh189ExpectedSource({
    required this.source,
    required this.status,
    this.resultCount,
    this.heemskerkCount,
  });

  final String source;
  final String status;
  final int? resultCount;
  final int? heemskerkCount;
}

class _Hkh189FailureCase {
  const _Hkh189FailureCase({required this.status, required this.message});

  final String status;
  final String message;
}

void _expectHkh189ResponseContract(
  HistoricalSearchResponse response,
  _Hkh189UiCase matrixCase,
  String diagnostic,
) {
  expect(response.state, matrixCase.expectedState, reason: diagnostic);
  expect(response.total, matrixCase.expectedTotal, reason: diagnostic);
  expect(
    response.sources,
    hasLength(matrixCase.expectedSources.length),
    reason: diagnostic,
  );
  for (final expectedSource in matrixCase.expectedSources) {
    final actualSource = response.sources.singleWhere(
      (source) => source.source == expectedSource.source,
      orElse: () => throw TestFailure(
        '$diagnostic; bron ${expectedSource.source} ontbreekt',
      ),
    );
    expect(actualSource.status, expectedSource.status, reason: diagnostic);
    expect(
      actualSource.resultCount,
      expectedSource.resultCount,
      reason: '$diagnostic; resultCount voor ${expectedSource.source}',
    );
    expect(
      actualSource.heemskerkCount,
      expectedSource.heemskerkCount,
      reason: '$diagnostic; heemskerkCount voor ${expectedSource.source}',
    );
  }
}

String _hkh189VisibleSourceSummary(_Hkh189ExpectedSource source) {
  final name = switch (source.source) {
    'EUROPEANA' => 'Europeana',
    'OPEN_ARCHIEVEN' => 'Open Archieven',
    _ => source.source,
  };
  if (source.status != 'AVAILABLE') {
    final message = switch (source.status) {
      'TEMPORARILY_UNAVAILABLE' => 'tijdelijk niet beschikbaar',
      _ => 'niet beschikbaar',
    };
    return '$name: $message.';
  }
  if (source.resultCount == null || source.heemskerkCount == null) {
    return '$name: beschikbaar.';
  }
  return '$name: beschikbaar, ${source.resultCount} resultaten. '
      'Lokale Heemskerk-indicatie op basis van plaatsmetadata: '
      '${source.heemskerkCount} (geen historisch bewijs).';
}

HistoricalSearchResponse _hkh189Response({
  List<HistoricalSearchResult> results = const [],
  required int total,
  required String state,
  required List<HistoricalSourceStatus> sources,
}) => HistoricalSearchResponse(
  results: results,
  total: total,
  start: 0,
  limit: 100,
  state: state,
  sources: sources,
);

HistoricalSearchResult _hkh189Result({String? title}) => HistoricalSearchResult(
  source: 'OPEN_ARCHIEVEN',
  sourceRecordId: 'hee:synthetic-189',
  stableUrl: 'https://synthetic.example/items/record-189',
  sourceName: 'Synthetisch Archief',
  stableIdentifier: 'hee:synthetic-189',
  originalSourceUrl: 'https://synthetic.example/items/record-189',
  title: title,
  retrievedAt: DateTime.utc(2026, 8, 14),
);
