import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';

import '../records/external_link_launcher.dart';
import 'historical_context_detail.dart';
import 'historical_rights_explanation.dart';

enum HistoricalSourceChoice { europeana, openArchieven }

enum HistoricalContextStatus { available, missing, uncertain, unavailable }

enum HistoricalFollowUpTopic { place, person, event, period }

const historicalFollowUpWarning =
    'Dit is een nieuwe zoekingang en bewijst geen relatie tussen bronnen.';

class HistoricalFollowUpAction {
  const HistoricalFollowUpAction({
    required this.topic,
    required this.label,
    this.place,
    this.person,
    this.event,
    this.fromYear,
    this.toYear,
  });

  final HistoricalFollowUpTopic topic;
  final String label;
  final String? place;
  final String? person;
  final String? event;
  final String? fromYear;
  final String? toYear;
}

HistoricalContextStatus _contextStatusFromJson(Object? value) =>
    switch (value) {
      'AVAILABLE' => HistoricalContextStatus.available,
      'MISSING' => HistoricalContextStatus.missing,
      'UNCERTAIN' => HistoricalContextStatus.uncertain,
      'UNAVAILABLE' => HistoricalContextStatus.unavailable,
      // A missing or unknown context status must never be promoted to a certain
      // value merely because a payload happens to contain text.
      _ => HistoricalContextStatus.unavailable,
    };

String _sourceName(HistoricalSourceChoice source) => switch (source) {
  HistoricalSourceChoice.europeana => 'EUROPEANA',
  HistoricalSourceChoice.openArchieven => 'OPEN_ARCHIEVEN',
};

String? _optionalHistoricalFilter(String value) =>
    value.trim().isEmpty ? null : value;

class HistoricalRelationshipSource {
  const HistoricalRelationshipSource({required this.name});

  final String name;
}

class HistoricalRelationshipTarget {
  const HistoricalRelationshipTarget({
    required this.name,
    required this.uri,
    required this.link,
  });

  final String name;
  final String uri;
  final String link;
}

class HistoricalSearchRelationship {
  const HistoricalSearchRelationship({
    required this.type,
    required this.source,
    required this.target,
  });

  final String type;
  final HistoricalRelationshipSource source;
  final HistoricalRelationshipTarget target;

  static HistoricalSearchRelationship? tryParse(Object? value) {
    if (value is! Map) return null;
    final type = value['type'];
    final source = value['source'];
    final target = value['target'];
    if (type is! String ||
        type.trim().isEmpty ||
        source is! Map ||
        target is! Map) {
      return null;
    }
    final sourceName = source['name'];
    final targetName = target['name'];
    final targetUri = target['uri'];
    final targetLink = target['link'];
    if (sourceName is! String ||
        sourceName.trim().isEmpty ||
        targetName is! String ||
        targetName.trim().isEmpty ||
        !_isHttpUrl(targetUri) ||
        !_isHttpUrl(targetLink)) {
      return null;
    }
    return HistoricalSearchRelationship(
      type: type.trim(),
      source: HistoricalRelationshipSource(name: sourceName.trim()),
      target: HistoricalRelationshipTarget(
        name: targetName.trim(),
        uri: targetUri as String,
        link: targetLink as String,
      ),
    );
  }
}

bool _isHttpUrl(Object? value) {
  if (value is! String || value.trim().isEmpty) return false;
  final uri = Uri.tryParse(value);
  return uri != null &&
      uri.hasScheme &&
      (uri.scheme == 'http' || uri.scheme == 'https') &&
      uri.host.isNotEmpty;
}

class HistoricalSearchResult {
  const HistoricalSearchResult({
    required this.source,
    required this.sourceRecordId,
    required this.stableUrl,
    required this.retrievedAt,
    this.title,
    this.description,
    this.place,
    this.person,
    this.event,
    this.dateStart,
    this.dateEnd,
    this.institution,
    this.rights,
    this.privacy,
    this.technicalStatus = 'AVAILABLE',
    this.metadataRights = 'UNKNOWN',
    this.objectMediaRights = 'UNKNOWN',
    this.privacyStatus = 'UNKNOWN',
    HistoricalContextStatus? placeStatus,
    HistoricalContextStatus? personStatus,
    HistoricalContextStatus? eventStatus,
    this.relationships = const [],
  }) : placeStatus =
           placeStatus ??
           (place == null
               ? HistoricalContextStatus.missing
               : HistoricalContextStatus.available),
       personStatus =
           personStatus ??
           (person == null
               ? HistoricalContextStatus.missing
               : HistoricalContextStatus.available),
       eventStatus =
           eventStatus ??
           (event == null
               ? HistoricalContextStatus.missing
               : HistoricalContextStatus.available);

