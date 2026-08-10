import 'dart:convert';

import 'package:http/http.dart' as http;

import '../auth/admin_session.dart';

class RecordIntakeExternalLinkInput {
  const RecordIntakeExternalLinkInput({
    required this.durableUrl,
    required this.linkRationale,
    required this.uncertainty,
  });

  final String durableUrl;
  final String linkRationale;
  final String uncertainty;

  Map<String, dynamic> toJson() => {
    'durableUrl': durableUrl,
    'linkRationale': linkRationale,
    'uncertainty': uncertainty,
  };
}

class RecordIntakeInput {
  const RecordIntakeInput({
    required this.localIdentifier,
    required this.title,
    required this.description,
    required this.dating,
    required this.provenance,
    required this.rightsStatus,
    required this.privacyClassification,
    required this.accessUrl,
    this.externalLink,
    this.deceasedStatus,
    this.nextOfKinConfirmed,
    this.confirmExternalArchiveData,
  });

  final String localIdentifier;
  final String? title;
  final String? description;
  final String dating;
  final String provenance;
  final String rightsStatus;
  final String privacyClassification;
  final String accessUrl;
  final RecordIntakeExternalLinkInput? externalLink;

  /// Overlijdensstatus van de hoofdpersoon (`onbekend`/`overleden`/`levend`); fail-closed
  /// `onbekend` zonder expliciete invoer.
  final String? deceasedStatus;

  /// Bevestigt of het record gegevens van een nog levende nabestaande bevat.
  final bool? nextOfKinConfirmed;

  /// Vraagt bij opslaan een servergezijdige herhaalde bevraging van `externalLink.durableUrl` aan;
  /// de eerder getoonde preview-data wordt hiervoor nooit vertrouwd.
  final bool? confirmExternalArchiveData;

  Map<String, dynamic> toJson() => {
    'localIdentifier': localIdentifier,
    'title': title,
    'description': description,
    'dating': dating,
    'provenance': provenance,
    'rightsStatus': rightsStatus,
    'privacyClassification': privacyClassification,
    'accessUrl': accessUrl,
    if (externalLink != null) 'externalLink': externalLink!.toJson(),
    if (deceasedStatus != null) 'deceasedStatus': deceasedStatus,
    if (nextOfKinConfirmed != null) 'nextOfKinConfirmed': nextOfKinConfirmed,
    if (confirmExternalArchiveData != null)
      'confirmExternalArchiveData': confirmExternalArchiveData,
  };
}

class RecordIntakeCreationResult {
  const RecordIntakeCreationResult({
    required this.status,
    required this.externalLinkCreated,
    this.externalArchiveDataStored = false,
    this.externalArchiveDataReason,
  });

  final String status;
  final bool externalLinkCreated;

  /// Alleen `true` wanneer naam, geboortedatum en sterftedatum daadwerkelijk opgeslagen zijn.
  final bool externalArchiveDataStored;

  /// Leesbare toelichting van de dubbele fail-closed classificatie-uitkomst, aanwezig zodra de
  /// server een externe-archiefdatabeoordeling heeft uitgevoerd.
  final String? externalArchiveDataReason;
}

/// Machineleesbare statuslabels van het niet-persisterende previewendpoint, gelijk aan
/// `RecordIntakeExternalArchivePreviewStatus` in de backend.
class RecordIntakeExternalArchivePreviewStatus {
  const RecordIntakeExternalArchivePreviewStatus._();

  static const String verified = 'GEVERIFIEERD';
  static const String noMatch = 'GEEN_MATCH';
  static const String unreachable = 'NIET_BEREIKBAAR';
}

/// Niet-persisterende preview van de externe archiefbron voor een `durableUrl`.
class RecordIntakeExternalArchivePreviewResult {
  const RecordIntakeExternalArchivePreviewResult({
    required this.status,
    this.name,
    this.birthDate,
    this.deathDate,
    this.license,
    this.sourceUri,
  });

  final String status;
  final String? name;
  final String? birthDate;
  final String? deathDate;
  final String? license;
  final String? sourceUri;
}

/// Machineleesbare veldfouten; geen conceptrecord is aangemaakt.
class RecordIntakeFieldErrors implements Exception {
  const RecordIntakeFieldErrors(this.fieldErrors);

  final List<String> fieldErrors;
}

/// Fail-closed weigering wegens de privacyregel; geen conceptrecord is aangemaakt.
class RecordIntakePrivacyBlocked implements Exception {
  const RecordIntakePrivacyBlocked();
}

abstract interface class AdminRecordIntakeSource {
  Future<RecordIntakeCreationResult> create({
    required AdminIdentity identity,
    required RecordIntakeInput input,
  });

  /// Bevraagt het niet-persisterende previewendpoint voor een `durableUrl`, zonder autorisatie.
  Future<RecordIntakeExternalArchivePreviewResult> previewExternalArchiveData({
    required String durableUrl,
  });
}

class AdminRecordIntakeClient implements AdminRecordIntakeSource {
  AdminRecordIntakeClient(this.apiBaseUrl, {http.Client? client})
    : _client = client ?? http.Client();

  final String apiBaseUrl;
  final http.Client _client;

  @override
  Future<RecordIntakeCreationResult> create({
    required AdminIdentity identity,
    required RecordIntakeInput input,
  }) async {
    final response = await _client
        .post(
          Uri.parse('$apiBaseUrl/api/record-intake'),
          headers: {
            'Content-Type': 'application/json',
            ...identity.requestHeaders,
          },
          body: jsonEncode(input.toJson()),
        )
        .timeout(const Duration(seconds: 10));

    if (response.statusCode == 201) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final externalArchiveData =
          json['externalArchiveData'] as Map<String, dynamic>?;
      return RecordIntakeCreationResult(
        status: json['status'] as String,
        externalLinkCreated: json['externalLink'] != null,
        externalArchiveDataStored:
            externalArchiveData?['stored'] as bool? ?? false,
        externalArchiveDataReason: externalArchiveData?['reason'] as String?,
      );
    }
    if (response.statusCode == 400) {
      final json = jsonDecode(response.body) as Map<String, dynamic>;
      final fieldErrors = (json['fieldErrors'] as List<dynamic>? ?? const [])
          .cast<String>();
      throw RecordIntakeFieldErrors(fieldErrors);
    }
    if (response.statusCode == 422) {
      throw const RecordIntakePrivacyBlocked();
    }
    throw StateError('Record-intake geweigerd (${response.statusCode}).');
  }

  @override
  Future<RecordIntakeExternalArchivePreviewResult> previewExternalArchiveData({
    required String durableUrl,
  }) async {
    final response = await _client
        .post(
          Uri.parse('$apiBaseUrl/api/record-intake/external-archive-preview'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({'durableUrl': durableUrl}),
        )
        .timeout(const Duration(seconds: 10));

    if (response.statusCode != 200) {
      return const RecordIntakeExternalArchivePreviewResult(
        status: RecordIntakeExternalArchivePreviewStatus.unreachable,
      );
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    return RecordIntakeExternalArchivePreviewResult(
      status: json['status'] as String,
      name: json['name'] as String?,
      birthDate: json['birthDate'] as String?,
      deathDate: json['deathDate'] as String?,
      license: json['license'] as String?,
      sourceUri: json['sourceUri'] as String?,
    );
  }
}
