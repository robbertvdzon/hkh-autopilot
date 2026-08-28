import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/personquery/wikidata_meaning_client.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

http.Response _entityResponse(String qid, String label, String description) {
  return http.Response(
    jsonEncode({
      'entities': {
        qid: {
          'labels': {
            'nl': {'language': 'nl', 'value': label},
          },
          'descriptions': {
            'nl': {'language': 'nl', 'value': description},
          },
        },
      },
    }),
    200,
  );
}

void main() {
  test(
    'haalt live labels en beschrijvingen op via wbsearchentities en EntityData',
    () async {
      final requestedPaths = <String>[];
      final client = MockClient((request) async {
        requestedPaths.add(request.url.path);
        if (request.url.path == '/w/api.php') {
          expect(request.url.queryParameters['action'], 'wbsearchentities');
          expect(request.url.queryParameters['search'], 'Heemskerk');
          expect(request.url.queryParameters['language'], 'nl');
          expect(request.url.queryParameters['type'], 'item');
          expect(request.url.queryParameters['format'], 'json');
          return http.Response(jsonEncode({'search': []}), 200);
        }
        if (request.url.path == '/wiki/Special:EntityData/Q9926.json') {
          return _entityResponse(
            'Q9926',
            'Heemskerk',
            'gemeente in Noord-Holland',
          );
        }
        if (request.url.path == '/wiki/Special:EntityData/Q91564725.json') {
          return _entityResponse('Q91564725', 'Heemskerk', 'achternaam');
        }
        return http.Response('not found', 404);
      });

      final wikidataClient = WikidataMeaningClient(client: client);
      final result = await wikidataClient.fetchMeanings();

      expect(requestedPaths, [
        '/w/api.php',
        '/wiki/Special:EntityData/Q9926.json',
        '/wiki/Special:EntityData/Q91564725.json',
      ]);
      expect(result.place.qid, WikidataMeaningIds.place);
      expect(result.place.label, 'Q9926 · Heemskerk');
      expect(result.place.description, 'gemeente in Noord-Holland');
      expect(result.surname.qid, WikidataMeaningIds.surname);
      expect(result.surname.label, 'Q91564725 · Heemskerk');
      expect(result.surname.description, 'achternaam');
    },
  );

  test(
    'gooit een gecontroleerde fout wanneer wbsearchentities mislukt',
    () async {
      final client = MockClient((request) async => http.Response('error', 500));
      final wikidataClient = WikidataMeaningClient(client: client);

      expect(
        () => wikidataClient.fetchMeanings(),
        throwsA(isA<WikidataMeaningException>()),
      );
    },
  );

  test(
    'gooit een gecontroleerde fout wanneer EntityData ongeldige JSON teruggeeft',
    () async {
      final client = MockClient((request) async {
        if (request.url.path == '/w/api.php') {
          return http.Response(jsonEncode({'search': []}), 200);
        }
        return http.Response('not json', 200);
      });
      final wikidataClient = WikidataMeaningClient(client: client);

      expect(
        () => wikidataClient.fetchMeanings(),
        throwsA(isA<WikidataMeaningException>()),
      );
    },
  );

  test('gooit een gecontroleerde fout bij een time-out', () async {
    final client = MockClient((request) async {
      await Future<void>.delayed(const Duration(milliseconds: 50));
      return http.Response(jsonEncode({'search': []}), 200);
    });
    final wikidataClient = WikidataMeaningClient(
      client: client,
      timeout: const Duration(milliseconds: 1),
    );

    expect(
      () => wikidataClient.fetchMeanings(),
      throwsA(isA<WikidataMeaningException>()),
    );
  });
}