  factory HistoricalSearchResult.fromJson(Map<String, dynamic> json) =>
      HistoricalSearchResult(
        source: json['source'] as String,
        sourceRecordId: json['sourceRecordId'] as String,
        stableUrl: json['stableUrl'] as String,
        title: json['title'] as String?,
        description: json['description'] as String?,
        place: json['place'] as String?,
        person: json['person'] as String?,
        event: json['event'] as String?,
        dateStart: json['dateStart'] as String?,
        dateEnd: json['dateEnd'] as String?,
        institution: json['institution'] as String?,
        rights: json['rights'] as String?,
        privacy: json['privacy'] as String?,
        retrievedAt: DateTime.parse(json['retrievedAt'] as String),
        technicalStatus: json['technicalStatus'] as String? ?? 'UNKNOWN',
        metadataRights: json['metadataRights'] as String? ?? 'UNKNOWN',
        objectMediaRights: json['objectMediaRights'] as String? ?? 'UNKNOWN',
        privacyStatus: json['privacyStatus'] as String? ?? 'UNKNOWN',
        placeStatus: _contextStatusFromJson(json['placeStatus']),
        personStatus: _contextStatusFromJson(json['personStatus']),
        eventStatus: _contextStatusFromJson(json['eventStatus']),
        relationships:
            (json['relationships'] is List
                    ? (json['relationships'] as List)
                    : const <Object?>[])
                .map(HistoricalSearchRelationship.tryParse)
                .whereType<HistoricalSearchRelationship>()
                .toList(growable: false),
      );

  final String source;
  final String sourceRecordId;
  final String stableUrl;
  final String? title;
  final String? description;
  final String? place;
  final String? person;
  final String? event;
  final String? dateStart;
  final String? dateEnd;
  final String? institution;
  final String? rights;
  final String? privacy;
  final DateTime retrievedAt;
  final String technicalStatus;
  final String metadataRights;
  final String objectMediaRights;
  final String privacyStatus;
  final HistoricalContextStatus placeStatus;
  final HistoricalContextStatus personStatus;
  final HistoricalContextStatus eventStatus;
  final List<HistoricalSearchRelationship> relationships;
}

List<HistoricalFollowUpAction> historicalFollowUpActions(
  HistoricalSearchResult result,
) {
  if (result.technicalStatus != 'AVAILABLE' ||
      result.metadataRights != 'ALLOWED' ||
      result.privacyStatus != 'CLEAR') {
    return const [];
  }

  final actions = <HistoricalFollowUpAction>[];
  void addContextAction({
    required HistoricalFollowUpTopic topic,
    required HistoricalContextStatus status,
    required String? value,
    required String subject,
  }) {
    if (status != HistoricalContextStatus.available ||
        value?.trim().isNotEmpty != true) {
      return;
    }
    actions.add(
      HistoricalFollowUpAction(
        topic: topic,
        label: 'Nieuwe zoekingang op $subject: $value',
        place: topic == HistoricalFollowUpTopic.place ? value : null,
        person: topic == HistoricalFollowUpTopic.person ? value : null,
        event: topic == HistoricalFollowUpTopic.event ? value : null,
      ),
    );
  }

  addContextAction(
    topic: HistoricalFollowUpTopic.place,
    status: result.placeStatus,
    value: result.place,
    subject: 'plaats',
  );
  addContextAction(
    topic: HistoricalFollowUpTopic.person,
    status: result.personStatus,
    value: result.person,
    subject: 'persoon',
  );
  addContextAction(
    topic: HistoricalFollowUpTopic.event,
    status: result.eventStatus,
    value: result.event,
    subject: 'gebeurtenis',
  );

  final fromYear = result.dateStart?.trim();
  final toYear = result.dateEnd?.trim();
  final validYear = RegExp(r'^\d{4}$');
  if (fromYear != null &&
      toYear != null &&
      validYear.hasMatch(fromYear) &&
      validYear.hasMatch(toYear) &&
      int.parse(fromYear) <= int.parse(toYear)) {
    actions.add(
      HistoricalFollowUpAction(
        topic: HistoricalFollowUpTopic.period,
        label:
            'Nieuwe zoekingang op periode: ${result.dateStart}–${result.dateEnd}',
        fromYear: result.dateStart,
        toYear: result.dateEnd,
      ),
    );
  }
  return actions;
}

