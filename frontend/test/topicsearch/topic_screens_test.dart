import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/topicsearch/topic_empty_screen.dart';
import 'package:hkh_app/topicsearch/topic_outage_screen.dart';
import 'package:hkh_app/topicsearch/topic_results_screen.dart';
import 'package:hkh_app/topicsearch/topic_search_models.dart';

TopicSearchAnswer _watersnoodAnswer({TopicSearchContext? context}) {
  return TopicSearchAnswer(
    topicSearchTerm: 'watersnood van 1916',
    records: const [
      TopicSearchRecord(
        title: 'Watersnood van 1916 bij Heemskerk',
        dataProvider: 'Noord-Hollands Archief',
        license: TopicSearchLicenseBadge(
          text: 'Publiek domein',
          url: 'https://creativecommons.org/publicdomain/mark/1.0/',
        ),
        sourceUrl: 'https://archief.example/1',
      ),
      TopicSearchRecord(
        title: 'Foto dijkdoorbraak 1916',
        dataProvider: 'Rijksmuseum',
        license: TopicSearchLicenseBadge(
          text: 'CC BY-SA',
          url: 'https://creativecommons.org/licenses/by-sa/4.0/',
        ),
        sourceUrl: 'https://archief.example/2',
      ),
    ],
    context: context,
    checkedAt: DateTime.utc(2026, 9, 10, 10),
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

  group('TopicResultsScreen', () {
    testWidgets('toont records met titel, dataProvider, licentie en context', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: TopicResultsScreen(
              originalQuery: 'Wat weten we over de watersnood van 1916?',
              answer: _watersnoodAnswer(
                context: const TopicSearchContext(
                  label: 'Watersnood van 1916',
                  description: 'overstroming in Nederland',
                ),
              ),
              onBackToStart: () {},
            ),
          ),
        ),
      );

      expect(find.text('Watersnood van 1916 bij Heemskerk'), findsOneWidget);
      expect(find.text('Foto dijkdoorbraak 1916'), findsOneWidget);
      expect(find.text('Noord-Hollands Archief'), findsOneWidget);
      expect(find.text('Publiek domein'), findsOneWidget);
      expect(find.text('CC BY-SA'), findsOneWidget);
      expect(find.text('Context'), findsOneWidget);
      expect(find.text('Watersnood van 1916'), findsOneWidget);
      expect(find.textContaining('2 gevonden item'), findsOneWidget);
    });

    testWidgets('toont geen Context-blok wanneer er geen context is', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: TopicResultsScreen(
              originalQuery: 'Wat weten we over de watersnood van 1916?',
              answer: _watersnoodAnswer(),
              onBackToStart: () {},
            ),
          ),
        ),
      );

      expect(find.text('Context'), findsNothing);
    });

    testWidgets('blijft bij 320px breed zonder overloop', (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: TopicResultsScreen(
              originalQuery: 'Wat weten we over de watersnood van 1916?',
              answer: _watersnoodAnswer(
                context: const TopicSearchContext(
                  label: 'Watersnood van 1916',
                ),
              ),
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
            body: TopicResultsScreen(
              originalQuery: 'Wat weten we over de watersnood van 1916?',
              answer: _watersnoodAnswer(),
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

  group('TopicEmptyScreen', () {
    testWidgets('toont exact de vereiste statustekst en verfijningsvoorstellen', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: TopicEmptyScreen(
              originalQuery: 'Wat weten we over onbekend onderwerp?',
              refinementSuggestions: const ['Gebruik een breder trefwoord.'],
              onBackToStart: () {},
            ),
          ),
        ),
      );

      expect(
        find.text('Hiervoor vinden we geen betrouwbare bron'),
        findsOneWidget,
      );
      expect(find.text('Gebruik een breder trefwoord.'), findsOneWidget);
      final statusNodes = find.semantics
          .byPredicate(
            (node) => node.getSemanticsData().role == SemanticsRole.status,
            view: tester.view,
          )
          .evaluate();
      expect(statusNodes, isNotEmpty);
    });

    testWidgets('blijft bij 320px breed zonder overloop', (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: TopicEmptyScreen(
              originalQuery: 'Onbekend',
              refinementSuggestions: const ['Gebruik een breder trefwoord.'],
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
            body: TopicEmptyScreen(
              originalQuery: 'Onbekend',
              refinementSuggestions: const [],
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

  group('TopicOutageScreen', () {
    testWidgets('toont de vereiste statustekst en de afhankelijke Wikidata-status', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: TopicOutageScreen(
              originalQuery: 'Wat weten we over de watersnood van 1916?',
              onRetry: () {},
              onBackToStart: () {},
            ),
          ),
        ),
      );

      expect(
        find.text('Europeana is tijdelijk niet geraadpleegd'),
        findsOneWidget,
      );
      expect(
        find.text('Niet uitgevoerd · afhankelijk van Europeana'),
        findsOneWidget,
      );
      final statusNodes = find.semantics
          .byPredicate(
            (node) => node.getSemanticsData().role == SemanticsRole.status,
            view: tester.view,
          )
          .evaluate();
      expect(statusNodes, isNotEmpty);
    });

    testWidgets('blijft bij 320px breed zonder overloop', (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: TopicOutageScreen(
              originalQuery: 'Wat weten we over de watersnood van 1916?',
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
              body: TopicOutageScreen(
                originalQuery: 'Wat weten we over de watersnood van 1916?',
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
