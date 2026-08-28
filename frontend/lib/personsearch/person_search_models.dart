/// Worker-onafhankelijk statuscontract van een persoonszoekjob (zie
/// `PersonSearchStatus` in de backend `personsearch`-module).
enum PersonSearchStatus {
  queued,
  running,
  ready,
  noEvidence,
  partial,
  failed,
  cancelled,
  expired;

  /// `true` voor elke status waarin de job niet meer verder kan veranderen.
  bool get isTerminal =>
      this != PersonSearchStatus.queued && this != PersonSearchStatus.running;

  static PersonSearchStatus fromApiValue(String value) {
    switch (value) {
      case 'QUEUED':
        return PersonSearchStatus.queued;
      case 'RUNNING':
        return PersonSearchStatus.running;
      case 'READY':
        return PersonSearchStatus.ready;
      case 'NO_EVIDENCE':
        return PersonSearchStatus.noEvidence;
      case 'PARTIAL':
        return PersonSearchStatus.partial;
      case 'FAILED':
        return PersonSearchStatus.failed;
      case 'CANCELLED':
        return PersonSearchStatus.cancelled;
      case 'EXPIRED':
        return PersonSearchStatus.expired;
      default:
        throw ArgumentError('Onbekende persoonszoekstatus: $value');
    }
  }
}

/// Consultatiestatus van één externe bron (Open Archieven of Wikidata) voor een job.
enum PersonSearchSourceConsultationStatus {
  notStarted,
  inProgress,
  succeeded,
  failed;

  static PersonSearchSourceConsultationStatus fromApiValue(String value) {
    switch (value) {
      case 'NOT_STARTED':
        return PersonSearchSourceConsultationStatus.notStarted;
      case 'IN_PROGRESS':
        return PersonSearchSourceConsultationStatus.inProgress;
      case 'SUCCEEDED':
        return PersonSearchSourceConsultationStatus.succeeded;
      case 'FAILED':
        return PersonSearchSourceConsultationStatus.failed;
      default:
        throw ArgumentError('Onbekende consultatiestatus: $value');
    }
  }
}

/// Eén feitelijke antwoordzin met de nummers van de bronmarkeringen erachter.
class PersonSearchAnswerSentence {
  const PersonSearchAnswerSentence({
    required this.text,
    required this.sourceNumbers,
  });

  final String text;
  final List<int> sourceNumbers;

  factory PersonSearchAnswerSentence.fromJson(Map<String, dynamic> json) {
    return PersonSearchAnswerSentence(
      text: json['text'] as String,
      sourceNumbers: (json['sourceNumbers'] as List<dynamic>)
          .map((n) => n as int)
          .toList(growable: false),
    );
  }
}

/// Genummerde bronmarkering: beherende instelling, brontype, archief-,
/// register-, akte-/documentnummer, recordnummer/identifier, links en
/// `checkedAt` (tijdstip van live ophalen in déze job).
class PersonSearchSourceCitation {
  const PersonSearchSourceCitation({
    required this.number,
    required this.institution,
    required this.sourceType,
    required this.archiveCode,
    required this.identifier,
    this.archiveNumber,
    this.registerNumber,
    this.deedNumber,
    required this.recordNumber,
    required this.openArchivesLink,
    this.digitalOriginalLink,
    required this.checkedAt,
  });

  final int number;
  final String institution;
  final String sourceType;
  final String archiveCode;
  final String identifier;
  final String? archiveNumber;
  final String? registerNumber;
  final String? deedNumber;
  final String recordNumber;
  final String openArchivesLink;
  final String? digitalOriginalLink;
  final DateTime checkedAt;

  factory PersonSearchSourceCitation.fromJson(Map<String, dynamic> json) {
    return PersonSearchSourceCitation(
      number: json['number'] as int,
      institution: json['institution'] as String,
      sourceType: json['sourceType'] as String,
      archiveCode: json['archiveCode'] as String,
      identifier: json['identifier'] as String,
      archiveNumber: json['archiveNumber'] as String?,
      registerNumber: json['registerNumber'] as String?,
      deedNumber: json['deedNumber'] as String?,
      recordNumber: json['recordNumber'] as String,
      openArchivesLink: json['openArchivesLink'] as String,
      digitalOriginalLink: json['digitalOriginalLink'] as String?,
      checkedAt: DateTime.parse(json['checkedAt'] as String),
    );
  }
}

/// Een vervolgspoor (`followed-connection`) naar een rol/persoon uit hetzelfde
/// gevalideerde Show-record.
class PersonSearchConnectionOption {
  const PersonSearchConnectionOption({
    required this.role,
    required this.personName,
  });

  final String role;
  final String personName;

  factory PersonSearchConnectionOption.fromJson(Map<String, dynamic> json) {
    return PersonSearchConnectionOption(
      role: json['role'] as String,
      personName: json['personName'] as String,
    );
  }
}

/// Volledig antwoord voor `supported-answer`.
class PersonSearchAnswer {
  const PersonSearchAnswer({
    required this.sentences,
    required this.sources,
    required this.connections,
    required this.disclaimer,
  });

  final List<PersonSearchAnswerSentence> sentences;
  final List<PersonSearchSourceCitation> sources;
  final List<PersonSearchConnectionOption> connections;
  final String disclaimer;

