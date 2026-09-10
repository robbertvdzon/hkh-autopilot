import 'dart:convert';

import 'package:http/http.dart' as http;

import 'topic_search_models.dart';

/// Gecontroleerde fout bij een mislukte indiening (netwerkfout, timeout of
/// onverwachte respons). De aanroeper toont dan `topic-outage`.
class TopicSearchSubmitException implements Exception {
  const TopicSearchSubmitException(this.message);

  final String message;

  @override
  String toString() => 'TopicSearchSubmitException: $message';
}

/// Injecteerbare/mockbare bron voor een onderwerp/voorwerp/gebeurtenis-
/// zoekopdracht, zodat widgettests nooit een echte backend-aanroep hoeven te
/// doen.
abstract interface class TopicSearchSource {
  /// `POST /api/topic-search`. Volledig synchroon: het antwoord (of
  /// `EMPTY`/`OUTAGE`) komt terug binnen dezelfde aanroep, zonder
  /// statuspolling.
  Future<TopicSearchResult> search({required String topicSearchTerm});
}

/// Dient een onderwerp/voorwerp/gebeurtenis-zoekopdracht in bij
/// `POST /api/topic-search`.
class TopicSearchClient implements TopicSearchSource {
  TopicSearchClient(
    this.apiBaseUrl, {
    http.Client? client,
    this.timeout = const Duration(seconds: 3),
  }) : _client = client ?? http.Client();

  final String apiBaseUrl;
  final http.Client _client;

  /// Ruim boven het backend-budget van 2000ms, zodat de backend altijd zelf
  /// als eerste een terminale uitkomst (incl. `OUTAGE`) teruggeeft.
  final Duration timeout;

  @override
  Future<TopicSearchResult> search({required String topicSearchTerm}) async {
    try {
      final response = await _client
          .post(
            Uri.parse('$apiBaseUrl/api/topic-search'),
            headers: const {'Content-Type': 'application/json'},
            body: jsonEncode({'topicSearchTerm': topicSearchTerm}),
          )
          .timeout(timeout);

      if (response.statusCode != 200) {
        throw TopicSearchSubmitException(
          'Onverwachte respons (${response.statusCode}) bij de onderwerp-zoekopdracht.',
        );
      }
      return TopicSearchResult.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    } on TopicSearchSubmitException {
      rethrow;
    } catch (error) {
      throw TopicSearchSubmitException(error.toString());
    }
  }
}
