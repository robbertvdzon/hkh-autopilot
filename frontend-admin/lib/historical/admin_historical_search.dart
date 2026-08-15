import 'dart:convert';

import 'package:http/http.dart' as http;

import '../auth/admin_session.dart';

class AdminHistoricalStatus {
  const AdminHistoricalStatus({required this.status, required this.reason});

  final String status;
  final String reason;

  bool get isConfirmed => status == 'CONFIRMED';
}

class AdminHistoricalSearchResult {
  const AdminHistoricalSearchResult({
    required this.source,
    required this.sourceName,
    required this.stableIdentifier,
    required this.originalSourceUrl,
    required this.technicalStatus,
    required this.sourceVerification,
    required this.metadataRights,
    required this.privacy,
    required this.publicRelease,
    required this.objectMediaRights,
  });

  final String source;
  final String? sourceName;
  final String? stableIdentifier;
  final String? originalSourceUrl;
  final String technicalStatus;
  final AdminHistoricalStatus sourceVerification;
  final AdminHistoricalStatus metadataRights;
  final AdminHistoricalStatus privacy;
  final AdminHistoricalStatus publicRelease;
  final AdminHistoricalStatus objectMediaRights;

  factory AdminHistoricalSearchResult.fromJson(Map<String, dynamic> json) {
    AdminHistoricalStatus status(String key, String reasonKey) =>
        AdminHistoricalStatus(
          status: json[key] as String? ?? 'UNKNOWN',
          reason: json[reasonKey] as String? ?? 'Status niet beschikbaar.',
        );

    return AdminHistoricalSearchResult(
      source: json['source'] as String? ?? 'UNKNOWN',
      sourceName: json['source_name'] as String?,
      stableIdentifier: json['stable_identifier'] as String?,
      originalSourceUrl: json['original_source_url'] as String?,
      technicalStatus: json['technicalStatus'] as String? ?? 'UNKNOWN',
      sourceVerification: status(
        'sourceVerificationStatus',
        'sourceVerificationReason',
      ),
      metadataRights: status('metadataRightsStatus', 'metadataRightsReason'),
      privacy: status('privacyStatus', 'privacyReason'),
      publicRelease: status('publicReleaseStatus', 'publicReleaseReason'),
      objectMediaRights: status(
        'objectMediaRightsStatus',
        'objectMediaRightsReason',
      ),
    );
  }
}

class AdminHistoricalSearchResultPage {
  const AdminHistoricalSearchResultPage({
    required this.results,
    required this.total,
    required this.start,
    required this.limit,
    required this.state,
  });

  final List<AdminHistoricalSearchResult> results;
  final int total;
  final int start;
  final int limit;
  final String state;

  factory AdminHistoricalSearchResultPage.fromJson(Map<String, dynamic> json) {
    return AdminHistoricalSearchResultPage(
      results: (json['results'] as List<dynamic>? ?? const [])
          .map(
            (item) => AdminHistoricalSearchResult.fromJson(
              item as Map<String, dynamic>,
            ),
          )
          .toList(growable: false),
      total: json['total'] as int? ?? 0,
      start: json['start'] as int? ?? 0,
      limit: json['limit'] as int? ?? 100,
      state: json['state'] as String? ?? 'SOURCE_FAILURE',
    );
  }
}

abstract interface class AdminHistoricalSearchSource {
  Future<AdminHistoricalSearchResultPage> search({
    required AdminIdentity identity,
    required String query,
  });
}

class AdminHistoricalSearchClient implements AdminHistoricalSearchSource {
  AdminHistoricalSearchClient(this.apiBaseUrl, {http.Client? client})
    : _client = client ?? http.Client();

  final String apiBaseUrl;
  final http.Client _client;

  @override
  Future<AdminHistoricalSearchResultPage> search({
    required AdminIdentity identity,
    required String query,
  }) async {
    final uri = Uri.parse('$apiBaseUrl/api/admin/historical-search').replace(
      queryParameters: query.trim().isEmpty ? null : {'q': query.trim()},
    );
    final response = await _client
        .get(uri, headers: identity.requestHeaders)
        .timeout(const Duration(seconds: 10));
    if (response.statusCode != 200) {
      throw StateError(
        'Historische zoekresultaten konden niet worden geladen (${response.statusCode}).',
      );
    }
    return AdminHistoricalSearchResultPage.fromJson(
      jsonDecode(response.body) as Map<String, dynamic>,
    );
  }
}
