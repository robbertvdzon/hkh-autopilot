import 'package:flutter/material.dart';
import 'package:unorm_dart/unorm_dart.dart' as unorm;

import '../records/external_link_launcher.dart';
import 'historical_search.dart';

class HistoricalContextRelation {
  const HistoricalContextRelation({
    required this.result,
    required this.sharedFields,
    required this.periodOverlaps,
  });

  final HistoricalSearchResult result;
  final List<String> sharedFields;
  final bool periodOverlaps;
}

List<HistoricalContextRelation> findHistoricalContextRelations(
  HistoricalSearchResult opened,
  List<HistoricalSearchResult> visibleResults,
) {
  final relations = <HistoricalContextRelation>[];
  for (final candidate in visibleResults) {
    if (_sameHistoricalResult(opened, candidate)) continue;
    final sharedFields = <String>[];
    if (_sameCertainContext(
      opened.place,
      opened.placeStatus,
      candidate.place,
      candidate.placeStatus,
    )) {
      sharedFields.add('Plaats');
    }
    if (_sameCertainContext(
      opened.person,
      opened.personStatus,
      candidate.person,
      candidate.personStatus,
    )) {
      sharedFields.add('Persoon');
    }
    if (_sameCertainContext(
      opened.event,
      opened.eventStatus,
      candidate.event,
      candidate.eventStatus,
    )) {
      sharedFields.add('Gebeurtenis');
    }
    if (sharedFields.isNotEmpty) {
      relations.add(
        HistoricalContextRelation(
          result: candidate,
          sharedFields: sharedFields,
          periodOverlaps: _historicalPeriodsOverlap(opened, candidate),
        ),
      );
    }
    if (relations.length == 3) break;
  }
  return relations;
}

bool _sameCertainContext(
  String? left,
  HistoricalContextStatus leftStatus,
  String? right,
  HistoricalContextStatus rightStatus,
) {
  if (leftStatus != HistoricalContextStatus.available ||
      rightStatus != HistoricalContextStatus.available ||
      left == null ||
      right == null) {
    return false;
  }
  return _normalizeContext(left) == _normalizeContext(right);
}

String _normalizeContext(String value) =>
    unorm.nfkc(value.trim()).replaceAll(RegExp(r'\s+'), ' ').toLowerCase();

bool _sameHistoricalResult(
  HistoricalSearchResult left,
  HistoricalSearchResult right,
) =>
    (left.source == right.source &&
        left.sourceRecordId == right.sourceRecordId) ||
    left.stableUrl == right.stableUrl;

bool _historicalPeriodsOverlap(
  HistoricalSearchResult left,
  HistoricalSearchResult right,
) {
  final leftRange = _historicalDateRange(left);
  final rightRange = _historicalDateRange(right);
  if (leftRange == null || rightRange == null) return false;
  return !leftRange.start.isAfter(rightRange.end) &&
      !rightRange.start.isAfter(leftRange.end);
}

({DateTime start, DateTime end})? _historicalDateRange(
  HistoricalSearchResult result,
) {
  final start = _historicalDate(result.dateStart);
  final end = _historicalDate(result.dateEnd) ?? start;
  if (start == null || end == null || start.isAfter(end)) return null;
  return (start: start, end: end);
}

DateTime? _historicalDate(String? value) {
  if (value == null) return null;
  if (RegExp(r'^\d{4}$').hasMatch(value)) {
    return DateTime.utc(int.parse(value), 1, 1);
  }
  return DateTime.tryParse(value);
}

class HistoricalContextDetailPage extends StatelessWidget {
  const HistoricalContextDetailPage({
    required this.result,
    required this.visibleResults,
    required this.searchState,
    required this.sourceStatuses,
    this.source,
    super.key,
  });

  final HistoricalSearchResult result;
  final List<HistoricalSearchResult> visibleResults;
  final String searchState;
  final List<HistoricalSourceStatus> sourceStatuses;
  final HistoricalSearchSource? source;