class HistoricalSourceStatus {
  const HistoricalSourceStatus({
    required this.source,
    required this.status,
    this.message,
    this.resultCount,
    this.heemskerkCount,
  });

  factory HistoricalSourceStatus.fromJson(Map<String, dynamic> json) =>
      HistoricalSourceStatus(
        source: json['source'] as String,
        status: json['status'] as String,
        message: json['message'] as String?,
        resultCount: json['resultCount'] as int?,
        heemskerkCount: json['heemskerkCount'] as int?,
      );

  final String source;
  final String status;
  final String? message;
  final int? resultCount;
  final int? heemskerkCount;
}

class HistoricalSearchResponse {
  const HistoricalSearchResponse({
    required this.results,
    required this.total,
    required this.start,
    required this.limit,
    required this.sources,
    this.state = 'RESULTS',
  });

  factory HistoricalSearchResponse.fromJson(Map<String, dynamic> json) {
    final results = (json['results'] as List<dynamic>)
        .cast<Map<String, dynamic>>()
        .map(HistoricalSearchResult.fromJson)
        .toList(growable: false);
    final sources = (json['sources'] as List<dynamic>)
        .cast<Map<String, dynamic>>()
        .map(HistoricalSourceStatus.fromJson)
        .toList(growable: false);
    final explicitState = json['state'];
    return HistoricalSearchResponse(
      results: results,
      total: json['total'] as int,
      start: json['start'] as int,
      limit: json['limit'] as int,
      sources: sources,
      state: explicitState is String
          ? explicitState
          : _deriveHistoricalSearchState(
              results,
              json['total'] as int,
              sources,
            ),
    );
  }

  final List<HistoricalSearchResult> results;
  final int total;
  final int start;
  final int limit;
  final List<HistoricalSourceStatus> sources;
  final String state;
}

String _deriveHistoricalSearchState(
  List<HistoricalSearchResult> results,
  int total,
  List<HistoricalSourceStatus> sources,
) {
  final hasFailure = sources.any((source) => source.status != 'AVAILABLE');
  final hasAvailable = sources.any((source) => source.status == 'AVAILABLE');
  if (!hasAvailable) return 'SOURCE_FAILURE';
  if (hasFailure) return 'PARTIAL_AVAILABILITY';
  if (total == 0 && results.isEmpty) return 'NO_RESULTS';
  return 'RESULTS';
}

abstract interface class HistoricalSearchSource {
  Future<HistoricalSearchResponse> loadHistoricalSearch({
    String? text,
    String? place,
    String? person,
    String? event,
    String? fromYear,
    String? toYear,
    HistoricalSourceChoice? source,
    int start,
    int limit,
  });
}

class HistoricalSearchValidationException implements Exception {
  const HistoricalSearchValidationException(this.message);

  final String message;

  @override
  String toString() => message;
}

class HistoricalSearchPage extends StatefulWidget {
  const HistoricalSearchPage({required this.source, this.followUp, super.key});

  final HistoricalSearchSource source;
  final HistoricalFollowUpAction? followUp;

  @override
  State<HistoricalSearchPage> createState() => _HistoricalSearchPageState();
}

class _HistoricalSearchPageState extends State<HistoricalSearchPage> {
  final _text = TextEditingController();
  final _textFocusNode = FocusNode();
  final _place = TextEditingController();
  final _person = TextEditingController();
  final _event = TextEditingController();
  final _fromYear = TextEditingController();
  final _toYear = TextEditingController();
  HistoricalSourceChoice? _source;
  Future<HistoricalSearchResponse>? _search;
  static const _limit = 100;

