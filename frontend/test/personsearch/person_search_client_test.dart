import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/personsearch/person_search_client.dart';
import 'package:hkh_app/personsearch/person_search_models.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('posts the recognized name and optional fields as json', () async {
    http.Request? capturedRequest;
    final mockClient = MockClient((request) async {
      capturedRequest = request;
      return http.Response(
        jsonEncode({
          'jobId': 'job-1',
          'status': 'NO_EVIDENCE',
          'originalQuery': 'Wie was Jan Jansen?',
        }),
        200,
      );
    });

    final client = PersonSearchClient(
      'https://backend.example',
      client: mockClient,
    );
    final result = await client.submit(
      recognizedName: 'Jan Jansen',
      yearOrPeriod: '1900',
      originalQuery: 'Wie was Jan Jansen?',
    );

    expect(
      capturedRequest!.url.toString(),
      'https://backend.example/api/person-search',
    );
    final body = jsonDecode(capturedRequest!.body) as Map<String, dynamic>;
    expect(body['recognizedName'], 'Jan Jansen');
    expect(body['yearOrPeriod'], '1900');
    expect(body.containsKey('secondName'), isFalse);
    expect(result.status, PersonSearchStatus.noEvidence);
    expect(result.jobId, 'job-1');
  });

  test('parses a full supported-answer response', () async {
    final mockClient = MockClient((request) async {
      return http.Response(
        jsonEncode({
          'jobId': 'job-2',
          'status': 'READY',
          'originalQuery': 'Wie was Nicolaas Jacobus Sinnige?',
          'answer': {
            'sentences': [
              {
                'text':
                    'Nicolaas Jacobus Sinnige is geboren op 25 juli 1878 in Heemskerk.',
                'sourceNumbers': [1],
              },
            ],
            'sources': [
              {
                'number': 1,
                'institution': 'Noord-Hollands Archief',
                'sourceType': 'Geboorteakte',
                'archiveCode': 'nha',
                'identifier': '002ED0F3-F08C-4223-A5EA-BA385D04336E',
                'recordNumber': '789',
                'openArchivesLink':
                    'https://www.openarchieven.nl/nha:002ED0F3-F08C-4223-A5EA-BA385D04336E',
                'checkedAt': '2026-08-28T10:00:00Z',
              },
            ],
            'connections': [
              {'role': 'Vader', 'personName': 'Pieter Sinnige'},
            ],
            'disclaimer': 'Geen volledig levensverhaal.',
          },
          'context': {'label': 'Heemskerk', 'description': 'gemeente'},
        }),
        200,
      );
    });

    final client = PersonSearchClient(
      'https://backend.example',
      client: mockClient,
    );
    final result = await client.submit(
      recognizedName: 'Nicolaas Jacobus Sinnige',
      originalQuery: 'Wie was Nicolaas Jacobus Sinnige?',
    );

    expect(result.status, PersonSearchStatus.ready);
    expect(result.answer!.sources.single.number, 1);
    expect(result.answer!.connections.single.personName, 'Pieter Sinnige');
    expect(result.context!.label, 'Heemskerk');
  });

  test('a non 200 response throws a submit exception', () async {
    final mockClient = MockClient(
      (request) async => http.Response('boom', 500),
    );
    final client = PersonSearchClient(
      'https://backend.example',
      client: mockClient,
    );

    expect(
      () => client.submit(recognizedName: 'X', originalQuery: 'X'),
      throwsA(isA<PersonSearchSubmitException>()),
    );
  });

  test('a network error is wrapped as a submit exception', () async {
    final mockClient = MockClient(
      (request) async => throw Exception('offline'),
    );
    final client = PersonSearchClient(
      'https://backend.example',
      client: mockClient,
    );

    expect(
      () => client.submit(recognizedName: 'X', originalQuery: 'X'),
      throwsA(isA<PersonSearchSubmitException>()),
    );
  });

  test('pollStatus parses a non-terminal status without an answer', () async {
    final mockClient = MockClient((request) async {
      expect(request.method, 'GET');
      expect(request.url.path, '/api/person-search/job-1/status');
      return http.Response(
        jsonEncode({
          'jobId': 'job-1',
          'status': 'RUNNING',
          'originalQuery': 'Wie was Jan Jansen?',
          'createdAt': '2026-08-28T10:00:00Z',
          'updatedAt': '2026-08-28T10:00:01Z',
          'openArchievenStatus': 'IN_PROGRESS',
          'wikidataStatus': 'NOT_STARTED',
        }),
        200,
      );
    });
    final client = PersonSearchClient(
      'https://backend.example',
      client: mockClient,
    );

    final result = await client.pollStatus('job-1');

    expect(result.status, PersonSearchStatus.running);
    expect(
      result.openArchievenStatus,
      PersonSearchSourceConsultationStatus.inProgress,
    );
    expect(
      result.wikidataStatus,
      PersonSearchSourceConsultationStatus.notStarted,
    );
    expect(result.answer, isNull);
  });

  test(
    'a status request for an unknown or foreign job throws PersonSearchJobUnavailableException',
    () async {
      final mockClient = MockClient((request) async => http.Response('', 404));
      final client = PersonSearchClient(
        'https://backend.example',
        client: mockClient,
      );

      expect(
        () => client.pollStatus('unknown-job'),
        throwsA(isA<PersonSearchJobUnavailableException>()),
      );
    },
  );

  test('cancel posts to the cancel endpoint and parses CANCELLED', () async {
    final mockClient = MockClient((request) async {
      expect(request.method, 'POST');
      expect(request.url.path, '/api/person-search/job-1/cancel');
      return http.Response(
        jsonEncode({
          'jobId': 'job-1',
          'status': 'CANCELLED',
          'originalQuery': 'Wie was Jan Jansen?',
          'createdAt': '2026-08-28T10:00:00Z',
          'updatedAt': '2026-08-28T10:00:02Z',
          'openArchievenStatus': 'FAILED',
          'wikidataStatus': 'NOT_STARTED',
        }),
        200,
      );
    });
    final client = PersonSearchClient(
      'https://backend.example',
      client: mockClient,
    );

    final result = await client.cancel('job-1');

    expect(result.status, PersonSearchStatus.cancelled);
  });

  test(
    'open posts to the open endpoint and parses the openedAt timestamp',
    () async {
      final mockClient = MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/person-search/job-1/open');
        return http.Response(
          jsonEncode({
            'jobId': 'job-1',
            'status': 'READY',
            'originalQuery': 'Wie was Jan Jansen?',
            'createdAt': '2026-08-28T10:00:00Z',
            'updatedAt': '2026-08-28T10:00:02Z',
            'openArchievenStatus': 'SUCCEEDED',
            'wikidataStatus': 'SUCCEEDED',
            'openedAt': '2026-08-28T10:00:03Z',
          }),
          200,
        );
      });
      final client = PersonSearchClient(
        'https://backend.example',
        client: mockClient,
      );

      final result = await client.open('job-1');

      expect(result.openedAt, DateTime.parse('2026-08-28T10:00:03Z'));
    },
  );

  test(
    'sessionIndicator parses the running and ready-unopened counts and job ids',
    () async {
      final mockClient = MockClient((request) async {
        expect(request.method, 'GET');
        expect(request.url.path, '/api/person-search/session');
        return http.Response(
          jsonEncode({
            'runningCount': 1,
            'readyUnopenedCount': 2,
            'runningJobIds': ['job-1'],
            'readyUnopenedJobIds': ['job-2', 'job-3'],
          }),
          200,
        );
      });
      final client = PersonSearchClient(
        'https://backend.example',
        client: mockClient,
      );

      final indicator = await client.sessionIndicator();

      expect(indicator.runningCount, 1);
      expect(indicator.readyUnopenedCount, 2);
      expect(indicator.runningJobIds, ['job-1']);
      expect(indicator.readyUnopenedJobIds, ['job-2', 'job-3']);
    },
  );
}
