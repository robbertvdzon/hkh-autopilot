import 'dart:convert';

import 'package:http/http.dart' as http;

/// De vaste Wikidata-QID's voor de twee betekenissen van "Heemskerk" die in
/// deze story onderscheiden worden. Er wordt bewust nooit resultaat van beide
/// betekenissen samengevoegd.
class WikidataMeaningIds {
  static const place = 'Q9926';
  static const surname = 'Q91564725';
}

/// Eén betekenis-optie met het actuele NL-label en de beschrijving, zoals
/// live opgehaald bij Wikidata, of de vaste fallback wanneer dat mislukt.
class WikidataMeaningOption {
  const WikidataMeaningOption({
    required this.qid,
    required this.label,
    required this.description,
  });

  final String qid;
  final String label;
  final String description;
}

class WikidataMeaningResult {
  const WikidataMeaningResult({required this.place, required this.surname});

  final WikidataMeaningOption place;
  final WikidataMeaningOption surname;
}

/// Gecontroleerde fout bij een mislukte live Wikidata-oproep (netwerkfout,
/// time-out of ongeldige respons). De aanroeper toont dan de statische
/// fallback-labels plus een zichtbare storingsmelding.
class WikidataMeaningException implements Exception {
  const WikidataMeaningException(this.message);

  final String message;

  @override
  String toString() => 'WikidataMeaningException: $message';
}

/// Injecteerbare/mockbare bron voor de twee Heemskerk-betekenissen, zodat
/// widgettests nooit een echte Wikidata-aanroep hoeven te doen.
abstract interface class WikidataMeaningSource {
  Future<WikidataMeaningResult> fetchMeanings();
}

/// Haalt bij tonen van het meaning-selection-scherm live
/// `wbsearchentities` op (search=Heemskerk, language=nl, type=item,
/// format=json), gevolgd door `Special:EntityData/<qid>.json` voor beide
/// vaste QID's, voor het actuele NL-label en de beschrijving per optie.
class WikidataMeaningClient implements WikidataMeaningSource {
  WikidataMeaningClient({
    http.Client? client,
    Uri? baseUri,
    this.timeout = const Duration(seconds: 10),
  }) : _client = client ?? http.Client(),
       _baseUri = baseUri ?? Uri.parse('https://www.wikidata.org');

  final http.Client _client;
  final Uri _baseUri;
  final Duration timeout;

  @override
  Future<WikidataMeaningResult> fetchMeanings() async {
    try {
      final searchUri = _baseUri.replace(
        path: '/w/api.php',
        queryParameters: {
          'action': 'wbsearchentities',
          'search': 'Heemskerk',
          'language': 'nl',
          'type': 'item',
          'format': 'json',
        },
      );
      final searchResponse = await _client.get(searchUri).timeout(timeout);
      if (searchResponse.statusCode != 200) {
        throw const WikidataMeaningException(
          'wbsearchentities gaf geen geldige respons.',
        );
      }
      // Verifieert dat de respons parsebaar JSON is; de inhoud is voor deze
      // story niet verder nodig omdat de twee QID's al vastliggen.
      jsonDecode(searchResponse.body);

      final place = await _fetchEntity(
        WikidataMeaningIds.place,
        'Heemskerk (plaats)',
      );
      final surname = await _fetchEntity(
        WikidataMeaningIds.surname,
        'Heemskerk (achternaam)',
      );
      return WikidataMeaningResult(place: place, surname: surname);
    } on WikidataMeaningException {
      rethrow;
    } catch (error) {
      throw WikidataMeaningException(error.toString());
    }
  }

  Future<WikidataMeaningOption> _fetchEntity(
    String qid,
    String fallbackSuffix,
  ) async {
    final uri = _baseUri.replace(path: '/wiki/Special:EntityData/$qid.json');
    final response = await _client.get(uri).timeout(timeout);
    if (response.statusCode != 200) {
      throw WikidataMeaningException('EntityData mislukt voor $qid.');
    }
    final json = jsonDecode(response.body) as Map<String, dynamic>;
    final entities = json['entities'] as Map<String, dynamic>;
    final entity = entities[qid] as Map<String, dynamic>;
    final labels = entity['labels'] as Map<String, dynamic>?;
    final descriptions = entity['descriptions'] as Map<String, dynamic>?;
    final nlLabel =
        (labels?['nl'] as Map<String, dynamic>?)?['value'] as String? ??
        fallbackSuffix;
    final nlDescription =
        (descriptions?['nl'] as Map<String, dynamic>?)?['value'] as String? ??
        '';
    return WikidataMeaningOption(
      qid: qid,
      label: '$qid · $nlLabel',
      description: nlDescription,
    );
  }
}
