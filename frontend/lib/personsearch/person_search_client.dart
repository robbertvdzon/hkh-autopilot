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
}
