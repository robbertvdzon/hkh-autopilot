import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/backend/backend_client.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  test('loads and parses latest news', () async {
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

    final news = await client.loadLatestNews();

    expect(news, hasLength(1));
    expect(news.single.id, 42);
    expect(news.single.title, 'Dorpsnieuws');
    expect(news.single.message, 'Een verhaal');
    expect(news.single.publishedAt, DateTime.utc(2026, 8, 7, 9));
    expect(news.single.entities, hasLength(1));
    expect(news.single.entities.single.type, 'PLEK');
    expect(news.single.entities.single.label, 'Kerkweg');
    expect(news.single.source, isNotNull);
  });

  test('reports a backend error while loading news', () async {
    final client = BackendClient(
      'https://example.test',
      client: MockClient((_) async => http.Response('error', 500)),
    );

    await expectLater(client.loadLatestNews(), throwsStateError);
  });
}
