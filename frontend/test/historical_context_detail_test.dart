import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/historical/historical_context_detail.dart';
import 'package:hkh_app/historical/historical_rights_explanation.dart';
import 'package:hkh_app/historical/historical_search.dart';

HistoricalSearchResult _result(
  String id, {
  String? place,
  String? person,
  String? event,
  String? dateStart,
  String? dateEnd,
  HistoricalContextStatus? placeStatus,
  HistoricalContextStatus? personStatus,
  HistoricalContextStatus? eventStatus,
  List<HistoricalSearchRelationship> relationships = const [],
  String metadataRights = 'UNKNOWN',
  String objectMediaRights = 'UNKNOWN',
}) => HistoricalSearchResult(
  source: 'OPEN_ARCHIEVEN',
  sourceRecordId: id,
  stableUrl: 'https://example.test/$id',
  retrievedAt: DateTime.utc(2026, 8, 12),
  place: place,
  person: person,
  event: event,
  dateStart: dateStart,
  dateEnd: dateEnd,
  placeStatus: placeStatus,
  personStatus: personStatus,
  eventStatus: eventStatus,
  metadataRights: metadataRights,
  objectMediaRights: objectMediaRights,
  relationships: relationships,
);

void main() {
  test('matches exact normalized context and keeps visible order', () {
    final opened = _result(
      'opened',
      place: '  Heemskerk ',
      person: 'Jan',
      dateStart: '1900',
      dateEnd: '1910',
    );
    final placeMatch = _result('place', place: 'HEEMSKERK');
    final periodOnly = _result(
      'period-only',
      dateStart: '1905',
      dateEnd: '1906',
    );
    final uncertainMatch = _result(
      'uncertain',
      person: 'JAN',
      personStatus: HistoricalContextStatus.uncertain,
    );
    final personMatch = _result('person', person: ' Jan ');
    final fourthMatch = _result('fourth', place: 'Heemskerk');

    final relations = findHistoricalContextRelations(opened, [
      opened,
      placeMatch,
      periodOnly,
      uncertainMatch,
      personMatch,
      fourthMatch,
      _result('fifth', place: 'Heemskerk'),
    ]);

    expect(relations.map((relation) => relation.result.sourceRecordId), [
      'place',
      'person',
      'fourth',
    ]);
    expect(relations.first.sharedFields, ['Plaats']);
    expect(relations[1].sharedFields, ['Persoon']);
    expect(relations.first.periodOverlaps, isFalse);
  });

  test('matches canonically equivalent Unicode context values', () {
    final opened = _result('opened', place: 'Café');
    final decomposed = _result('decomposed', place: 'Cafe\u0301');

    final relations = findHistoricalContextRelations(opened, [
      opened,
      decomposed,
    ]);

    expect(relations, hasLength(1));
    expect(relations.single.sharedFields, ['Plaats']);
  });

  test('a period overlap is only an annotation on an existing relation', () {
    final opened = _result(
      'opened',
      place: 'Heemskerk',
      dateStart: '1900',
      dateEnd: '1910',
    );
    final related = _result(
      'related',
      place: 'heemskerk',
      dateStart: '1905',
      dateEnd: '1915',
    );
    final unrelated = _result('unrelated', dateStart: '1905', dateEnd: '1915');

    final relations = findHistoricalContextRelations(opened, [
      opened,
      related,
      unrelated,
    ]);

    expect(relations, hasLength(1));
    expect(relations.single.periodOverlaps, isTrue);
  });

  test('parses only complete stable source relationships', () {
    final result = HistoricalSearchResult.fromJson({
      'source': 'OPEN_ARCHIEVEN',
      'sourceRecordId': 'source-1',
      'stableUrl': 'https://example.test/source-1',
      'retrievedAt': '2026-08-12T00:00:00Z',
      'relationships': [
        {
          'type': 'isPartOf',
          'source': {'name': 'Open Archieven'},
          'target': {
            'name': 'Register 1900',
            'uri': 'https://data.example/record/1',
            'link': 'https://source.example/record/1',
          },
        },
        {
          'type': 'missing-uri',
          'source': {'name': 'Open Archieven'},
          'target': {
            'name': 'Ongeldig doel',
            'link': 'https://source.example/record/2',
          },
        },
        {
          'type': 'derived-only',
          'source': {'name': 'Open Archieven'},
          'target': {
            'name': 'Geen bronlink',
            'uri': 'https://data.example/record/3',
            'link': 'javascript:alert(1)',
          },
        },
      ],
    });

    expect(result.relationships, hasLength(1));
    expect(result.relationships.single.type, 'isPartOf');
    expect(result.relationships.single.source.name, 'Open Archieven');
    expect(result.relationships.single.target.name, 'Register 1900');
  });

  testWidgets('shows source relationships separately from metadata overlap', (
    tester,
  ) async {
    final opened = _result(
      'opened',
      place: 'Heemskerk',
      relationships: const [
        HistoricalSearchRelationship(
          type: 'isPartOf',
          source: HistoricalRelationshipSource(name: 'Open Archieven'),
          target: HistoricalRelationshipTarget(
            name: 'Register 1900',
            uri: 'https://data.example/record/1',
            link: 'https://source.example/record/1',
          ),
        ),
      ],
    );
    final related = _result('related', place: 'Heemskerk');
    expect(opened.relationships, hasLength(1));
    await tester.pumpWidget(
      MaterialApp(
        home: HistoricalContextDetailPage(
          result: opened,
          visibleResults: [opened, related],
          searchState: 'RESULTS',
          sourceStatuses: const [],
        ),
      ),
    );

    await tester.scrollUntilVisible(
      find.byKey(const Key('historical-source-relationship-0')),
      300,
    );
    expect(find.text('Bronvastgelegde relatie'), findsOneWidget);
    expect(
      find.text(
        'Bronclaim: deze relatie is expliciet door de externe bron geleverd en niet door HKH afgeleid.',
      ),
      findsOneWidget,
    );
    expect(find.text('Relatietype: isPartOf'), findsOneWidget);
    expect(find.text('Bron: Open Archieven'), findsNWidgets(2));
    expect(find.text('Doelrecord: Register 1900'), findsOneWidget);
    expect(
      find.text('Stabiele doel-URI: https://data.example/record/1'),
      findsOneWidget,
    );
    expect(
      find.text('Externe link naar doelrecord openen in nieuw tabblad'),
      findsOneWidget,
    );
    await tester.scrollUntilVisible(
      find.byKey(const Key('historical-relation-0')),
      300,
    );
    expect(find.text('Verwante resultaten'), findsOneWidget);
    expect(
      find.byKey(const Key('historical-source-relationship-0')),
      findsOneWidget,
    );
  });

  testWidgets('hides the source relationship section when none are valid', (
    tester,
  ) async {
    final result = HistoricalSearchResult.fromJson({
      'source': 'OPEN_ARCHIEVEN',
      'sourceRecordId': 'source-1',
      'stableUrl': 'https://example.test/source-1',
      'retrievedAt': '2026-08-12T00:00:00Z',
      'relationships': [
        {
          'type': 'missing-target',
          'source': {'name': 'Open Archieven'},
          'target': {
            'name': 'Target',
            'uri': 'not-a-uri',
            'link': 'not-a-link',
          },
        },
      ],
    });
    await tester.pumpWidget(
      MaterialApp(
        home: HistoricalContextDetailPage(
          result: result,
          visibleResults: [result],
          searchState: 'RESULTS',
          sourceStatuses: const [],
        ),
      ),
    );

    expect(find.text('Bronvastgelegde relatie'), findsNothing);
    expect(
      find.byKey(const Key('historical-source-relationship-0')),
      findsNothing,
    );
  });

  testWidgets(
    'shows separate rights statuses and their explanation in detail',
    (tester) async {
      final result = _result(
        'rights-detail',
        metadataRights: 'ALLOWED',
        objectMediaRights: 'RESTRICTED',
      );
      await tester.pumpWidget(
        MaterialApp(
          home: HistoricalContextDetailPage(
            result: result,
            visibleResults: [result],
            searchState: 'RESULTS',
            sourceStatuses: const [],
          ),
        ),
      );

      expect(find.text('Metadatarechten: Toegestaan'), findsOneWidget);
      expect(find.text('Object-/mediarechten: Beperkt'), findsOneWidget);
      await tester.drag(find.byType(ListView), const Offset(0, -500));
      await tester.pump();
      final toggle = find.byKey(
        const Key('historical-rights-explanation-detail-rights-detail-toggle'),
      );
      expect(toggle, findsOneWidget);
      expect(
        tester
            .getSemantics(toggle)
            .getSemanticsData()
            .hasAction(SemanticsAction.tap),
        isTrue,
      );
      await tester.ensureVisible(toggle);
      await tester.tap(toggle);
      await tester.pump();
      expect(find.text(historicalRightsExplanation), findsOneWidget);
    },
  );
}
