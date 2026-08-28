import 'dart:convert';

import 'package:http/http.dart' as http;

import 'person_search_models.dart';

/// Gecontroleerde fout bij een mislukte indiening (netwerkfout, timeout of
/// onverwachte respons). De aanroeper toont dan `source-outage`.
class PersonSearchSubmitException implements Exception {
  const PersonSearchSubmitException(this.message);

  final String message;

  @override
  String toString() => 'PersonSearchSubmitException: $message';
}

/// Gecontroleerde fout bij een statusaanvraag/stopactie/openactie
/// (netwerkfout, timeout of onverwachte respons, maar geen 404).
class PersonSearchStatusException implements Exception {
  const PersonSearchStatusException(this.message);

  final String message;

  @override
  String toString() => 'PersonSearchStatusException: $message';
}

/// De job bestaat niet (meer) binnen deze sessie: onbekend, van een andere
/// sessie, of opgeschoond (gestopt/verlopen). De aanroeper toont dan dat het
/// antwoord niet meer beschikbaar is, met een aanbod om opnieuw in te dienen.
class PersonSearchJobUnavailableException implements Exception {
  const PersonSearchJobUnavailableException();

  @override
  String toString() => 'PersonSearchJobUnavailableException';
}

/// Injecteerbare/mockbare bron voor een persoonszoekopdracht, zodat
/// widgettests nooit een echte backend-aanroep hoeven te doen.
abstract interface class PersonSearchSource {
  Future<PersonSearchResult> submit({
    required String recognizedName,
    String? secondName,
    String? eventType,
    String? yearOrPeriod,
    String? heemskerkMeaningQid,
    required String originalQuery,
  });

  /// `GET /api/person-search/{jobId}/status`. Gooit
  /// [PersonSearchJobUnavailableException] wanneer de job niet (meer)
  /// bestaat binnen deze sessie.
  Future<PersonSearchStatusResult> pollStatus(String jobId);

  /// `POST /api/person-search/{jobId}/cancel`.
  Future<PersonSearchStatusResult> cancel(String jobId);

  /// `POST /api/person-search/{jobId}/open`.
  Future<PersonSearchStatusResult> open(String jobId);

  /// `GET /api/person-search/session`.
  Future<PersonSearchSessionIndicator> sessionIndicator();
}

/// Dient een persoonszoekopdracht in bij `POST /api/person-search`. De
/// route-gebonden sessiecookie wordt door de browser automatisch meegestuurd
/// bij een gelijke-origin `apiBaseUrl`; dit is de aanname van deze story (geen
/// login, geen cross-origin credential-plumbing nodig).
class PersonSearchClient implements PersonSearchSource {
  PersonSearchClient(
    this.apiBaseUrl, {
    http.Client? client,
    this.timeout = const Duration(seconds: 10),
  }) : _client = client ?? http.Client();

  final String apiBaseUrl;
  final http.Client _client;
  final Duration timeout;

  @override
  Future<PersonSearchResult> submit({
    required String recognizedName,
    String? secondName,
    String? eventType,
    String? yearOrPeriod,
    String? heemskerkMeaningQid,
    required String originalQuery,
  }) async {
    try {
      final response = await _client
          .post(
            Uri.parse('$apiBaseUrl/api/person-search'),
            headers: const {'Content-Type': 'application/json'},
            body: jsonEncode({
              'recognizedName': recognizedName,
              if (secondName != null) 'secondName': secondName,
              if (eventType != null) 'eventType': eventType,
              if (yearOrPeriod != null) 'yearOrPeriod': yearOrPeriod,
              if (heemskerkMeaningQid != null)
                'heemskerkMeaningQid': heemskerkMeaningQid,
              'originalQuery': originalQuery,
            }),
          )
          .timeout(timeout);

      if (response.statusCode != 200) {
        throw PersonSearchSubmitException(
          'Onverwachte respons (${response.statusCode}) bij het indienen van de vraag.',
        );
      }
      return PersonSearchResult.fromJson(
        jsonDecode(response.body) as Map<String, dynamic>,
      );
    } on PersonSearchSubmitException {
      rethrow;
    } catch (error) {
      throw PersonSearchSubmitException(error.toString());
    }
  }

  @override
  Future<PersonSearchStatusResult> pollStatus(String jobId) async {
    final response = await _getWithCredentials(
      '/api/person-search/$jobId/status',
    );
    return PersonSearchStatusResult.fromJson(
      jsonDecode(response.body) as Map<String, dynamic>,
    );
  }

  @override
  Future<PersonSearchStatusResult> cancel(String jobId) async {
    final response = await _postWithCredentials(
      '/api/person-search/$jobId/cancel',
    );
    return PersonSearchStatusResult.fromJson(
      jsonDecode(response.body) as Map<String, dynamic>,
    );
  }

  @override
  Future<PersonSearchStatusResult> open(String jobId) async {
    final response = await _postWithCredentials(
      '/api/person-search/$jobId/open',
    );
    return PersonSearchStatusResult.fromJson(
      jsonDecode(response.body) as Map<String, dynamic>,
    );
  }

  @override
  Future<PersonSearchSessionIndicator> sessionIndicator() async {
    final response = await _getWithCredentials('/api/person-search/session');
    return PersonSearchSessionIndicator.fromJson(
      jsonDecode(response.body) as Map<String, dynamic>,
    );
  }

  Future<http.Response> _getWithCredentials(String path) async {
    try {
      final response = await _client
          .get(Uri.parse('$apiBaseUrl$path'))
          .timeout(timeout);
      return _checkStatusResponse(response);
    } on PersonSearchJobUnavailableException {
      rethrow;
    } on PersonSearchStatusException {
      rethrow;
    } catch (error) {
      throw PersonSearchStatusException(error.toString());
    }
  }

  Future<http.Response> _postWithCredentials(String path) async {
    try {
      final response = await _client
          .post(Uri.parse('$apiBaseUrl$path'))
          .timeout(timeout);
      return _checkStatusResponse(response);
    } on PersonSearchJobUnavailableException {
      rethrow;
    } on PersonSearchStatusException {
      rethrow;
    } catch (error) {
      throw PersonSearchStatusException(error.toString());
    }
  }

  http.Response _checkStatusResponse(http.Response response) {
    if (response.statusCode == 404) {
      throw const PersonSearchJobUnavailableException();
    }
    if (response.statusCode != 200) {
      throw PersonSearchStatusException(
        'Onverwachte respons (${response.statusCode}) bij de statusaanvraag.',
      );
    }
    return response;
  }
}
