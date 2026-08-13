import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/historical/historical_context_detail.dart';
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
}
