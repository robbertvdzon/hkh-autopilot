/// Uitkomststatus van een onderwerp/voorwerp/gebeurtenis-zoekopdracht (zie
/// `TopicSearchOutcome` in de backend `topicsearch`-module). Volledig
/// synchroon: geen `QUEUED`/`RUNNING`-tussenstatus.
enum TopicSearchStatus {
  ready,
  empty,
  outage;

  static TopicSearchStatus fromApiValue(String value) {
    switch (value) {
      case 'READY':
        return TopicSearchStatus.ready;
      case 'EMPTY':
        return TopicSearchStatus.empty;
      case 'OUTAGE':
        return TopicSearchStatus.outage;
      default:
        throw ArgumentError('Onbekende onderwerp-zoekstatus: $value');
    }
  }
}

/// Leesbare licentie-/rechtenbadge (tekst, niet uitsluitend kleur).
class TopicSearchLicenseBadge {
  const TopicSearchLicenseBadge({required this.text, this.url});

  final String text;
  final String? url;

  factory TopicSearchLicenseBadge.fromJson(Map<String, dynamic> json) {
    return TopicSearchLicenseBadge(
      text: json['licenseText'] as String,
      url: json['licenseUrl'] as String?,
    );
  }
}

/// Eén geldig Europeana-record: eigen kaartje, geen samenvattende zin.
class TopicSearchRecord {
  const TopicSearchRecord({
    required this.title,
    required this.dataProvider,
    required this.license,
    required this.sourceUrl,
  });

  final String title;
  final String dataProvider;
  final TopicSearchLicenseBadge license;
  final String sourceUrl;

  factory TopicSearchRecord.fromJson(Map<String, dynamic> json) {
    return TopicSearchRecord(
      title: json['title'] as String,
      dataProvider: json['dataProvider'] as String,
      license: TopicSearchLicenseBadge.fromJson(json),
      sourceUrl: json['sourceUrl'] as String,
    );
  }
}

/// Apart gelabeld 'Context'-blok; draagt nooit zelfstandig een bewering.
class TopicSearchContext {
  const TopicSearchContext({required this.label, this.description});

  final String label;
  final String? description;

  factory TopicSearchContext.fromJson(Map<String, dynamic> json) {
    return TopicSearchContext(
      label: json['label'] as String,
      description: json['description'] as String?,
    );
  }
}

/// Volledig antwoord voor `topic-results`.
class TopicSearchAnswer {
  const TopicSearchAnswer({
    required this.topicSearchTerm,
    required this.records,
    this.context,
    required this.checkedAt,
  });

  final String topicSearchTerm;
  final List<TopicSearchRecord> records;
  final TopicSearchContext? context;
  final DateTime checkedAt;

  factory TopicSearchAnswer.fromJson(Map<String, dynamic> json) {
    return TopicSearchAnswer(
      topicSearchTerm: json['topicSearchTerm'] as String,
      records: (json['records'] as List<dynamic>)
          .map((item) => TopicSearchRecord.fromJson(item as Map<String, dynamic>))
          .toList(growable: false),
      context: json['context'] == null
          ? null
          : TopicSearchContext.fromJson(json['context'] as Map<String, dynamic>),
      checkedAt: DateTime.parse(json['checkedAt'] as String),
    );
  }
}

/// Volledige uitkomst van `POST /api/topic-search`.
class TopicSearchResult {
  const TopicSearchResult({
    required this.status,
    required this.topicSearchTerm,
    this.answer,
    this.refinementSuggestions = const [],
  });

  final TopicSearchStatus status;
  final String topicSearchTerm;
  final TopicSearchAnswer? answer;
  final List<String> refinementSuggestions;

  factory TopicSearchResult.fromJson(Map<String, dynamic> json) {
    return TopicSearchResult(
      status: TopicSearchStatus.fromApiValue(json['status'] as String),
      topicSearchTerm: json['topicSearchTerm'] as String,
      answer: json['answer'] == null
          ? null
          : TopicSearchAnswer.fromJson(json['answer'] as Map<String, dynamic>),
      refinementSuggestions:
          (json['refinementSuggestions'] as List<dynamic>? ?? const [])
              .map((item) => item as String)
              .toList(growable: false),
    );
  }
}
