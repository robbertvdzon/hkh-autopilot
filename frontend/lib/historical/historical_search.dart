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
      (uri.scheme.toLowerCase() == 'http' ||
          uri.scheme.toLowerCase() == 'https') &&
      uri.host.isNotEmpty;
}

String? _nonEmptyText(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

String? _stringValue(Object? value) => value is String ? value : null;

bool _isOpenArchievenContractValid({
  required String? sourceName,
  required String? stableIdentifier,
  required String? originalSourceUrl,
  required String? legacyIdentifier,
  required String? legacySourceUrl,
}) {
  final hasRequiredValues =
      _nonEmptyText(sourceName) != null &&
      _nonEmptyText(stableIdentifier) != null &&
      _isHttpUrl(originalSourceUrl);
  if (!hasRequiredValues) return false;

  // When both the normalized legacy fields and the explicit Open Archieven
  // fields are present, they must identify the same source record. A mismatch
  // is not repaired locally and therefore makes the result undisplayable.
  final identifiersMatch =
      _nonEmptyText(legacyIdentifier) == null ||
      legacyIdentifier!.trim() == stableIdentifier!.trim();
  final urlsMatch =
      _nonEmptyText(legacySourceUrl) == null ||
      legacySourceUrl!.trim() == originalSourceUrl!.trim();
  return identifiersMatch && urlsMatch;
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
    this.sourceName,
    this.stableIdentifier,
    this.originalSourceUrl,
    this.openArchievenContractValid,
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

  factory HistoricalSearchResult.fromJson(Map<String, dynamic> json) {
    final source = _stringValue(json['source']) ?? '';
    final sourceRecordId = _stringValue(json['sourceRecordId']);
    final stableIdentifier =
        _stringValue(json['stable_identifier']) ??
        _stringValue(json['stableIdentifier']);
    final stableUrl = _stringValue(json['stableUrl']);
    final originalSourceUrl =
        _stringValue(json['original_source_url']) ??
        _stringValue(json['originalSourceUrl']);
    final sourceName =
        _stringValue(json['source_name']) ?? _stringValue(json['sourceName']);

    return HistoricalSearchResult(
      source: source,
      sourceRecordId: sourceRecordId ?? stableIdentifier ?? '',
      stableUrl: stableUrl ?? originalSourceUrl ?? '',
      title: _stringValue(json['title']),
      description: _stringValue(json['description']),
      place: _stringValue(json['place']),
      person: _stringValue(json['person']),
      event: _stringValue(json['event']),
      dateStart: _stringValue(json['dateStart']),
      dateEnd: _stringValue(json['dateEnd']),
      institution: _stringValue(json['institution']),
      rights: _stringValue(json['rights']),
      privacy: _stringValue(json['privacy']),
      retrievedAt: DateTime.parse(json['retrievedAt'] as String),
      technicalStatus: _stringValue(json['technicalStatus']) ?? 'UNKNOWN',
      metadataRights: _stringValue(json['metadataRights']) ?? 'UNKNOWN',
      objectMediaRights: _stringValue(json['objectMediaRights']) ?? 'UNKNOWN',
      privacyStatus: _stringValue(json['privacyStatus']) ?? 'UNKNOWN',
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
      sourceName: sourceName,
      stableIdentifier: stableIdentifier,
      originalSourceUrl: originalSourceUrl,
      openArchievenContractValid: source == 'OPEN_ARCHIEVEN'
          ? _isOpenArchievenContractValid(
              sourceName: sourceName,
              stableIdentifier: stableIdentifier,
              originalSourceUrl: originalSourceUrl,
              legacyIdentifier: sourceRecordId,
              legacySourceUrl: stableUrl,
            )
          : null,
    );
  }

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
  final String? sourceName;
  final String? stableIdentifier;
  final String? originalSourceUrl;

  /// Set by JSON contract mapping for Open Archieven. A null value denotes a
  /// normalized in-memory result constructed by existing callers.
  final bool? openArchievenContractValid;

  String get normalizedSourceName =>
      sourceName ??
      switch (source) {
        'EUROPEANA' => 'Europeana',
        'OPEN_ARCHIEVEN' => 'Open Archieven',
        _ => source,
      };

  String get normalizedStableIdentifier => stableIdentifier ?? sourceRecordId;

  String get normalizedOriginalSourceUrl => originalSourceUrl ?? stableUrl;

  bool get isPubliclyDisplayable {
    if (source == 'OPEN_ARCHIEVEN' && openArchievenContractValid == false) {
      return false;
    }
    return _nonEmptyText(normalizedStableIdentifier) != null &&
        _isHttpUrl(normalizedOriginalSourceUrl);
  }
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
    this.querySemantics,
  });

  factory HistoricalSourceStatus.fromJson(Map<String, dynamic> json) =>
      HistoricalSourceStatus(
        source: json['source'] as String,
        status: json['status'] as String,
        message: json['message'] as String?,
        resultCount: json['resultCount'] as int?,
        heemskerkCount: json['heemskerkCount'] as int?,
        querySemantics: json['querySemantics'] is List
            ? (json['querySemantics'] as List).whereType<String>().toList(
                growable: false,
              )
            : null,
      );

  final String source;
  final String status;
  final String? message;
  final int? resultCount;
  final int? heemskerkCount;
  final List<String>? querySemantics;
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

