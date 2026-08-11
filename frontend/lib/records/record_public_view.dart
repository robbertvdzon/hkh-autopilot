/// Afgeleide, publiek zichtbare status van precies één record, geleverd door
/// `GET /api/records/{localIdentifier}`. Bevat uitsluitend niet-privacygevoelige,
/// publicatiewaardige velden; nooit de ruwe `RecordIntakeRecord`-data.
enum RecordPublicStatus { noIntake, savedWithoutSource, confirmed }

RecordPublicStatus _statusFromJson(String value) => switch (value) {
  'CONFIRMED' => RecordPublicStatus.confirmed,
  'SAVED_WITHOUT_SOURCE' => RecordPublicStatus.savedWithoutSource,
  _ => RecordPublicStatus.noIntake,
};

class RecordPublicView {
  const RecordPublicView({
    required this.localIdentifier,
    required this.status,
    this.name,
    this.birthYear,
    this.deathYear,
    this.license,
    this.sourceUri,
    this.confirmedAt,
  });

  factory RecordPublicView.fromJson(Map<String, dynamic> json) =>
      RecordPublicView(
        localIdentifier: json['localIdentifier'] as String,
        status: _statusFromJson(json['status'] as String),
        name: json['name'] as String?,
        birthYear: json['birthYear'] as String?,
        deathYear: json['deathYear'] as String?,
        license: json['license'] as String?,
        sourceUri: json['sourceUri'] as String?,
        confirmedAt: json['confirmedAt'] == null
            ? null
            : DateTime.parse(json['confirmedAt'] as String),
      );

  final String localIdentifier;
  final RecordPublicStatus status;
  final String? name;
  final String? birthYear;
  final String? deathYear;
  final String? license;
  final String? sourceUri;
  final DateTime? confirmedAt;
}

abstract interface class RecordPublicSource {
  /// Haalt de afgeleide publieke status van een record op. Levert altijd een
  /// resultaat op (nooit een 404), ook wanneer er geen intake voor deze
  /// `localIdentifier` bestaat: dat wordt uitgedrukt via [RecordPublicStatus.noIntake].
  Future<RecordPublicView> loadRecord(String localIdentifier);
}
