import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/placesearch/place_answer_screen.dart';
import 'package:hkh_app/placesearch/place_empty_screen.dart';
import 'package:hkh_app/placesearch/place_outage_screen.dart';
import 'package:hkh_app/placesearch/place_search_models.dart';

PlaceSearchAnswer _assumburgAnswer({
  bool commonsOutage = false,
  List<PlaceSearchImage> images = const [],
}) {
  return PlaceSearchAnswer(
    qid: 'Q1968571',
    label: 'Kasteel Assumburg',
    description: 'kasteel in Heemskerk',
    sentences: const [
      PlaceSearchAnswerSentence(
        text: 'Kasteel Assumburg is opgericht of gebouwd in 1546.',
        sourceNumbers: [1],
      ),
      PlaceSearchAnswerSentence(
        text: 'Kasteel Assumburg heeft de erfgoedstatus rijksmonument.',
        sourceNumbers: [2],
      ),
    ],
    contextSentence: const PlaceSearchAnswerSentence(
      text: 'Kasteel Assumburg ligt in de gemeente Heemskerk.',
      sourceNumbers: [3],
    ),
    sources: [
      PlaceSearchSourceCitation(
        number: 1,
        qid: 'Q1968571',
        wikidataLink: 'https://www.wikidata.org/wiki/Q1968571',
        checkedAt: DateTime.utc(2026, 8, 31, 10),
      ),
      PlaceSearchSourceCitation(
        number: 2,
        qid: 'Q1968571',
        wikidataLink: 'https://www.wikidata.org/wiki/Q1968571',
        checkedAt: DateTime.utc(2026, 8, 31, 10),
      ),
      PlaceSearchSourceCitation(
        number: 3,
        qid: 'Q1968571',
        wikidataLink: 'https://www.wikidata.org/wiki/Q1968571',
        checkedAt: DateTime.utc(2026, 8, 31, 10),
      ),
    ],
    images: images,
    commonsOutage: commonsOutage,
    disclaimer:
        'Dit is een actuele beschrijving van dit ene object uit Wikidata (Q1968571).',
    checkedAt: DateTime.utc(2026, 8, 31, 10),
  );
}

