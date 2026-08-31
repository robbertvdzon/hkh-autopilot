import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/placesearch/place_search_client.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('posts the candidate term and parses a READY response', () async {
    late Uri requestedUri;
    late String requestedBody;
    final mockClient = MockClient((request) async {
      requestedUri = request.url;
      requestedBody = request.body;
      return http.Response(
        jsonEncode({
          'status': 'READY',
          'candidateTerm': 'Kasteel Assumburg',
          'answer': {
            'qid': 'Q1968571',
            'label': 'Kasteel Assumburg',
            'description': 'kasteel in Heemskerk',
            'sentences': [
              {
                'text': 'Kasteel Assumburg is een kasteel.',
                'sourceNumbers': [1],
              },
            ],
            'contextSentence': {
              'text': 'Kasteel Assumburg ligt in de gemeente Heemskerk.',
              'sourceNumbers': [2],
            },
            'sources': [
              {
                'number': 1,
                'qid': 'Q1968571',
                'wikidataLink': 'https://www.wikidata.org/wiki/Q1968571',
                'checkedAt': '2026-08-31T10:00:00Z',
              },
              {
                'number': 2,
                'qid': 'Q1968571',
                'wikidataLink': 'https://www.wikidata.org/wiki/Q1968571',
                'checkedAt': '2026-08-31T10:00:00Z',
              },
            ],
            'images': [
              {
                'url': 'https://upload.wikimedia.org/assumburg.jpg',
                'fileName': 'Assumburg.jpg',
                'license': 'CC BY-SA 4.0',
                'filePageUrl':
                    'https://commons.wikimedia.org/wiki/File:Assumburg.jpg',
              },
            ],
            'commonsOutage': false,
            'disclaimer': 'Dit is een actuele beschrijving.',
            'checkedAt': '2026-08-31T10:00:00Z',
          },
          'refinementCandidates': [],
        }),
        200,
      );
    });
    final client = PlaceSearchClient(
      'https://example.test',
      client: mockClient,
    );

    final result = await client.search(candidateTerm: 'Kasteel Assumburg');

    expect(requestedUri.toString(), 'https://example.test/api/place-search');
    expect(jsonDecode(requestedBody), {'candidateTerm': 'Kasteel Assumburg'});
    expect(result.answer?.qid, 'Q1968571');
    expect(result.answer?.sources, hasLength(2));
    expect(result.answer?.images, hasLength(1));
    expect(result.answer?.images.first.license, 'CC BY-SA 4.0');
  });

  test('parses a NO_MATCH response with refinement candidates', () async {
    final mockClient = MockClient((request) async {
      return http.Response(
        jsonEncode({
          'status': 'NO_MATCH',
          'candidateTerm': 'Kasteel',
          'refinementCandidates': [
            {'qid': 'Q1', 'label': 'Kasteel A'},
            {'qid': 'Q2', 'label': 'Kasteel B'},
          ],
        }),
        200,
      );
    });
    final client = PlaceSearchClient(
      'https://example.test',
      client: mockClient,
    );

    final result = await client.search(candidateTerm: 'Kasteel');

    expect(result.answer, isNull);
    expect(result.refinementCandidates.map((c) => c.label), [
      'Kasteel A',
      'Kasteel B',
    ]);
  });

  test('parses an OUTAGE response without an answer', () async {
    final mockClient = MockClient((request) async {
      return http.Response(
        jsonEncode({'status': 'OUTAGE', 'candidateTerm': 'Kasteel Assumburg'}),
        200,
      );
    });
    final client = PlaceSearchClient(
      'https://example.test',
      client: mockClient,
    );

    final result = await client.search(candidateTerm: 'Kasteel Assumburg');

    expect(result.answer, isNull);
  });

  test('throws PlaceSearchSubmitException on a non-200 response', () async {
    final mockClient = MockClient((request) async {
      return http.Response('boom', 500);
    });
    final client = PlaceSearchClient(
      'https://example.test',
      client: mockClient,
    );

    expect(
      () => client.search(candidateTerm: 'Kasteel Assumburg'),
      throwsA(isA<PlaceSearchSubmitException>()),
    );
  });
}
