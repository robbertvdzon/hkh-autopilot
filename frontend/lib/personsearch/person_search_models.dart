/// Uitkomststatus van een persoonszoekjob, naar het backendcontract van
/// `POST /api/person-search`.
enum PersonSearchStatus {
  running,
  supportedAnswer,
  noResults,
  partial,
  sourceOutage;

  static PersonSearchStatus fromApiValue(String value) {
    switch (value) {
      case 'RUNNING':
        return PersonSearchStatus.running;
      case 'SUPPORTED_ANSWER':
        return PersonSearchStatus.supportedAnswer;
      case 'NO_RESULTS':
        return PersonSearchStatus.noResults;
      case 'PARTIAL':
        return PersonSearchStatus.partial;
      case 'SOURCE_OUTAGE':
        return PersonSearchStatus.sourceOutage;
      default:
        throw ArgumentError('Onbekende persoonszoekstatus: $value');
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
            (item) =>
                PersonSearchAnswerSentence.fromJson(item as Map<String, dynamic>),
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