  @override
  Widget build(BuildContext context) {
    final relations = findHistoricalContextRelations(result, visibleResults);
    final sourceStatus = sourceStatuses.where(
      (source) => source.source == result.source,
    );
    final sourceStatusLabel = sourceStatus.isEmpty
        ? _historicalStatus(result.technicalStatus)
        : _historicalSourceStatus(sourceStatus.first.status);
    return Scaffold(
      appBar: AppBar(title: const Text('Historische context')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Semantics(
              header: true,
              child: Text(
                'Context van historisch zoekresultaat',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
            ),
            const SizedBox(height: 16),
            Text('Zoekstatus: ${_historicalSearchStateLabel(searchState)}'),
            Text('Bronstatus: $sourceStatusLabel'),
            const Divider(height: 32),
            _detailField('Titel', result.title),
            _detailField(
              'Plaats',
              _contextValue(result.place, result.placeStatus),
            ),
            _detailField('Periode', _periodLabel(result)),
            _detailField(
              'Persoon',
              _contextValue(result.person, result.personStatus),
            ),
            _detailField(
              'Gebeurtenis',
              _contextValue(result.event, result.eventStatus),
            ),
            _detailField('Bron', _historicalSourceLabel(result.source)),
            _detailField('Bronhouder', result.institution),
            _detailField('Stabiele bronidentifier', result.sourceRecordId),
            _detailField('Stabiele bron-URI', result.stableUrl),
            _detailField(
              'Server-side opgehaald',
              result.retrievedAt.toLocal().toString(),
            ),
            _detailField(
              'Technische bronstatus',
              _historicalStatus(result.technicalStatus),
            ),
            _detailField(
              'Metadatarechten',
              _historicalStatus(result.metadataRights),
            ),
            _detailField(
              'Object-/mediarechten',
              _historicalStatus(result.objectMediaRights),
            ),
            _detailField(
              'Privacystatus',
              _historicalStatus(result.privacyStatus),
            ),
            const SizedBox(height: 8),
            TextButton.icon(
              key: const Key('historical-context-source-link'),
              onPressed: () => openExternalLink(result.stableUrl),
              icon: const Icon(Icons.open_in_new),
              label: const Text('Externe bron openen in nieuw tabblad'),
            ),
            ..._sourceRelationshipSection(),
            ..._followUpSection(context),
            const Divider(height: 32),
            Semantics(
              header: true,
              child: Text(
                'Verwante resultaten',
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ),
            if (relations.isEmpty)
              const Text('Geen deterministische relaties gevonden.'),
            ...relations.asMap().entries.map(
              (entry) => _HistoricalRelationCard(
                index: entry.key,
                relation: entry.value,
              ),
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _sourceRelationshipSection() {
    if (result.relationships.isEmpty) return const [];
    return [
      const Divider(height: 32),
      Semantics(header: true, child: const Text('Bronvastgelegde relatie')),
      const SizedBox(height: 8),
      ...result.relationships.asMap().entries.map(
        (entry) => _HistoricalSourceRelationshipCard(
          index: entry.key,
          relationship: entry.value,
        ),
      ),
    ];
  }

  Widget _detailField(String label, String? value) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Text(
      '$label: ${value == null || value.isEmpty ? 'Niet beschikbaar' : value}',
    ),
  );

  String? _periodLabel(HistoricalSearchResult value) {
    if (value.dateStart == null && value.dateEnd == null) return null;
    return '${value.dateStart ?? 'Niet beschikbaar'}${value.dateEnd == null ? '' : '–${value.dateEnd}'}';
  }

  List<Widget> _followUpSection(BuildContext context) {
    final source = this.source;
    if (source == null) return const [];
    final actions = historicalFollowUpActions(result);
    if (actions.isEmpty) return const [];
    return [
      const Divider(height: 32),
      Semantics(header: true, child: const Text('Nieuwe zoekingangen')),
      const SizedBox(height: 8),
      Semantics(
        container: true,
        explicitChildNodes: true,
        excludeSemantics: true,
        label: historicalFollowUpWarning,
        child: const Text(historicalFollowUpWarning),
      ),
      const SizedBox(height: 8),
      ...actions.map(
        (action) => Align(
          alignment: Alignment.centerLeft,
          child: TextButton(
            key: Key(
              'historical-follow-up-${action.topic.name}-${result.sourceRecordId}',
            ),
            onPressed: () => Navigator.of(context).push<void>(
              MaterialPageRoute<void>(
                builder: (_) =>
                    HistoricalSearchPage(source: source, followUp: action),
              ),
            ),
            child: Text(action.label),
          ),
        ),
      ),
    ];
  }
}

class _HistoricalRelationCard extends StatelessWidget {
  const _HistoricalRelationCard({required this.index, required this.relation});

  final int index;
  final HistoricalContextRelation relation;

  @override
  Widget build(BuildContext context) {
    final result = relation.result;
    return Card(
      key: Key('historical-relation-$index'),
      margin: const EdgeInsets.only(top: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Gedeeld: ${relation.sharedFields.join(', ')}'),
            if (relation.periodOverlaps)
              const Text('Aanvullend: overlappende periode'),
            Text('Bron: ${_historicalSourceLabel(result.source)}'),
            Text('Bronidentifier: ${result.sourceRecordId}'),
            Text('Stabiele bronlink: ${result.stableUrl}'),
            TextButton.icon(
              key: Key('historical-relation-link-$index'),
              onPressed: () => openExternalLink(result.stableUrl),
              icon: const Icon(Icons.open_in_new),
              label: const Text('Verwante bron openen in nieuw tabblad'),
            ),
          ],
        ),
      ),
    );
  }
}

class _HistoricalSourceRelationshipCard extends StatelessWidget {
  const _HistoricalSourceRelationshipCard({
    required this.index,
    required this.relationship,
  });

  final int index;
  final HistoricalSearchRelationship relationship;

  @override
  Widget build(BuildContext context) {
    final target = relationship.target;
    return Card(
      key: Key('historical-source-relationship-$index'),
      margin: const EdgeInsets.only(top: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'Bronclaim: deze relatie is expliciet door de externe bron geleverd en niet door HKH afgeleid.',
            ),
            Text('Relatietype: ${relationship.type}'),
            Text('Bron: ${relationship.source.name}'),
            Text('Doelrecord: ${target.name}'),
            Text('Stabiele doel-URI: ${target.uri}'),
            TextButton.icon(
              key: Key('historical-source-relationship-link-$index'),
              onPressed: () => openExternalLink(target.link),
              icon: const Icon(Icons.open_in_new),
              label: const Text(
                'Externe link naar doelrecord openen in nieuw tabblad',
              ),
            ),
          ],
        ),
      ),
    );
  }
}