void main() {
  Future<void> useGenerousViewport(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1400, 2600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
  }

  Future<void> useNarrowMobileViewport(WidgetTester tester) async {
    tester.view.physicalSize = const Size(320, 5000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
  }

  group('PlaceAnswerScreen', () {
    testWidgets('toont zinnen met bronmarkering, context en bronnen', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: PlaceAnswerScreen(
              originalQuery: 'Wat is Kasteel Assumburg?',
              answer: _assumburgAnswer(),
              onBackToStart: () {},
            ),
          ),
        ),
      );

      expect(
        find.textContaining('opgericht of gebouwd in 1546'),
        findsOneWidget,
      );
      expect(find.textContaining('rijksmonument'), findsOneWidget);
      expect(find.text('Context'), findsOneWidget);
      expect(
        find.textContaining('ligt in de gemeente Heemskerk'),
        findsOneWidget,
      );
      expect(find.textContaining('[1]'), findsWidgets);
      expect(find.textContaining('[3]'), findsWidgets);
      expect(
        find.text('Geen afbeeldingen beschikbaar op Wikimedia Commons.'),
        findsOneWidget,
      );
    });

    testWidgets('toont een Commons-uitvalstatus in plaats van de galerij', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: PlaceAnswerScreen(
              originalQuery: 'Wat is Kasteel Assumburg?',
              answer: _assumburgAnswer(commonsOutage: true),
              onBackToStart: () {},
            ),
          ),
        ),
      );

      expect(
        find.text('Niet uitgevoerd · afhankelijk van Wikidata'),
        findsOneWidget,
      );
    });

    testWidgets('blijft bij 320px breed zonder overloop', (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: PlaceAnswerScreen(
              originalQuery: 'Wat is Kasteel Assumburg?',
              answer: _assumburgAnswer(),
              onBackToStart: () {},
            ),
          ),
        ),
      );

      expect(tester.takeException(), isNull);
      expect(
        tester.getSize(find.byType(MaterialApp)).width,
        lessThanOrEqualTo(320),
      );
    });

    testWidgets('Tab bereikt de terugknop en Enter activeert deze', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      var backPressed = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: PlaceAnswerScreen(
              originalQuery: 'Wat is Kasteel Assumburg?',
              answer: _assumburgAnswer(),
              onBackToStart: () => backPressed = true,
            ),
          ),
        ),
      );

      for (var i = 0; i < 12; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();

      expect(backPressed, isTrue);
    });
  });

  group('PlaceEmptyScreen', () {
    testWidgets(
      'toont exact de vereiste statustekst zonder verfijning bij 0 matches',
      (tester) async {
        await useGenerousViewport(tester);
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: PlaceEmptyScreen(
                originalQuery: 'Wat is een onbekend gebouw?',
                refinementCandidates: const [],
                onPickCandidate: (_) {},
                onBackToStart: () {},
              ),
            ),
          ),
        );

        expect(
          find.text('Hiervoor vinden we geen betrouwbare bron'),
          findsOneWidget,
        );
        expect(find.text('Bedoelde je een van deze?'), findsNothing);
        final statusNodes = find.semantics
            .byPredicate(
              (node) => node.getSemanticsData().role == SemanticsRole.status,
              view: tester.view,
            )
            .evaluate();
        expect(statusNodes, isNotEmpty);
      },
    );

    testWidgets(
      'toont verfijningsvoorstellen bij meerdere kandidaten zonder samenvoeging',
      (tester) async {
        await useGenerousViewport(tester);
        String? picked;
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: PlaceEmptyScreen(
                originalQuery: 'Kasteel',
                refinementCandidates: const [
                  PlaceSearchCandidate(qid: 'Q1', label: 'Kasteel A'),
                  PlaceSearchCandidate(qid: 'Q2', label: 'Kasteel B'),
                ],
                onPickCandidate: (label) => picked = label,
                onBackToStart: () {},
              ),
            ),
          ),
        );

        expect(
          find.text('Hiervoor vinden we geen betrouwbare bron'),
          findsOneWidget,
        );
        expect(find.text('Kasteel A'), findsOneWidget);
        expect(find.text('Kasteel B'), findsOneWidget);

        await tester.tap(find.text('Kasteel A'));
        await tester.pump();
        expect(picked, 'Kasteel A');
      },
    );

    testWidgets('blijft bij 320px breed zonder overloop', (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: PlaceEmptyScreen(
              originalQuery: 'Kasteel',
              refinementCandidates: const [
                PlaceSearchCandidate(qid: 'Q1', label: 'Kasteel A'),
                PlaceSearchCandidate(qid: 'Q2', label: 'Kasteel B'),
              ],
              onPickCandidate: (_) {},
              onBackToStart: () {},
            ),
          ),
        ),
      );

      expect(tester.takeException(), isNull);
      expect(
        tester.getSize(find.byType(MaterialApp)).width,
        lessThanOrEqualTo(320),
      );
    });

    testWidgets('Tab bereikt de terugknop en Enter activeert deze', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      var backPressed = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: PlaceEmptyScreen(
              originalQuery: 'Onbekend',
              refinementCandidates: const [],
              onPickCandidate: (_) {},
              onBackToStart: () => backPressed = true,
            ),
          ),
        ),
      );

      for (var i = 0; i < 12; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();

      expect(backPressed, isTrue);
    });
  });

  group('PlaceOutageScreen', () {
    testWidgets(
      'toont de vereiste statustekst en de afhankelijke Commons-status',
      (tester) async {
        await useGenerousViewport(tester);
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: PlaceOutageScreen(
                originalQuery: 'Wat is Kasteel Assumburg?',
                onRetry: () {},
                onBackToStart: () {},
              ),
            ),
          ),
        );

        expect(
          find.text('Wikidata is tijdelijk niet geraadpleegd'),
          findsOneWidget,
        );
        expect(
          find.text('Niet uitgevoerd · afhankelijk van Wikidata'),
          findsOneWidget,
        );
        final statusNodes = find.semantics
            .byPredicate(
              (node) => node.getSemanticsData().role == SemanticsRole.status,
              view: tester.view,
            )
            .evaluate();
        expect(statusNodes, isNotEmpty);
      },
    );

    testWidgets('blijft bij 320px breed zonder overloop', (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: PlaceOutageScreen(
              originalQuery: 'Wat is Kasteel Assumburg?',
              onRetry: () {},
              onBackToStart: () {},
            ),
          ),
        ),
      );

      expect(tester.takeException(), isNull);
      expect(
        tester.getSize(find.byType(MaterialApp)).width,
        lessThanOrEqualTo(320),
      );
    });

    testWidgets(
      'Tab bereikt beide knoppen en Enter activeert de gefocuste knop',
      (tester) async {
        await useGenerousViewport(tester);
        var retried = false;
        var backPressed = false;
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: PlaceOutageScreen(
                originalQuery: 'Wat is Kasteel Assumburg?',
                onRetry: () => retried = true,
                onBackToStart: () => backPressed = true,
              ),
            ),
          ),
        );

        for (var i = 0; i < 20 && !retried; i++) {
          await tester.sendKeyEvent(LogicalKeyboardKey.enter);
          await tester.pump();
          if (retried) break;
          await tester.sendKeyEvent(LogicalKeyboardKey.tab);
          await tester.pump();
        }
        expect(
          retried,
          isTrue,
          reason: '"Opnieuw proberen" moet via Tab/Enter bereikbaar zijn.',
        );

        for (var i = 0; i < 20 && !backPressed; i++) {
          await tester.sendKeyEvent(LogicalKeyboardKey.tab);
          await tester.pump();
          await tester.sendKeyEvent(LogicalKeyboardKey.enter);
          await tester.pump();
        }
        expect(
          backPressed,
          isTrue,
          reason: '"Nieuwe vraag stellen" moet via Tab/Enter bereikbaar zijn.',
        );
      },
    );
  });
}
