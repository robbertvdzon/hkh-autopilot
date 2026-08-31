import 'dart:convert';

import 'package:http/http.dart' as http;

import 'place_search_models.dart';

/// Gecontroleerde fout bij een mislukte indiening (netwerkfout, timeout of
/// onverwachte respons). De aanroeper toont dan `place-outage`.
class PlaceSearchSubmitException implements Exception {
  const PlaceSearchSubmitException(this.message);

  final String message;

  @override
  String toString() => 'PlaceSearchSubmitException: $message';
}

/// Injecteerbare/mockbare bron voor een plek/gebouw-zoekopdracht, zodat
/// widgettests nooit een echte backend-aanroep hoeven te doen.
abstract interface class PlaceSearchSource {
  /// `POST /api/place-search`. Volledig synchroon: het antwoord (of
  /// `NO_MATCH`/`OUTAGE`) komt terug binnen dezelfde aanroep, zonder
  /// statuspolling.
  Future<PlaceSearchResult> search({required String candidateTerm});
}

/// Dient een plek/gebouw-zoekopdracht in bij `POST /api/place-search`.
class PlaceSearchClient implements PlaceSearchSource {
  PlaceSearchClient(
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
  Future<PlaceSearchResult> search({required String candidateTerm}) async {
    try {
      final response = await _client
          .post(
            Uri.parse('$apiBaseUrl/api/place-search'),
            headers: const {'Content-Type': 'application/json'},
            body: jsonEncode({'candidateTerm': candidateTerm}),
          )
          .timeout(timeout);

      if (response.statusCode != 200) {
        throw PlaceSearchSubmitException(
          'Onverwachte respons (${response.statusCode}) bij de plek/gebouw-zoekopdracht.',
        );
      }
      return PlaceSearchResult.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    } on PlaceSearchSubmitException {
      rethrow;
    } catch (error) {
      throw PlaceSearchSubmitException(error.toString());
    }
  }
}