String _contextValue(String? value, HistoricalContextStatus status) =>
    switch (status) {
      HistoricalContextStatus.available =>
        value?.trim().isNotEmpty == true ? value!.trim() : 'Niet beschikbaar',
      HistoricalContextStatus.uncertain => 'Onzeker',
      HistoricalContextStatus.missing ||
      HistoricalContextStatus.unavailable => 'Niet beschikbaar',
    };

String _historicalSourceLabel(String source) => switch (source) {
  'EUROPEANA' => 'Europeana',
  'OPEN_ARCHIEVEN' => 'Open Archieven',
  _ => source,
};

String _historicalSourceStatus(String status) => switch (status) {
  'AVAILABLE' => 'Beschikbaar',
  'DISABLED' => 'Niet beschikbaar',
  'TEMPORARILY_UNAVAILABLE' => 'Tijdelijk niet beschikbaar',
  'INVALID_RESPONSE' => 'Ongeldige bronrespons',
  _ => 'Onbekend',
};

String _historicalSearchStateLabel(String state) => switch (state) {
  'RESULTS' => 'Resultaten beschikbaar',
  'NO_RESULTS' => 'Geen resultaten',
  'PARTIAL_AVAILABILITY' => 'Gedeeltelijke bronbeschikbaarheid',
  'SOURCE_FAILURE' => 'Bronnen niet beschikbaar',
  _ => 'Onbekend',
};

String _historicalStatus(String value) => switch (value) {
  'ALLOWED' || 'CLEAR' || 'AVAILABLE' => 'Toegestaan',
  'RESTRICTED' || 'BLOCKED' => 'Beperkt',
  'DISABLED' => 'Niet beschikbaar',
  _ => 'Onbekend',
};