class _HistoricalSearchContext {
  const _HistoricalSearchContext({
    required this.text,
    required this.place,
    required this.person,
    required this.event,
    required this.fromYear,
    required this.toYear,
    required this.source,
    required this.start,
    required this.limit,
  });

  final String? text;
  final String? place;
  final String? person;
  final String? event;
  final String? fromYear;
  final String? toYear;
  final HistoricalSourceChoice? source;
  final int start;
  final int limit;
}

class _CompletedHistoricalSearch {
  const _CompletedHistoricalSearch({
    required this.context,
    required this.response,
  });

  final _HistoricalSearchContext context;
  final HistoricalSearchResponse response;
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
  _CompletedHistoricalSearch? _lastCompletedSearch;
  _HistoricalSearchContext? _lastRequestContext;
  _CompletedHistoricalSearch? _retryPreviousSearch;
  bool _retryInProgress = false;
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

  void _runSearch({
    int? start,
    _HistoricalSearchContext? context,
    bool isRetry = false,
  }) {
    final nextContext =
        context ??
        _HistoricalSearchContext(
          text: _optionalHistoricalFilter(_text.text),
          place: _optionalHistoricalFilter(_place.text),
          person: _optionalHistoricalFilter(_person.text),
          event: _optionalHistoricalFilter(_event.text),
          fromYear: _optionalHistoricalFilter(_fromYear.text),
          toYear: _optionalHistoricalFilter(_toYear.text),
          source: _source,
          start: start ?? 0,
          limit: _limit,
        );
    final previousSearch = isRetry ? _lastCompletedSearch : null;
    // Keep only the normalized request context before starting the request.
    // This also covers a first request that fails before a completed response
    // can be stored, without retaining a response, history, or provider data.
    _lastRequestContext = nextContext;
    final future = widget.source.loadHistoricalSearch(
      text: nextContext.text,
      place: nextContext.place,
      person: nextContext.person,
      event: nextContext.event,
      fromYear: nextContext.fromYear,
      toYear: nextContext.toYear,
      source: nextContext.source,
      start: nextContext.start,
      limit: nextContext.limit,
    );
    setState(() {
      _search = future;
      if (!isRetry) _lastCompletedSearch = null;
      _retryPreviousSearch = previousSearch;
      if (!isRetry) _retryInProgress = false;
    });
    future.then(
      (response) {
        if (!mounted || !identical(_search, future)) return;
        final state = _effectiveHistoricalSearchState(response);
        setState(() {
          if (isRetry) _retryInProgress = false;
          // SOURCE_FAILURE is a failed retry: retain the previous valid
          // snapshot so its results and source statuses remain visible.
          if (!isRetry || state != 'SOURCE_FAILURE') {
            _lastCompletedSearch = _CompletedHistoricalSearch(
              context: nextContext,
              response: response,
            );
            _retryPreviousSearch = null;
          }
        });
      },
      onError: (Object _, StackTrace __) {
        // FutureBuilder renders the safe transport-failure state. Keeping the
        // retry snapshot here is intentional and contains no exception text.
        if (!mounted || !identical(_search, future)) return;
        setState(() {
          if (isRetry) _retryInProgress = false;
        });
      },
    );
  }

