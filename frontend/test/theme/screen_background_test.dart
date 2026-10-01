import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/backend/backend_status.dart';
import 'package:hkh_app/main.dart';
import 'package:hkh_app/news/latest_news.dart';
import 'package:hkh_app/personquery/person_query_page.dart';
import 'package:hkh_app/personsearch/person_search_client.dart';
import 'package:hkh_app/personsearch/person_search_models.dart';
import 'package:hkh_app/product_vision_page.dart';
import 'package:hkh_app/theme/app_theme.dart';

const _backendStatus = BackendStatus(
  application: 'hkh',
  version: 'test',
  commit: 'abc123',
);

class _StatusSource implements BackendStatusSource {
  @override
  Future<BackendStatus> load() async => _backendStatus;
}

class _NewsSource implements LatestNewsSource {
  @override
  Future<List<LatestNewsItem>> loadLatestNews() async => const [];
}

/// Stille fake die nooit het netwerk raakt. De sessie-indicator in de AppBar
/// van [PersonQueryPage] bevraagt zijn bron al in `initState`; zonder deze
/// injectie zou de pagina de echte client aanmaken.
class _IdlePersonSearchSource implements PersonSearchSource {
  @override
  Future<PersonSearchResult> submit({
    required String recognizedName,
    String? secondName,
    String? eventType,
    String? yearOrPeriod,
    String? heemskerkMeaningQid,
    required String originalQuery,
  }) => throw UnimplementedError('niet gebruikt in deze tests');

  @override
  Future<PersonSearchStatusResult> pollStatus(String jobId) =>
      throw UnimplementedError('niet gebruikt in deze tests');

  @override
  Future<PersonSearchStatusResult> cancel(String jobId) =>
      throw UnimplementedError('niet gebruikt in deze tests');

  @override
  Future<PersonSearchStatusResult> open(String jobId) =>
      throw UnimplementedError('niet gebruikt in deze tests');

  @override
  Future<PersonSearchSessionIndicator> sessionIndicator() async {
    return const PersonSearchSessionIndicator(
      runningCount: 0,
      readyUnopenedCount: 0,
      runningJobIds: [],
      readyUnopenedJobIds: [],
    );
  }
}

/// De daadwerkelijk toegepaste achtergrondkleur van een scherm: de kleur van
/// het `Material` dat `Scaffold` zelf om zijn inhoud zet. Dit leest dus het
/// resultaat van de widgetboom, niet de themawaarde die erin ging.
Color appliedScaffoldBackground(WidgetTester tester) {
  final material = tester.widget<Material>(
    find
        .descendant(of: find.byType(Scaffold), matching: find.byType(Material))
        .first,
  );
  final color = material.color;
  expect(color, isNotNull, reason: 'Scaffold zonder achtergrondkleur');
  return color!;
}

Widget _wrap(Widget child) => MaterialApp(theme: AppTheme.light, home: child);

void main() {
  group('AC2 — elk app-niveau scherm vult zijn achtergrond met het token', () {
    testWidgets('de homepage gebruikt het achtergrondtoken', (tester) async {
      await tester.pumpWidget(
        HkhApp(statusSource: _StatusSource(), newsSource: _NewsSource()),
      );
      await tester.pumpAndSettle();

      expect(find.byType(HomePage), findsOneWidget);
      expect(appliedScaffoldBackground(tester), AppTheme.backgroundColor);
    });

    testWidgets('de productvisiepagina gebruikt het achtergrondtoken', (
      tester,
    ) async {
      await tester.pumpWidget(_wrap(const ProductVisionPage()));
      await tester.pumpAndSettle();

      expect(find.byType(ProductVisionPage), findsOneWidget);
      expect(appliedScaffoldBackground(tester), AppTheme.backgroundColor);
    });

    testWidgets('de persoonsvraagpagina gebruikt het achtergrondtoken', (
      tester,
    ) async {
      await tester.pumpWidget(
        _wrap(PersonQueryPage(personSearchSource: _IdlePersonSearchSource())),
      );
      await tester.pumpAndSettle();

      expect(find.byType(PersonQueryPage), findsOneWidget);
      expect(appliedScaffoldBackground(tester), AppTheme.backgroundColor);
    });

    testWidgets('geen enkel scherm zet zelf een achtergrondkleurliteraal', (
      tester,
    ) async {
      // Elk `Scaffold` laat `backgroundColor` leeg en erft daarmee het ene
      // token uit het thema. Zou een scherm alsnog een eigen kleur zetten,
      // dan faalt deze test.
      Future<void> check(Widget screen) async {
        await tester.pumpWidget(_wrap(screen));
        await tester.pumpAndSettle();
        final scaffold = tester.widget<Scaffold>(find.byType(Scaffold));
        expect(scaffold.backgroundColor, isNull);
      }

      await check(const ProductVisionPage());
      await check(
        PersonQueryPage(personSearchSource: _IdlePersonSearchSource()),
      );
    });

    testWidgets('de homepage erft de achtergrond ook op vervolgschermen', (
      tester,
    ) async {
      await tester.pumpWidget(
        HkhApp(statusSource: _StatusSource(), newsSource: _NewsSource()),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Lees onze productvisie'));
      await tester.pumpAndSettle();

      expect(find.byType(ProductVisionPage), findsOneWidget);
      expect(appliedScaffoldBackground(tester), AppTheme.backgroundColor);
    });
  });

  group('AC5 — de publieke applicatie heeft geen donker thema', () {
    testWidgets(
      'MaterialApp levert geen darkTheme en geen highContrast-thema',
      (tester) async {
        await tester.pumpWidget(
          HkhApp(statusSource: _StatusSource(), newsSource: _NewsSource()),
        );
        await tester.pumpAndSettle();

        final app = tester.widget<MaterialApp>(find.byType(MaterialApp));
        expect(app.darkTheme, isNull);
        expect(app.highContrastTheme, isNull);
        expect(app.highContrastDarkTheme, isNull);
        expect(app.theme, isNotNull);
        expect(app.theme!.brightness, Brightness.light);
      },
    );
  });
}
