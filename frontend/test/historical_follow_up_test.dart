import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/historical/historical_context_detail.dart';
import 'package:hkh_app/historical/historical_search.dart';

HistoricalSearchResult _result({
  String? place = 'Heemskerk',
  String? person = 'Jan de Vries',
  String? event = 'Huwelijk',
  String? dateStart = '1900',
  String? dateEnd = '1910',
  String technicalStatus = 'AVAILABLE',
  String metadataRights = 'ALLOWED',
  String privacyStatus = 'CLEAR',
  HistoricalContextStatus? placeStatus,
  HistoricalContextStatus? personStatus,
  HistoricalContextStatus? eventStatus,
}) => HistoricalSearchResult(
  source: 'OPEN_ARCHIEVEN',
  sourceRecordId: 'follow-up-1',
  stableUrl: 'https://example.test/follow-up-1',
  retrievedAt: DateTime.utc(2026, 8, 12),
  title: 'Titel zonder contextafleiding',
  place: place,
  person: person,
  event: event,
  dateStart: dateStart,
  dateEnd: dateEnd,
  technicalStatus: technicalStatus,
  metadataRights: metadataRights,
  privacyStatus: privacyStatus,
  placeStatus: placeStatus,
  personStatus: personStatus,
  eventStatus: eventStatus,
);

class _CapturingHistoricalSource implements HistoricalSearchSource {
  _CapturingHistoricalSource(this.response);

  final HistoricalSearchResponse response;
  final calls =
      <
        ({
          String? text,
          String? place,
          String? person,
          String? event,
          String? fromYear,
          String? toYear,
          HistoricalSourceChoice? source,
        })
      >[];

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
    calls.add((
      text: text,
      place: place,
      person: person,
      event: event,
      fromYear: fromYear,
      toYear: toYear,
      source: source,
    ));
    return Future.value(response);
  }
}