  @override
  void initState() {
    super.initState();
    final followUp = widget.followUp;
    if (followUp != null) {
      _place.text = followUp.place ?? '';
      _person.text = followUp.person ?? '';
      _event.text = followUp.event ?? '';
      _fromYear.text = followUp.fromYear ?? '';
      _toYear.text = followUp.toYear ?? '';
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _runSearch();
      });
    }
  }

  @override
  void dispose() {
    _text.dispose();
    _textFocusNode.dispose();
    _place.dispose();
    _person.dispose();
    _event.dispose();
    _fromYear.dispose();
    _toYear.dispose();
    super.dispose();
  }

  void _focusSearchForm() {
    _textFocusNode.requestFocus();
  }

  void _runSearch({int? start}) {
    final nextStart = start ?? 0;
    setState(() {
      _search = widget.source.loadHistoricalSearch(
        text: _optionalHistoricalFilter(_text.text),
        place: _optionalHistoricalFilter(_place.text),
        person: _optionalHistoricalFilter(_person.text),
        event: _optionalHistoricalFilter(_event.text),
        fromYear: _optionalHistoricalFilter(_fromYear.text),
        toYear: _optionalHistoricalFilter(_toYear.text),
        source: _source,
        start: nextStart,
        limit: _limit,
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Historisch zoeken')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Text(
              'Zoek in publieke historische bronnen.',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            if (widget.followUp != null) ...[
              const SizedBox(height: 8),
              Text(widget.followUp!.label),
              const SizedBox(height: 8),
              Semantics(
                container: true,
                explicitChildNodes: true,
                excludeSemantics: true,
                label: historicalFollowUpWarning,
                child: const Text(historicalFollowUpWarning),
              ),
            ],
            const SizedBox(height: 16),
            TextField(
              controller: _text,
              focusNode: _textFocusNode,
              decoration: const InputDecoration(
                labelText: 'Vrije tekst',
                hintText: 'Bijvoorbeeld een naam of onderwerp',
                border: OutlineInputBorder(),
              ),
              onEditingComplete: _runSearch,
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _place,
              decoration: const InputDecoration(
                labelText: 'Plek (optioneel)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _person,
              decoration: const InputDecoration(
                labelText: 'Persoon (optioneel)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _event,
              decoration: const InputDecoration(
                labelText: 'Gebeurtenis (optioneel)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _fromYear,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Vanafjaar (optioneel)',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: TextField(
                    controller: _toYear,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(
                      labelText: 'Eindjaar (optioneel)',
                      border: OutlineInputBorder(),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<HistoricalSourceChoice>(
              initialValue: _source,
              decoration: const InputDecoration(
                labelText: 'Bron (optioneel)',
                border: OutlineInputBorder(),
              ),
              items: const [
                DropdownMenuItem(
                  value: HistoricalSourceChoice.europeana,
                  child: Text('Europeana'),
                ),
                DropdownMenuItem(
                  value: HistoricalSourceChoice.openArchieven,
                  child: Text('Open Archieven'),
                ),
              ],
              onChanged: (value) => setState(() => _source = value),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              key: const Key('historical-search-submit'),
              onPressed: _runSearch,
              icon: const Icon(Icons.search),
              label: const Text('Zoeken'),
            ),
            const SizedBox(height: 20),
            if (_search == null)
              const _HistoricalStatus(
                label:
                    'Vul een zoekterm of filter in en start de historische zoekopdracht.',
              )
            else
              FutureBuilder<HistoricalSearchResponse>(
                future: _search,
                builder: (context, snapshot) {
                  if (snapshot.connectionState != ConnectionState.done) {
                    return const _HistoricalStatus(
                      label: 'Historische zoekresultaten worden geladen.',
                      child: Column(
                        children: [
                          ExcludeSemantics(child: CircularProgressIndicator()),
                          SizedBox(height: 12),
                          Text('Historische zoekresultaten worden geladen.'),
                        ],
                      ),
                    );
                  }
                  if (snapshot.hasError) {
                    final error = snapshot.error;
                    if (error is HistoricalSearchValidationException) {
                      return _HistoricalValidationError(message: error.message);
                    }
                    return _HistoricalError(
                      onRetry: _runSearch,
                      onAdjust: _focusSearchForm,
                    );
                  }
                  final response = snapshot.requireData;
                  final state = _effectiveHistoricalSearchState(response);
                  if (state == 'SOURCE_FAILURE') {
                    return _HistoricalError(
                      sources: response.sources,
                      onRetry: _runSearch,
                      onAdjust: _focusSearchForm,
                    );
                  }
                  return _HistoricalResults(
                    response: response,
                    state: state,
                    source: widget.source,
                    onPrevious: response.start == 0
                        ? null
                        : () => _runSearch(
                            start: (response.start - response.limit).clamp(
                              0,
                              response.start,
                            ),
                          ),
                    onNext:
                        response.start + response.results.length >=
                            response.total
                        ? null
                        : () => _runSearch(
                            start: response.start + response.results.length,
                          ),
                  );
                },
              ),
          ],
        ),
      ),
    );
  }
}

String _effectiveHistoricalSearchState(HistoricalSearchResponse response) {
  if (response.state != 'RESULTS') return response.state;
  return _deriveHistoricalSearchState(
    response.results,
    response.total,
    response.sources,
  );
}

class _HistoricalResults extends StatelessWidget {
  const _HistoricalResults({
    required this.response,
    required this.state,
    required this.source,
    required this.onPrevious,
    required this.onNext,
  });

  final HistoricalSearchResponse response;
  final String state;
  final HistoricalSearchSource source;
  final VoidCallback? onPrevious;
  final VoidCallback? onNext;

  @override
  Widget build(BuildContext context) {
    final sourceSummaries = response.sources
        .map(_historicalSourceSummary)
        .toList(growable: false);
    final sourceMessages = response.sources
        .where((source) => source.status != 'AVAILABLE')
        .map(_historicalSourceMessage)
        .toList(growable: false);
    final statusSourceLabels =
        response.sources.any((source) => source.resultCount != null)
        ? sourceSummaries
        : sourceMessages;
    final noResults =
        state == 'NO_RESULTS' ||
        (response.results.isEmpty && response.total == 0);
    final statusLabel = noResults
        ? 'De historische zoekopdracht leverde geen resultaten op.${_sourceMessagesLabel(statusSourceLabels)}'
        : '${response.total} historische resultaten geladen.${_sourceMessagesLabel(statusSourceLabels)}';
    if (noResults) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _HistoricalStatus(
            label: statusLabel,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text('Geen historische resultaten gevonden.'),
                ...sourceSummaries.map(Text.new),
              ],
            ),
          ),
        ],
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _HistoricalStatus(
          label: statusLabel,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('${response.total} historische resultaten'),
              ...sourceSummaries.map(Text.new),
            ],
          ),
        ),
        const SizedBox(height: 12),
        ...response.results.map(
          (result) => _HistoricalResultCard(
            result: result,
            response: response,
            source: source,
          ),
        ),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            OutlinedButton(
              onPressed: onPrevious,
              child: const Text('Vorige resultaten'),
            ),
            OutlinedButton(
              onPressed: onNext,
              child: const Text('Volgende resultaten'),
            ),
          ],
        ),
      ],
    );
  }
}

