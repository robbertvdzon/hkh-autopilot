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
  };
}

class RecordIntakeCreationResult {
  const RecordIntakeCreationResult({
    required this.status,
    required this.externalLinkCreated,
  });

  final String status;
  final bool externalLinkCreated;
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
      return RecordIntakeCreationResult(
        status: json['status'] as String,
        externalLinkCreated: json['externalLink'] != null,
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
}