  void _retrySearch() {
    if (_retryInProgress) return;
    _retryInProgress = true;
    final previousSearch = _lastCompletedSearch;
    final previousContext = previousSearch?.context ?? _lastRequestContext;
    if (previousContext == null) {
      _runSearch(isRetry: true);
      return;
    }
    _runSearch(context: previousContext, isRetry: true);
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
              key: const Key('historical-search-text'),
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
              key: const Key('historical-search-place'),
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
                    final previousSearch = _retryPreviousSearch;
                    if (previousSearch != null) {
                      final previousState = _effectiveHistoricalSearchState(
                        previousSearch.response,
                      );
                      if (previousSearch.response.results.isNotEmpty ||
                          previousState == 'PARTIAL_AVAILABILITY') {
                        return _HistoricalResults(
                          response: previousSearch.response,
                          state: previousState,
                          source: widget.source,
                          onRetry: _retrySearch,
                          retryInProgress: true,
                        );
                      }
                      return _HistoricalError(
                        sources: previousSearch.response.sources,
                        onRetry: _retrySearch,
                        onAdjust: _focusSearchForm,
                        retryInProgress: true,
                      );
                    }
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
                    final previousSearch = _retryPreviousSearch;
                    if (previousSearch != null &&
                        previousSearch.response.results.isNotEmpty) {
                      return _HistoricalResults(
                        response: previousSearch.response,
                        state: _effectiveHistoricalSearchState(
                          previousSearch.response,
                        ),
                        source: widget.source,
                        onRetry: _retrySearch,
                        retryFailed: true,
                      );
                    }
                    return _HistoricalError(
                      sources: previousSearch?.response.sources ?? const [],
                      onRetry: _retrySearch,
                      onAdjust: _focusSearchForm,
                      retryFailureMessage: previousSearch == null
                          ? null
                          : 'Nieuwe poging mislukt door een tijdelijke '
                                'transportfout. De vorige uitkomst blijft '
                                'beschikbaar.',
                    );
                  }
                  final response = snapshot.requireData;
                  final state = _effectiveHistoricalSearchState(response);
                  if (state == 'SOURCE_FAILURE') {
                    final previousSearch = _retryPreviousSearch;
                    if (previousSearch != null &&
                        previousSearch.response.results.isNotEmpty) {
                      return _HistoricalResults(
                        response: previousSearch.response,
                        state: _effectiveHistoricalSearchState(
                          previousSearch.response,
                        ),
                        source: widget.source,
                        onRetry: _retrySearch,
                        retryFailureSources: response.sources,
                        retryFailed: true,
                      );
                    }
                    return _HistoricalError(
                      sources: response.sources,
                      onRetry: _retrySearch,
                      onAdjust: _focusSearchForm,
                      retryFailureMessage: previousSearch == null
                          ? null
                          : 'Nieuwe poging mislukt; de bronfout wordt '
                                'hieronder afzonderlijk gemeld.',
                    );
                  }
                  return _HistoricalResults(
                    response: response,
                    state: state,
                    source: widget.source,
                    onRetry: _retrySearch,
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
    this.onRetry,
    this.retryInProgress = false,
    this.retryFailed = false,
    this.retryFailureSources = const [],
    this.onPrevious,
    this.onNext,
  });

  final HistoricalSearchResponse response;
  final String state;
  final HistoricalSearchSource source;
  final VoidCallback? onRetry;
  final bool retryInProgress;
  final bool retryFailed;
  final List<HistoricalSourceStatus> retryFailureSources;
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
    final retryLabel = retryInProgress
        ? ' Nieuwe poging voor dezelfde historische zoekopdracht wordt '
              'uitgevoerd. De vorige uitkomst blijft zichtbaar.'
        : retryFailed
        ? ' Nieuwe poging mislukt. De vorige uitkomst blijft zichtbaar.'
        : '';
    final retryFailureLabel = retryFailureSources.isEmpty
        ? ''
        : ' Nieuwe bronfout: ${_sourceMessagesLabel(retryFailureSources.map(_historicalSourceMessage).toList(growable: false))}';
    final fullStatusLabel = '$statusLabel$retryLabel$retryFailureLabel';
    final interpretationLabels = _historicalInterpretationLabels(
      response.sources,
    );
    final accessibleStatusLabel =
        '$fullStatusLabel'
        '${interpretationLabels.isEmpty ? '' : ' ${interpretationLabels.join(' ')}'}';
    final canRetry =
        onRetry != null &&
        !retryInProgress &&
        (state == 'PARTIAL_AVAILABILITY' || retryFailed);
    if (noResults) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _HistoricalStatus(
            label: accessibleStatusLabel,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (retryInProgress)
                  const Text(
                    'Nieuwe poging voor dezelfde historische zoekopdracht '
                    'wordt uitgevoerd; de vorige uitkomst blijft zichtbaar.',
                  ),
                if (retryFailed)
                  const Text(
                    'Nieuwe poging mislukt; de vorige uitkomst blijft '
                    'zichtbaar.',
                  ),
                const Text('Geen historische resultaten gevonden.'),
                ...sourceSummaries.map(Text.new),
                ...interpretationLabels.map(Text.new),
                if (retryFailureSources.isNotEmpty) ...[
                  const Text('Melding van de nieuwe poging:'),
                  ...retryFailureSources.map(
                    (source) => Text(_historicalSourceMessage(source)),
                  ),
                ],
              ],
            ),
          ),
          if (canRetry) ...[const SizedBox(height: 12), _retryButton(onRetry!)],
        ],
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _HistoricalStatus(
          label: accessibleStatusLabel,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (retryInProgress)
                const Text(
                  'Nieuwe poging voor dezelfde historische zoekopdracht '
                  'wordt uitgevoerd; de vorige uitkomst blijft zichtbaar.',
                ),
              if (retryInProgress)
                const Padding(
                  padding: EdgeInsets.only(top: 8),
                  child: ExcludeSemantics(child: LinearProgressIndicator()),
                ),
              if (retryFailed)
                const Text(
                  'Nieuwe poging mislukt; de vorige uitkomst blijft zichtbaar.',
                ),
              Text('${response.total} historische resultaten'),
              ...sourceSummaries.map(Text.new),
              ...interpretationLabels.map(Text.new),
              if (retryFailureSources.isNotEmpty) ...[
                const Text('Melding van de nieuwe poging:'),
                ...retryFailureSources.map(
                  (source) => Text(_historicalSourceMessage(source)),
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 12),
        ...response.results
            .where((result) => result.isPubliclyDisplayable)
            .map(
              (result) => _HistoricalResultCard(
                result: result,
                response: response,
                source: source,
              ),
            ),
        if (canRetry) ...[const SizedBox(height: 4), _retryButton(onRetry!)],
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

  Widget _retryButton(VoidCallback onPressed) => OutlinedButton.icon(
    key: const Key('historical-search-retry'),
    onPressed: onPressed,
    icon: const Icon(Icons.refresh),
    label: const Text('Opnieuw proberen'),
  );
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
    final title = metadataAvailable ? _nonEmptyText(result.title) : null;
    final description = metadataAvailable
        ? _nonEmptyText(result.description)
        : null;
    final heading = title ?? description;
    final dateStart = metadataAvailable
        ? _nonEmptyText(result.dateStart)
        : null;
    final dateEnd = metadataAvailable ? _nonEmptyText(result.dateEnd) : null;
    return Card(
      key: Key('historical-result-card-${result.sourceRecordId}'),
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (heading != null)
              Text(heading, style: Theme.of(context).textTheme.titleMedium),
            if (title != null && description != null) Text(description),
            if (metadataAvailable && _nonEmptyText(result.place) != null)
              Text('Plaats: ${_nonEmptyText(result.place)}'),
            if (metadataAvailable && _nonEmptyText(result.institution) != null)
              Text('Bronhouder: ${_nonEmptyText(result.institution)}'),
            if (metadataAvailable && _nonEmptyText(result.person) != null)
              Text('Persoon: ${_nonEmptyText(result.person)}'),
            if (metadataAvailable && _nonEmptyText(result.event) != null)
              Text('Gebeurtenis: ${_nonEmptyText(result.event)}'),
            if (dateStart != null || dateEnd != null)
              Text(
                'Datering: ${dateStart ?? dateEnd}${dateStart != null && dateEnd != null ? '–$dateEnd' : ''}',
              ),
            Text('Bronnaam: ${result.normalizedSourceName}'),
            Text('Bronidentifier: ${result.normalizedStableIdentifier}'),
            Text('Opgehaald: ${result.retrievedAt.toUtc().toIso8601String()}'),
            Text(
              'Technische beschikbaarheid: ${_status(result.technicalStatus)}',
            ),
            Text(
              'Metadatarechten: ${_metadataRightsStatus(result.metadataRights)}',
            ),
            Text(
              'Object-/mediarechten: ${_objectMediaRightsStatus(result.objectMediaRights)}',
            ),
            HistoricalRightsExplanation(
              keyPrefix:
                  'historical-rights-explanation-${result.sourceRecordId}',
            ),
            Text('Privacy: ${_privacyStatus(result.privacyStatus)}'),
            if (metadataAvailable && _nonEmptyText(result.rights) != null)
              Text('Rechten: ${_nonEmptyText(result.rights)}'),
            if (metadataAvailable && _nonEmptyText(result.privacy) != null)
              Text('Privacybron: ${_nonEmptyText(result.privacy)}'),
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
              child: Semantics(
                link: true,
                label: historicalExternalLinkSemanticLabel,
                child: TextButton.icon(
                  key: const Key('historical-external-link'),
                  onPressed: () =>
                      openExternalLink(result.normalizedOriginalSourceUrl),
                  icon: const Icon(Icons.open_in_new),
                  label: const Text(historicalExternalLinkLabel),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _status(String value) => switch (value) {
    'AVAILABLE' => 'Toegestaan',
    'DISABLED' => 'Niet beschikbaar',
    _ => 'Onbekend',
  };
}

const historicalExternalLinkLabel = 'Externe bron openen in nieuw tabblad';
// The visible label already announces the new-tab behavior and is reused as
// the semantic label so keyboard and screen-reader users receive one clear
// action name.
const historicalExternalLinkSemanticLabel = historicalExternalLinkLabel;

String _metadataRightsStatus(String value) => switch (value) {
  'ALLOWED' => 'Toegestaan',
  'RESTRICTED' => 'Beperkt',
  _ => 'Onbekend',
};

String _objectMediaRightsStatus(String value) => switch (value) {
  'ALLOWED' => 'Toegestaan',
  'RESTRICTED' => 'Beperkt',
  _ => 'Onbekend',
};

String _privacyStatus(String value) => switch (value) {
  'CLEAR' => 'Toegestaan',
  'BLOCKED' => 'Beperkt',
  _ => 'Onbekend',
};

class _HistoricalError extends StatelessWidget {
  const _HistoricalError({
    required this.onRetry,
    required this.onAdjust,
    this.sources = const [],
    this.retryInProgress = false,
    this.retryFailureMessage,
  });

  final List<HistoricalSourceStatus> sources;
  final VoidCallback onRetry;
  final VoidCallback onAdjust;
  final bool retryInProgress;
  final String? retryFailureMessage;

  @override
  Widget build(BuildContext context) {
    final sourceMessages = sources
        .where((source) => source.status != 'AVAILABLE')
        .map(_historicalSourceMessage)
        .toList(growable: false);
    final interpretationLabels = _historicalInterpretationLabels(sources);
    final label = retryInProgress
        ? 'Historische zoekresultaten worden geladen.'
        : 'Geen historische bronnen konden worden geraadpleegd.${_sourceMessagesLabel(sourceMessages)}'
              '${retryFailureMessage == null ? '' : ' $retryFailureMessage'}'
              '${interpretationLabels.isEmpty ? '' : ' ${interpretationLabels.join(' ')}'}';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _HistoricalStatus(
          label: label,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (retryInProgress)
                const Text(
                  'Nieuwe poging voor dezelfde historische zoekopdracht '
                  'wordt uitgevoerd; de vorige uitkomst blijft zichtbaar.',
                ),
              if (retryInProgress)
                const Padding(
                  padding: EdgeInsets.only(top: 8),
                  child: ExcludeSemantics(child: LinearProgressIndicator()),
                ),
              if (retryFailureMessage != null) Text(retryFailureMessage!),
              const Text(
                'Geen historische bronnen konden worden geraadpleegd.',
              ),
              ...sourceMessages.map(Text.new),
              ...interpretationLabels.map(Text.new),
            ],
          ),
        ),
        const SizedBox(height: 12),
        if (!retryInProgress)
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
    'TIMEOUT' => 'Open Archieven reageerde niet op tijd',
    'HTTP_ERROR' => 'Open Archieven gaf een fout bij het opvragen',
    'INVALID_JSON' => 'Open Archieven stuurde een onleesbaar antwoord',
    'MISSING_REQUIRED_FIELDS' =>
      'Open Archieven stuurde een onvolledig antwoord',
    'RATE_LIMITED' =>
      'Open Archieven is tijdelijk niet beschikbaar door verzoekbeperking',
    _ => 'niet beschikbaar',
  };
  if (const {
    'TIMEOUT',
    'HTTP_ERROR',
    'INVALID_JSON',
    'MISSING_REQUIRED_FIELDS',
    'RATE_LIMITED',
  }.contains(source.status)) {
    return '$status.';
  }
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

const _historicalQuerySemanticLabels = <String, String>{
  'name': 'naam',
  'eventplace': 'plaats',
  'birthplace': 'geboorteplaats',
};

String _historicalSourceInterpretation(HistoricalSourceStatus source) {
  if (source.source != 'OPEN_ARCHIEVEN') return '';
  final semantics = source.querySemantics
      ?.map((parameter) {
        final label = _historicalQuerySemanticLabels[parameter];
        return label == null ? null : '$label ($parameter)';
      })
      .whereType<String>()
      .toSet()
      .toList(growable: false);
  if (semantics == null || semantics.isEmpty) {
    return 'Zoekinterpretatie: niet beschikbaar.';
  }
  return 'Zoekinterpretatie: ${semantics.join(', ')}.';
}

List<String> _historicalInterpretationLabels(
  Iterable<HistoricalSourceStatus> sources,
) {
  final openArchievenSources = sources
      .where((source) => source.source == 'OPEN_ARCHIEVEN')
      .toList(growable: false);
  if (openArchievenSources.isEmpty) {
    return const ['Zoekinterpretatie: niet beschikbaar.'];
  }
  return openArchievenSources
      .map(_historicalSourceInterpretation)
      .toList(growable: false);
}

String _sourceMessagesLabel(List<String> messages) =>
    messages.isEmpty ? '' : ' ${messages.join(' ')}';

String historicalSourceQueryValue(HistoricalSourceChoice source) =>
    _sourceName(source);
