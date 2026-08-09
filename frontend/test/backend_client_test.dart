import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/backend/backend_client.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('loads and parses latest news with total and aggregated entities', () async {
    final client = BackendClient(
      'https://example.test',
      client: MockClient((request) async {
        expect(request.url.toString(), 'https://example.test/api/news');
        return http.Response(
          '{"items":[{"id":42,"title":"Dorpsnieuws","message":"Een verhaal",'
          '"publishedAt":"2026-08-07T09:00:00Z","createdAt":"2026-08-07T09:00:00Z",'
          '"entities":[{"type":"PLEK","label":"Kerkweg"}],'
          '"source":"Afkomstig uit gepubliceerd HKH-nieuwsbericht, gepubliceerd op 2026-08-07T09:00:00Z"}],'
          '"total":1,'
          '"entities":[{"type":"PLEK","label":"Kerkweg","itemCount":1}]}',
          200,
        );
      }),
    );

    final result = await client.loadLatestNews();

    expect(result.total, 1);
    expect(result.items, hasLength(1));
    expect(result.items.single.id, 42);
    expect(result.items.single.title, 'Dorpsnieuws');
    expect(result.items.single.message, 'Een verhaal');
    expect(result.items.single.publishedAt, DateTime.utc(2026, 8, 7, 9));
    expect(result.items.single.entities, hasLength(1));
    expect(result.items.single.entities.single.type, 'PLEK');
    expect(result.items.single.entities.single.label, 'Kerkweg');
    expect(result.items.single.source, isNotNull);
    expect(result.entities, hasLength(1));
    expect(result.entities.single.type, 'PLEK');
    expect(result.entities.single.label, 'Kerkweg');
    expect(result.entities.single.itemCount, 1);
  });

  test('sends q and entity as query parameters when provided', () async {
    final client = BackendClient(
      'https://example.test',
      client: MockClient((request) async {
        expect(request.url.queryParameters['q'], 'kerk');
        expect(request.url.queryParameters['entity'], 'Kerkweg');
        return http.Response('{"items":[],"total":0,"entities":[]}', 200);
      }),
    );

    final result = await client.loadLatestNews(q: 'kerk', entity: 'Kerkweg');

    expect(result.total, 0);
    expect(result.items, isEmpty);
  });

  test('omits query parameters when q and entity are absent', () async {
    final client = BackendClient(
      'https://example.test',
      client: MockClient((request) async {
        expect(request.url.query, isEmpty);
        return http.Response('{"items":[],"total":0,"entities":[]}', 200);
      }),
    );

    await client.loadLatestNews();
  });

  test('reports a backend error while loading news', () async {
    final client = BackendClient(
      'https://example.test',
      client: MockClient((_) async => http.Response('error', 500)),
    );

    await expectLater(client.loadLatestNews(), throwsStateError);
  });
}
