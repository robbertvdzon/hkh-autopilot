import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/topicsearch/topic_search_client.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('posts the topic search term and parses a READY response', () async {
    late Uri requestedUri;
    late String requestedBody;
    final mockClient = MockClient((request) async {
      requestedUri = request.url;
      requestedBody = request.body;
      return http.Response(
        jsonEncode({
          'status': 'READY',
          'topicSearchTerm': 'watersnood van 1916',
          'answer': {
            'topicSearchTerm': 'watersnood van 1916',
            'records': [
              {
                'title': 'Watersnood van 1916',
                'dataProvider': 'Noord-Hollands Archief',
                'licenseText': 'Publiek domein',
                'licenseUrl':
                    'https://creativecommons.org/publicdomain/mark/1.0/',
                'sourceUrl': 'https://archief.example/1',
              },
            ],
            'context': {
              'label': 'Watersnood van 1916',
              'description': 'overstroming',
            },
            'checkedAt': '2026-09-10T10:00:00Z',
          },
          'refinementSuggestions': [],
        }),
        200,
      );
    });
    final client = TopicSearchClient(
      'https://example.test',
      client: mockClient,
    );

    final result = await client.search(topicSearchTerm: 'watersnood van 1916');

    assertRequest(requestedUri, requestedBody);
    expect(result.answer?.records, hasLength(1));
    expect(result.answer?.records.first.license.text, 'Publiek domein');
    expect(result.answer?.context?.label, 'Watersnood van 1916');
  });

  test('parses an EMPTY response with refinement suggestions', () async {
    final mockClient = MockClient((request) async {
      return http.Response(
        jsonEncode({
          'status': 'EMPTY',
          'topicSearchTerm': 'onbekend onderwerp',
          'refinementSuggestions': ['Gebruik een breder trefwoord.'],
        }),
        200,
      );
    });
    final client = TopicSearchClient(
      'https://example.test',
      client: mockClient,
    );

    final result = await client.search(topicSearchTerm: 'onbekend onderwerp');

    expect(result.answer, isNull);
    expect(result.refinementSuggestions, ['Gebruik een breder trefwoord.']);
  });

  test('parses an OUTAGE response without an answer', () async {
    final mockClient = MockClient((request) async {
      return http.Response(
        jsonEncode({
          'status': 'OUTAGE',
          'topicSearchTerm': 'watersnood van 1916',
        }),
        200,
      );
    });
    final client = TopicSearchClient(
      'https://example.test',
      client: mockClient,
    );

    final result = await client.search(topicSearchTerm: 'watersnood van 1916');

    expect(result.answer, isNull);
  });

  test('throws TopicSearchSubmitException on a non-200 response', () async {
    final mockClient = MockClient((request) async {
      return http.Response('boom', 500);
    });
    final client = TopicSearchClient(
      'https://example.test',
      client: mockClient,
    );

    expect(
      () => client.search(topicSearchTerm: 'watersnood van 1916'),
      throwsA(isA<TopicSearchSubmitException>()),
    );
  });
}

void assertRequest(Uri requestedUri, String requestedBody) {
  expect(requestedUri.toString(), 'https://example.test/api/topic-search');
  expect(jsonDecode(requestedBody), {'topicSearchTerm': 'watersnood van 1916'});
}
