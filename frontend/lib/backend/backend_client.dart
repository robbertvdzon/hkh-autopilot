import 'dart:convert';

import 'package:http/http.dart' as http;

import 'backend_status.dart';
import '../news/latest_news.dart';
import '../records/record_public_view.dart';

class BackendClient
    implements BackendStatusSource, LatestNewsSource, RecordPublicSource {
  BackendClient(this.apiBaseUrl, {http.Client? client})
    : _client = client ?? http.Client();

  final String apiBaseUrl;
  final http.Client _client;

  @override
  Future<BackendStatus> load() async {
    final responses = await Future.wait([
      _client.get(Uri.parse('$apiBaseUrl/actuator/health')),
      _client.get(Uri.parse('$apiBaseUrl/api/version')),
    ]).timeout(const Duration(seconds: 10));

    if (responses[0].statusCode != 200 || responses[1].statusCode != 200) {
      throw StateError('HKH backend is unavailable.');
    }

    final health = jsonDecode(responses[0].body) as Map<String, dynamic>;
    if (health['status'] != 'UP') throw StateError('HKH backend is unhealthy.');
    return BackendStatus.fromJson(
      jsonDecode(responses[1].body) as Map<String, dynamic>,
    );
  }

  @override
  Future<NewsSearchResult> loadLatestNews({String? q, String? entity}) async {
    final queryParameters = <String, String>{
      if (q != null && q.isNotEmpty) 'q': q,
      if (entity != null && entity.isNotEmpty) 'entity': entity,
    };
    final uri = Uri.parse('$apiBaseUrl/api/news').replace(
      queryParameters: queryParameters.isEmpty ? null : queryParameters,
    );
    final response = await _client
        .get(uri)
        .timeout(const Duration(seconds: 10));
    if (response.statusCode != 200) {
      throw StateError('Het laatste nieuws kon niet worden geladen.');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    return NewsSearchResult.fromJson(json);
  }

  @override
  Future<RecordPublicView> loadRecord(String localIdentifier) async {
    final uri = Uri.parse('$apiBaseUrl/api/records/$localIdentifier');
    final response = await _client
        .get(uri)
        .timeout(const Duration(seconds: 10));
    if (response.statusCode != 200) {
      throw StateError('De recordgegevens konden niet worden geladen.');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    return RecordPublicView.fromJson(json);
  }
}
