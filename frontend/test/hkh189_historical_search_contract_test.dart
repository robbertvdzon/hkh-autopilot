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
            'verwachte kaartzichtbaarheid=${matrixCase.cardVisible}';
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
      const failureCases = <MapEntry<String, String>>[
        MapEntry('TIMEOUT', 'Open Archieven reageerde niet op tijd.'),
        MapEntry('HTTP_ERROR', 'Open Archieven gaf een fout bij het opvragen.'),
        MapEntry(
          'INVALID_JSON',
          'Open Archieven stuurde een onleesbaar antwoord.',
        ),
        MapEntry(
          'MISSING_REQUIRED_FIELDS',
          'Open Archieven stuurde een onvolledig antwoord.',
        ),
      ];

      for (final failureCase in failureCases) {
        final response = _hkh189Response(
          total: 0,
          state: 'SOURCE_FAILURE',
          sources: [
            HistoricalSourceStatus(
              source: 'OPEN_ARCHIEVEN',
              status: failureCase.key,
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
            '${failureCase.key}: verwachte bronfout zonder kaart';
        expect(
          find.text(failureCase.value, skipOffstage: false),
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
    required this.cardVisible,
    required this.expectedText,
    this.forbiddenText,
  });

  final String name;
  final HistoricalSearchResponse response;
  final bool cardVisible;
  final String expectedText;
  final String? forbiddenText;
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