void main() {
  test('creates exact actions only for certain, permitted metadata', () {
    final actions = historicalFollowUpActions(_result());

    expect(actions.map((action) => action.topic), [
      HistoricalFollowUpTopic.place,
      HistoricalFollowUpTopic.person,
      HistoricalFollowUpTopic.event,
      HistoricalFollowUpTopic.period,
    ]);
    expect(actions[0].place, 'Heemskerk');
    expect(actions[1].person, 'Jan de Vries');
    expect(actions[2].event, 'Huwelijk');
    expect(actions[3].fromYear, '1900');
    expect(actions[3].toYear, '1910');
  });

  test(
    'does not offer actions for uncertain, derived, restricted or invalid values',
    () {
      expect(
        historicalFollowUpActions(
          _result(
            placeStatus: HistoricalContextStatus.uncertain,
            personStatus: HistoricalContextStatus.unavailable,
            event: null,
            dateStart: '1900-01-01',
            dateEnd: '1910-01-01',
          ),
        ),
        isEmpty,
      );
      expect(
        historicalFollowUpActions(_result(metadataRights: 'RESTRICTED')),
        isEmpty,
      );
      expect(
        historicalFollowUpActions(_result(privacyStatus: 'BLOCKED')),
        isEmpty,
      );
      expect(
        historicalFollowUpActions(_result(technicalStatus: 'INVALID_RESPONSE')),
        isEmpty,
      );
    },
  );

  test('does not promote missing or unknown API context statuses', () {
    final missing = HistoricalSearchResult.fromJson({
      'source': 'OPEN_ARCHIEVEN',
      'sourceRecordId': 'missing-status',
      'stableUrl': 'https://example.test/missing-status',
      'retrievedAt': '2026-08-12T00:00:00Z',
      'place': 'Heemskerk',
      'person': 'Jan de Vries',
      'event': 'Huwelijk',
      'placeStatus': 'MISSING',
      'personStatus': 'UNKNOWN',
      'eventStatus': null,
      'metadataRights': 'ALLOWED',
      'privacyStatus': 'CLEAR',
    });

    expect(missing.placeStatus, HistoricalContextStatus.missing);
    expect(missing.personStatus, HistoricalContextStatus.unavailable);
    expect(missing.eventStatus, HistoricalContextStatus.unavailable);
    expect(historicalFollowUpActions(missing), isEmpty);
  });

  testWidgets(
    'starts an unfiltered exact follow-up and preserves both back steps',
    (tester) async {
      final result = _result();
      final response = HistoricalSearchResponse(
        results: [result],
        total: 1,
        start: 0,
        limit: 100,
        sources: const [
          HistoricalSourceStatus(source: 'OPEN_ARCHIEVEN', status: 'AVAILABLE'),
        ],
      );
      final source = _CapturingHistoricalSource(response);
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                key: const Key('historical-results-list'),
                onPressed: () => Navigator.of(context).push<void>(
                  MaterialPageRoute<void>(
                    builder: (_) => HistoricalContextDetailPage(
                      result: result,
                      visibleResults: [result],
                      searchState: 'RESULTS',
                      sourceStatuses: const [
                        HistoricalSourceStatus(
                          source: 'OPEN_ARCHIEVEN',
                          status: 'AVAILABLE',
                        ),
                      ],
                      source: source,
                    ),
                  ),
                ),
                child: const Text('Resultatenlijst'),
              ),
            ),
          ),
        ),
      );

      await tester.tap(find.byKey(const Key('historical-results-list')));
      await tester.pumpAndSettle();
      for (var index = 0; index < 5; index++) {
        await tester.drag(find.byType(ListView), const Offset(0, -500));
        await tester.pump();
      }
      await tester.ensureVisible(
        find.byKey(const Key('historical-follow-up-place-follow-up-1')),
      );
      expect(
        tester
            .getSemantics(
              find.byKey(const Key('historical-follow-up-place-follow-up-1')),
            )
            .getSemanticsData()
            .hasAction(SemanticsAction.tap),
        isTrue,
      );
      await tester.tap(
        find.byKey(const Key('historical-follow-up-place-follow-up-1')),
      );
      await tester.pumpAndSettle();

      expect(
        find.text('Nieuwe zoekingang op plaats: Heemskerk'),
        findsOneWidget,
      );
      expect(find.text(historicalFollowUpWarning), findsOneWidget);
      expect(source.calls, hasLength(1));
      expect(source.calls.single.place, 'Heemskerk');
      expect(source.calls.single.person, isNull);
      expect(source.calls.single.event, isNull);
      expect(source.calls.single.fromYear, isNull);
      expect(source.calls.single.toYear, isNull);
      expect(source.calls.single.source, isNull);

      await tester.pageBack();
      await tester.pumpAndSettle();

      await tester.ensureVisible(
        find.byKey(const Key('historical-follow-up-person-follow-up-1')),
      );
      await tester.tap(
        find.byKey(const Key('historical-follow-up-person-follow-up-1')),
      );
      await tester.pumpAndSettle();
      expect(source.calls, hasLength(2));
      expect(source.calls[1].place, isNull);
      expect(source.calls[1].person, 'Jan de Vries');
      expect(source.calls[1].event, isNull);
      expect(source.calls[1].fromYear, isNull);
      expect(source.calls[1].toYear, isNull);
      expect(source.calls[1].source, isNull);

      await tester.pageBack();
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const Key('historical-follow-up-event-follow-up-1')),
      );
      await tester.tap(
        find.byKey(const Key('historical-follow-up-event-follow-up-1')),
      );
      await tester.pumpAndSettle();
      expect(source.calls, hasLength(3));
      expect(source.calls[2].place, isNull);
      expect(source.calls[2].person, isNull);
      expect(source.calls[2].event, 'Huwelijk');
      expect(source.calls[2].fromYear, isNull);
      expect(source.calls[2].toYear, isNull);
      expect(source.calls[2].source, isNull);

      await tester.pageBack();
      await tester.pumpAndSettle();
      await tester.ensureVisible(
        find.byKey(const Key('historical-follow-up-period-follow-up-1')),
      );
      await tester.tap(
        find.byKey(const Key('historical-follow-up-period-follow-up-1')),
      );
      await tester.pumpAndSettle();
      expect(source.calls, hasLength(4));
      expect(source.calls[3].place, isNull);
      expect(source.calls[3].person, isNull);
      expect(source.calls[3].event, isNull);
      expect(source.calls[3].fromYear, '1900');
      expect(source.calls[3].toYear, '1910');
      expect(source.calls[3].source, isNull);

      await tester.pageBack();
      await tester.pumpAndSettle();
      for (var index = 0; index < 5; index++) {
        await tester.drag(find.byType(ListView), const Offset(0, 500));
        await tester.pump();
      }
      await tester.ensureVisible(
        find.text('Context van historisch zoekresultaat'),
      );
      expect(find.text('Context van historisch zoekresultaat'), findsOneWidget);
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(find.text('Resultatenlijst'), findsOneWidget);
    },
  );
}