class _HistoricalResultCard extends StatelessWidget {
  const _HistoricalResultCard({
    required this.result,
    required this.response,
    required this.source,
  });

  final HistoricalSearchResult result;
  final HistoricalSearchResponse response;
  final HistoricalSearchSource source;

  @override
  Widget build(BuildContext context) {
    final metadataAvailable =
        result.metadataRights == 'ALLOWED' && result.privacyStatus == 'CLEAR';
    final heading = metadataAvailable
        ? result.title ?? result.description ?? 'Historisch bronresultaat'
        : 'Historisch bronresultaat';
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(heading, style: Theme.of(context).textTheme.titleMedium),
            if (metadataAvailable && result.description != null)
              Text(result.description!),
            if (metadataAvailable && result.institution != null)
              Text('Bronhouder: ${result.institution}'),
            if (metadataAvailable && result.person != null)
              Text('Persoon: ${result.person}'),
            if (metadataAvailable && result.event != null)
              Text('Gebeurtenis: ${result.event}'),
            if (metadataAvailable)
              Text(
                'Datering: ${result.dateStart ?? 'Onbekend'}${result.dateEnd == null ? '' : '–${result.dateEnd}'}',
              ),
            Text('Bronidentifier: ${result.sourceRecordId}'),
            Text('Opgehaald: ${result.retrievedAt.toLocal()}'),
            Text(
              'Technische beschikbaarheid: ${_status(result.technicalStatus)}',
            ),
            Text('Metadatarechten: ${_status(result.metadataRights)}'),
            Text('Object-/mediarechten: ${_status(result.objectMediaRights)}'),
            HistoricalRightsExplanation(
              keyPrefix:
                  'historical-rights-explanation-${result.sourceRecordId}',
            ),
            Text('Privacy: ${_status(result.privacyStatus)}'),
            if (metadataAvailable && result.rights != null)
              Text('Rechten: ${result.rights}'),
            if (metadataAvailable && result.privacy != null)
              Text('Privacybron: ${result.privacy}'),
            if (result.technicalStatus == 'AVAILABLE')
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(
                  key: Key(
                    'historical-context-action-${result.sourceRecordId}',
                  ),
                  onPressed: () => Navigator.of(context).push<void>(
                    MaterialPageRoute<void>(
                      builder: (_) => HistoricalContextDetailPage(
                        result: result,
                        visibleResults: response.results,
                        searchState: response.state,
                        sourceStatuses: response.sources,
                        source: source,
                      ),
                    ),
                  ),
                  child: const Text('Context bekijken'),
                ),
              ),
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                key: const Key('historical-external-link'),
                onPressed: () => openExternalLink(result.stableUrl),
                icon: const Icon(Icons.open_in_new),
                label: const Text('Externe bron openen in nieuw tabblad'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _status(String value) => switch (value) {
    'ALLOWED' || 'CLEAR' || 'AVAILABLE' => 'Toegestaan',
    'RESTRICTED' || 'BLOCKED' => 'Beperkt',
    'DISABLED' => 'Niet beschikbaar',
    _ => 'Onbekend',
  };
}

class _HistoricalError extends StatelessWidget {
  const _HistoricalError({
    required this.onRetry,
    required this.onAdjust,
    this.sources = const [],
  });

