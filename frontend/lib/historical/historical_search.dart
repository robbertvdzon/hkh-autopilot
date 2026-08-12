import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';

import '../records/external_link_launcher.dart';

enum HistoricalSourceChoice { europeana, openArchieven }

String _sourceName(HistoricalSourceChoice source) => switch (source) {
  HistoricalSourceChoice.europeana => 'EUROPEANA',
  HistoricalSourceChoice.openArchieven => 'OPEN_ARCHIEVEN',
};

class HistoricalSearchResult {
  const HistoricalSearchResult({
    required this.source,
    required this.sourceRecordId,
    required this.stableUrl,
    required this.retrievedAt,
    this.title,
    this.description,
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
  });

  factory HistoricalSearchResult.fromJson(Map<String, dynamic> json) =>
      HistoricalSearchResult(
        source: json['source'] as String,
        sourceRecordId: json['sourceRecordId'] as String,
        stableUrl: json['stableUrl'] as String,
        title: json['title'] as String?,
        description: json['description'] as String?,
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
      );

  final String source;
  final String sourceRecordId;
  final String stableUrl;
  final String? title;
  final String? description;
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
}

class HistoricalSourceStatus {
  const HistoricalSourceStatus({
    required this.source,
    required this.status,
    this.message,
  });

  factory HistoricalSourceStatus.fromJson(Map<String, dynamic> json) =>
      HistoricalSourceStatus(
        source: json['source'] as String,
        status: json['status'] as String,
        message: json['message'] as String?,
      );

  final String source;
  final String status;
  final String? message;
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
  const HistoricalSearchPage({required this.source, super.key});

  final HistoricalSearchSource source;

  @override
  State<HistoricalSearchPage> createState() => _HistoricalSearchPageState();
}

class _HistoricalSearchPageState extends State<HistoricalSearchPage> {
  final _text = TextEditingController();
  final _place = TextEditingController();
  final _person = TextEditingController();
  final _event = TextEditingController();
  final _fromYear = TextEditingController();
  final _toYear = TextEditingController();
  HistoricalSourceChoice? _source;
  Future<HistoricalSearchResponse>? _search;
  int _start = 0;
  static const _limit = 100;

  @override
  void dispose() {
    _text.dispose();
    _place.dispose();
    _person.dispose();
    _event.dispose();
    _fromYear.dispose();
    _toYear.dispose();
    super.dispose();
  }

  void _runSearch({int? start}) {
    final nextStart = start ?? 0;
    setState(() {
      _start = nextStart;
      _search = widget.source.loadHistoricalSearch(
        text: _text.text,
        place: _place.text,
        person: _person.text,
        event: _event.text,
        fromYear: _fromYear.text,
        toYear: _toYear.text,
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
            const SizedBox(height: 16),
            TextField(
              controller: _text,
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
                          CircularProgressIndicator(),
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
                    return _HistoricalError(onRetry: _runSearch);
                  }
                  final response = snapshot.requireData;
                  final state = _effectiveHistoricalSearchState(response);
                  if (state == 'SOURCE_FAILURE') {
                    return _HistoricalError(
                      sources: response.sources,
                      onRetry: _runSearch,
                    );
                  }
                  return _HistoricalResults(
                    response: response,
                    state: state,
                    onPrevious: _start == 0
                        ? null
                        : () => _runSearch(
                            start: (_start - response.results.length).clamp(
                              0,
                              _start,
                            ),
                          ),
                    onNext: _start + response.results.length >= response.total
                        ? null
                        : () => _runSearch(
                            start: _start + response.results.length,
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
    required this.onPrevious,
    required this.onNext,
  });

  final HistoricalSearchResponse response;
  final String state;
  final VoidCallback? onPrevious;
  final VoidCallback? onNext;

  @override
  Widget build(BuildContext context) {
    final sourceMessages = response.sources
        .where((source) => source.status != 'AVAILABLE')
        .map(_historicalSourceMessage)
        .toList(growable: false);
    final noResults =
        state == 'NO_RESULTS' ||
        (response.results.isEmpty && response.total == 0);
    final statusLabel = noResults
        ? 'De historische zoekopdracht leverde geen resultaten op.${_sourceMessagesLabel(sourceMessages)}'
        : '${response.total} historische resultaten geladen.${_sourceMessagesLabel(sourceMessages)}';
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
                ...sourceMessages.map(Text.new),
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
              ...sourceMessages.map(Text.new),
            ],
          ),
        ),
        const SizedBox(height: 12),
        ...response.results.map(
          (result) => _HistoricalResultCard(result: result),
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
  const _HistoricalResultCard({required this.result});

  final HistoricalSearchResult result;

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
            Text('Privacy: ${_status(result.privacyStatus)}'),
            if (metadataAvailable && result.rights != null)
              Text('Rechten: ${result.rights}'),
            if (metadataAvailable && result.privacy != null)
              Text('Privacybron: ${result.privacy}'),
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
  const _HistoricalError({required this.onRetry, this.sources = const []});

  final List<HistoricalSourceStatus> sources;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final sourceMessages = sources
        .where((source) => source.status != 'AVAILABLE')
        .map(_historicalSourceMessage)
        .toList(growable: false);
    final label =
        'De historische bronnen zijn niet beschikbaar.${_sourceMessagesLabel(sourceMessages)}';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _HistoricalStatus(
          label: label,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text('Historisch zoeken is tijdelijk niet beschikbaar.'),
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

String _sourceMessagesLabel(List<String> messages) =>
    messages.isEmpty ? '' : ' ${messages.join(' ')}';

String historicalSourceQueryValue(HistoricalSourceChoice source) =>
    _sourceName(source);
