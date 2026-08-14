import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/historical/historical_search.dart';

class _Hkh171Source implements HistoricalSearchSource {
  _Hkh171Source(this.response);

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

Map<String, dynamic> _openArchievenFixture({
  String? sourceName = 'Noord-Hollands Archief',
  String? stableIdentifier = 'hee:hkh171-1',
  String? originalSourceUrl = 'https://www.openarchieven.nl/hee:hkh171-1',
  String? title = 'Huwelijk van Jan Jansen',
  String? description = 'Primaire bronbeschrijving',
  String metadataRights = 'ALLOWED',
  String objectMediaRights = 'RESTRICTED',
  String privacyStatus = 'CLEAR',
}) => {
  'source': 'OPEN_ARCHIEVEN',
  'sourceRecordId': stableIdentifier,
  'stableUrl': originalSourceUrl,
  'source_name': sourceName,
  'stable_identifier': stableIdentifier,
  'original_source_url': originalSourceUrl,
  'title': title,
  'description': description,
  'place': 'Heemskerk',
  'person': 'Jan Jansen',
  'event': 'Huwelijk',
  'dateStart': '1910',
  'dateEnd': '1910',
  'institution': 'Noord-Hollands Archief',
  'retrievedAt': '2026-08-14T12:00:00Z',
  'technicalStatus': 'AVAILABLE',
  'metadataRights': metadataRights,
  'objectMediaRights': objectMediaRights,
  'privacyStatus': privacyStatus,
};

HistoricalSearchResponse _response(List<Map<String, dynamic>> fixtures) =>
    HistoricalSearchResponse(
      results: fixtures.map(HistoricalSearchResult.fromJson).toList(),
      total: fixtures.length,
      start: 0,
      limit: 100,
      sources: const [
        HistoricalSourceStatus(source: 'OPEN_ARCHIEVEN', status: 'AVAILABLE'),
      ],
    );

Future<void> _showResults(
  WidgetTester tester,
  List<Map<String, dynamic>> fixtures,
) async {
  await tester.pumpWidget(
    MaterialApp(
      home: HistoricalSearchPage(source: _Hkh171Source(_response(fixtures))),
    ),
  );
  await tester.tap(find.byKey(const Key('historical-search-submit')));
  await tester.pumpAndSettle();
}

void _expectText(WidgetTester tester, String text, int count) {
  expect(
    tester.allWidgets.whereType<Text>().where(
      (widget) => widget.data?.contains(text) == true,
    ),
    hasLength(count),
  );
}

void main() {
  testWidgets('renders a complete Open Archieven contract as a public card', (
    tester,
  ) async {
    await _showResults(tester, [_openArchievenFixture()]);
    await tester.ensureVisible(
      find.byKey(
        const Key('historical-result-card-hee:hkh171-1'),
        skipOffstage: false,
      ),
    );
    _expectText(tester, 'Noord-Hollands Archief', 2);
    _expectText(tester, 'Huwelijk van Jan Jansen', 1);
    _expectText(tester, 'Primaire bronbeschrijving', 1);
    _expectText(tester, 'Plaats: Heemskerk', 1);
    _expectText(tester, 'Bronhouder: Noord-Hollands Archief', 1);
    _expectText(tester, 'Persoon: Jan Jansen', 1);
    _expectText(tester, 'Gebeurtenis: Huwelijk', 1);
    _expectText(tester, 'Datering: 1910–1910', 1);
    _expectText(tester, 'Bronidentifier: hee:hkh171-1', 1);
    _expectText(tester, 'Opgehaald: 2026-08-14T12:00:00.000Z', 1);
    _expectText(tester, 'Metadatarechten: Toegestaan', 1);
    _expectText(tester, 'Object-/mediarechten: Beperkt', 1);
    _expectText(tester, 'Privacy: Toegestaan', 1);

    final link = find.byKey(
      const Key('historical-external-link'),
      skipOffstage: false,
    );
    expect(link, findsOneWidget);
    expect(
      find.bySemanticsLabel(historicalExternalLinkLabel, skipOffstage: false),
      findsOneWidget,
    );
    expect(
      tester
          .getSemantics(link)
          .getSemanticsData()
          .hasAction(SemanticsAction.tap),
      isTrue,
    );
  });

  testWidgets(
    'keeps the card but fails closed for optional statuses and content',
    (tester) async {
      await _showResults(tester, [
        _openArchievenFixture(
          title: null,
          description: null,
          metadataRights: '',
          objectMediaRights: 'not-a-contract-status',
          privacyStatus: '',
        ),
      ]);
      await tester.ensureVisible(
        find.byKey(
          const Key('historical-result-card-hee:hkh171-1'),
          skipOffstage: false,
        ),
      );

      expect(
        find.byKey(
          const Key('historical-result-card-hee:hkh171-1'),
          skipOffstage: false,
        ),
        findsOneWidget,
      );
      _expectText(tester, 'Historisch bronresultaat', 0);
      _expectText(tester, 'Huwelijk van Jan Jansen', 0);
      _expectText(tester, 'Primaire bronbeschrijving', 0);
      _expectText(tester, 'Plaats: Heemskerk', 0);
      _expectText(tester, 'Metadatarechten: Onbekend', 1);
      _expectText(tester, 'Object-/mediarechten: Onbekend', 1);
      _expectText(tester, 'Privacy: Onbekend', 1);
    },
  );

  testWidgets('uses description as content when title is absent', (
    tester,
  ) async {
    await _showResults(tester, [
      _openArchievenFixture(title: null, description: 'Alleen beschrijving'),
    ]);
    await tester.ensureVisible(
      find.byKey(
        const Key('historical-result-card-hee:hkh171-1'),
        skipOffstage: false,
      ),
    );

    _expectText(tester, 'Alleen beschrijving', 1);
    _expectText(tester, 'Historisch bronresultaat', 0);
  });

  testWidgets('does not render a card or link for invalid public identity', (
    tester,
  ) async {
    await _showResults(tester, [
      _openArchievenFixture(stableIdentifier: null),
      _openArchievenFixture(
        originalSourceUrl: 'javascript:alert(1)',
        stableIdentifier: 'hee:hkh171-unsafe',
      ),
      _openArchievenFixture(sourceName: null),
      {
        'source': 'EUROPEANA',
        'sourceRecordId': 'europeana-without-url',
        'stableUrl': '',
        'retrievedAt': '2026-08-14T12:00:00Z',
      },
    ]);

    expect(find.byType(Card), findsNothing);
    expect(find.byKey(const Key('historical-external-link')), findsNothing);
    _expectText(tester, 'Noord-Hollands Archief', 0);
  });
}
