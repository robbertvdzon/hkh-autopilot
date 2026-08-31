/// Uitkomststatus van een plek/gebouw-zoekopdracht (zie `PlaceSearchOutcome`
/// in de backend `placesearch`-module). Anders dan `personsearch` is deze
/// route volledig synchroon: geen `QUEUED`/`RUNNING`-tussenstatus.
enum PlaceSearchStatus {
  ready,
  noMatch,
  outage;

  static PlaceSearchStatus fromApiValue(String value) {
    switch (value) {
      case 'READY':
        return PlaceSearchStatus.ready;
      case 'NO_MATCH':
        return PlaceSearchStatus.noMatch;
      case 'OUTAGE':
        return PlaceSearchStatus.outage;
      default:
        throw ArgumentError('Onbekende plek/gebouw-zoekstatus: $value');
    }
  }
}

/// Genummerde bronmarkering: verwijst altijd naar hetzelfde Wikidata-item.
class PlaceSearchSourceCitation {
  const PlaceSearchSourceCitation({
    required this.number,
    required this.qid,
    required this.wikidataLink,
    required this.checkedAt,
  });

  final int number;
  final String qid;
  final String wikidataLink;
  final DateTime checkedAt;

  factory PlaceSearchSourceCitation.fromJson(Map<String, dynamic> json) {
    return PlaceSearchSourceCitation(
      number: json['number'] as int,
      qid: json['qid'] as String,
      wikidataLink: json['wikidataLink'] as String,
      checkedAt: DateTime.parse(json['checkedAt'] as String),
    );
  }
}

/// Eén feitelijke antwoordzin met de nummers van de bronmarkeringen erachter.
class PlaceSearchAnswerSentence {
  const PlaceSearchAnswerSentence({
    required this.text,
    required this.sourceNumbers,
  });

  final String text;
  final List<int> sourceNumbers;

  factory PlaceSearchAnswerSentence.fromJson(Map<String, dynamic> json) {
    return PlaceSearchAnswerSentence(
      text: json['text'] as String,
      sourceNumbers: (json['sourceNumbers'] as List<dynamic>)
          .map((n) => n as int)
          .toList(growable: false),
    );
  }
}

/// Eén Commons-afbeelding, gededupliceerd op bestandsnaam.
class PlaceSearchImage {
  const PlaceSearchImage({
    required this.url,
    required this.fileName,
    this.license,
    required this.filePageUrl,
  });

  final String url;
  final String fileName;
  final String? license;
  final String filePageUrl;

  factory PlaceSearchImage.fromJson(Map<String, dynamic> json) {
    return PlaceSearchImage(
      url: json['url'] as String,
      fileName: json['fileName'] as String,
      license: json['license'] as String?,
      filePageUrl: json['filePageUrl'] as String,
    );
  }
}

/// Volledig antwoord voor `place-answer`.
class PlaceSearchAnswer {
  const PlaceSearchAnswer({
    required this.qid,
    required this.label,
    this.description,
    required this.sentences,
    this.contextSentence,
    required this.sources,
    required this.images,
    required this.commonsOutage,
    required this.disclaimer,
    required this.checkedAt,
  });

  final String qid;
  final String label;
  final String? description;
  final List<PlaceSearchAnswerSentence> sentences;
  final PlaceSearchAnswerSentence? contextSentence;
  final List<PlaceSearchSourceCitation> sources;
  final List<PlaceSearchImage> images;

  /// `true` wanneer Wikimedia Commons zelf mislukte (niet-2xx/timeout/
  /// ongeldige JSON); een legitiem lege galerij (geen categorie/P18 of nul
  /// resultaten) is `false` met een lege [images]-lijst.
  final bool commonsOutage;
  final String disclaimer;
  final DateTime checkedAt;

  factory PlaceSearchAnswer.fromJson(Map<String, dynamic> json) {
    return PlaceSearchAnswer(
      qid: json['qid'] as String,
      label: json['label'] as String,
      description: json['description'] as String?,
      sentences: (json['sentences'] as List<dynamic>)
          .map(
            (item) => PlaceSearchAnswerSentence.fromJson(
              item as Map<String, dynamic>,
            ),
          )
          .toList(growable: false),
      contextSentence: json['contextSentence'] == null
          ? null
          : PlaceSearchAnswerSentence.fromJson(
              json['contextSentence'] as Map<String, dynamic>,
            ),
      sources: (json['sources'] as List<dynamic>)
          .map(
            (item) => PlaceSearchSourceCitation.fromJson(
              item as Map<String, dynamic>,
            ),
          )
          .toList(growable: false),
      images: (json['images'] as List<dynamic>)
          .map(
            (item) => PlaceSearchImage.fromJson(item as Map<String, dynamic>),
          )
          .toList(growable: false),
      commonsOutage: json['commonsOutage'] as bool,
      disclaimer: json['disclaimer'] as String,
      checkedAt: DateTime.parse(json['checkedAt'] as String),
    );
  }
}

/// Kandidaatlabel dat als verfijningsvoorstel getoond wordt bij >1 match.
class PlaceSearchCandidate {
  const PlaceSearchCandidate({required this.qid, required this.label});

  final String qid;
  final String label;

  factory PlaceSearchCandidate.fromJson(Map<String, dynamic> json) {
    return PlaceSearchCandidate(
      qid: json['qid'] as String,
      label: json['label'] as String,
    );
  }
}

/// Volledige uitkomst van `POST /api/place-search`.
class PlaceSearchResult {
  const PlaceSearchResult({
    required this.status,
    required this.candidateTerm,
    this.answer,
    this.refinementCandidates = const [],
  });

  final PlaceSearchStatus status;
  final String candidateTerm;
  final PlaceSearchAnswer? answer;
  final List<PlaceSearchCandidate> refinementCandidates;

  factory PlaceSearchResult.fromJson(Map<String, dynamic> json) {
    return PlaceSearchResult(
      status: PlaceSearchStatus.fromApiValue(json['status'] as String),
      candidateTerm: json['candidateTerm'] as String,
      answer: json['answer'] == null
          ? null
          : PlaceSearchAnswer.fromJson(json['answer'] as Map<String, dynamic>),
      refinementCandidates:
          (json['refinementCandidates'] as List<dynamic>? ?? const [])
              .map(
                (item) =>
                    PlaceSearchCandidate.fromJson(item as Map<String, dynamic>),
              )
              .toList(growable: false),
    );
  }
}