  final List<HistoricalSourceStatus> sources;
  final VoidCallback onRetry;
  final VoidCallback onAdjust;

  @override
  Widget build(BuildContext context) {
    final sourceMessages = sources
        .where((source) => source.status != 'AVAILABLE')
        .map(_historicalSourceMessage)
        .toList(growable: false);
    final label =
        'Geen historische bronnen konden worden geraadpleegd.${_sourceMessagesLabel(sourceMessages)}';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _HistoricalStatus(
          label: label,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text(
                'Geen historische bronnen konden worden geraadpleegd.',
              ),
              ...sourceMessages.map(Text.new),
            ],
          ),
        ),
        const SizedBox(height: 12),
        OutlinedButton.icon(
          key: const Key('historical-search-retry'),
          onPressed: onRetry,
          icon: const Icon(Icons.refresh),
          label: const Text('Opnieuw proberen'),
        ),
        const SizedBox(height: 8),
        OutlinedButton(
          key: const Key('historical-search-adjust'),
          onPressed: onAdjust,
          child: const Text('Zoekopdracht aanpassen'),
        ),
      ],
    );
  }
}

class _HistoricalValidationError extends StatelessWidget {
  const _HistoricalValidationError({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) => _HistoricalStatus(
    label: 'De historische zoekfilters zijn ongeldig.',
    child: Text(message),
  );
}

class _HistoricalStatus extends StatelessWidget {
  const _HistoricalStatus({required this.label, this.child});

  final String label;
  final Widget? child;

  @override
  Widget build(BuildContext context) => Semantics(
    container: true,
    role: SemanticsRole.status,
    label: label,
    excludeSemantics: true,
    child: child ?? Text(label),
  );
}

String _historicalSourceMessage(HistoricalSourceStatus source) {
  final name = switch (source.source) {
    'EUROPEANA' => 'Europeana',
    'OPEN_ARCHIEVEN' => 'Open Archieven',
    _ => source.source,
  };
  final status = switch (source.status) {
    'DISABLED' => 'niet geconfigureerd',
    'TEMPORARILY_UNAVAILABLE' => 'tijdelijk niet beschikbaar',
    'INVALID_RESPONSE' => 'ongeldige bronrespons',
    _ => 'niet beschikbaar',
  };
  return '$name: $status.';
}

String _historicalSourceSummary(HistoricalSourceStatus source) {
  if (source.status != 'AVAILABLE') {
    return _historicalSourceMessage(source);
  }
  if (source.resultCount == null) {
    return '${_historicalSourceName(source)}: beschikbaar.';
  }
  final indication = source.heemskerkCount;
  if (indication == null) {
    return '${_historicalSourceName(source)}: beschikbaar.';
  }
  return '${_historicalSourceName(source)}: beschikbaar, '
      '${source.resultCount} resultaten. '
      'Lokale Heemskerk-indicatie op basis van plaatsmetadata: $indication '
      '(geen historisch bewijs).';
}

String _historicalSourceName(HistoricalSourceStatus source) =>
    switch (source.source) {
      'EUROPEANA' => 'Europeana',
      'OPEN_ARCHIEVEN' => 'Open Archieven',
      _ => source.source,
    };

String _sourceMessagesLabel(List<String> messages) =>
    messages.isEmpty ? '' : ' ${messages.join(' ')}';

String historicalSourceQueryValue(HistoricalSourceChoice source) =>
    _sourceName(source);