  factory PersonSearchAnswer.fromJson(Map<String, dynamic> json) {
    return PersonSearchAnswer(
      sentences: (json['sentences'] as List<dynamic>)
          .map(
            (item) => PersonSearchAnswerSentence.fromJson(
              item as Map<String, dynamic>,
            ),
          )
          .toList(growable: false),
      sources: (json['sources'] as List<dynamic>)
          .map(
            (item) => PersonSearchSourceCitation.fromJson(
              item as Map<String, dynamic>,
            ),
          )
          .toList(growable: false),
      connections: (json['connections'] as List<dynamic>)
          .map(
            (item) => PersonSearchConnectionOption.fromJson(
              item as Map<String, dynamic>,
            ),
          )
          .toList(growable: false),
      disclaimer: json['disclaimer'] as String,
    );
  }
}

/// Wikidata-contextinformatie; draagt nooit zelfstandig een archiefbewering en
/// verschijnt uitsluitend onder een sectie die letterlijk 'Context' heet.
class PersonSearchWikidataContext {
  const PersonSearchWikidataContext({required this.label, this.description});

  final String label;
  final String? description;

  factory PersonSearchWikidataContext.fromJson(Map<String, dynamic> json) {
    return PersonSearchWikidataContext(
      label: json['label'] as String,
      description: json['description'] as String?,
    );
  }
}

/// Volledige uitkomst van een indiening bij `POST /api/person-search`.
class PersonSearchResult {
  const PersonSearchResult({
    required this.jobId,
    required this.status,
    required this.originalQuery,
    this.refinementMessage,
    this.answer,
    this.context,
  });

  final String jobId;
  final PersonSearchStatus status;
  final String originalQuery;
  final String? refinementMessage;
  final PersonSearchAnswer? answer;
  final PersonSearchWikidataContext? context;

  factory PersonSearchResult.fromJson(Map<String, dynamic> json) {
    return PersonSearchResult(
      jobId: json['jobId'] as String,
      status: PersonSearchStatus.fromApiValue(json['status'] as String),
      originalQuery: json['originalQuery'] as String,
      refinementMessage: json['refinementMessage'] as String?,
      answer: json['answer'] == null
          ? null
          : PersonSearchAnswer.fromJson(json['answer'] as Map<String, dynamic>),
      context: json['context'] == null
          ? null
          : PersonSearchWikidataContext.fromJson(
              json['context'] as Map<String, dynamic>,
            ),
    );
  }
}

/// Uitkomst van een statusaanvraag/stopactie/openactie
/// (`GET|POST /api/person-search/{jobId}/...`). Bevat nooit een sessie-id.
class PersonSearchStatusResult {
  const PersonSearchStatusResult({
    required this.jobId,
    required this.status,
    required this.originalQuery,
    required this.createdAt,
    required this.updatedAt,
    required this.openArchievenStatus,
    required this.wikidataStatus,
    this.openedAt,
    this.refinementMessage,
    this.answer,
    this.context,
  });

  final String jobId;
  final PersonSearchStatus status;
  final String originalQuery;
  final DateTime createdAt;
  final DateTime updatedAt;
  final PersonSearchSourceConsultationStatus openArchievenStatus;
  final PersonSearchSourceConsultationStatus wikidataStatus;
  final DateTime? openedAt;
  final String? refinementMessage;
  final PersonSearchAnswer? answer;
  final PersonSearchWikidataContext? context;

  factory PersonSearchStatusResult.fromJson(Map<String, dynamic> json) {
    return PersonSearchStatusResult(
      jobId: json['jobId'] as String,
      status: PersonSearchStatus.fromApiValue(json['status'] as String),
      originalQuery: json['originalQuery'] as String,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
      openArchievenStatus: PersonSearchSourceConsultationStatus.fromApiValue(
        json['openArchievenStatus'] as String,
      ),
      wikidataStatus: PersonSearchSourceConsultationStatus.fromApiValue(
        json['wikidataStatus'] as String,
      ),
      openedAt: json['openedAt'] == null
          ? null
          : DateTime.parse(json['openedAt'] as String),
      refinementMessage: json['refinementMessage'] as String?,
      answer: json['answer'] == null
          ? null
          : PersonSearchAnswer.fromJson(json['answer'] as Map<String, dynamic>),
      context: json['context'] == null
          ? null
          : PersonSearchWikidataContext.fromJson(
              json['context'] as Map<String, dynamic>,
            ),
    );
  }
}

/// Aantal en job-ids van lopende en gereedstaande-niet-geopende jobs van
/// uitsluitend de huidige sessie (`GET /api/person-search/session`).
class PersonSearchSessionIndicator {
  const PersonSearchSessionIndicator({
    required this.runningCount,
    required this.readyUnopenedCount,
    required this.runningJobIds,
    required this.readyUnopenedJobIds,
  });

  final int runningCount;
  final int readyUnopenedCount;
  final List<String> runningJobIds;
  final List<String> readyUnopenedJobIds;

  factory PersonSearchSessionIndicator.fromJson(Map<String, dynamic> json) {
    return PersonSearchSessionIndicator(
      runningCount: json['runningCount'] as int,
      readyUnopenedCount: json['readyUnopenedCount'] as int,
      runningJobIds: (json['runningJobIds'] as List<dynamic>)
          .cast<String>()
          .toList(growable: false),
      readyUnopenedJobIds: (json['readyUnopenedJobIds'] as List<dynamic>)
          .cast<String>()
          .toList(growable: false),
    );
  }
}
