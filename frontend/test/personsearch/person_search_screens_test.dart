import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/personsearch/background_search_screen.dart';
import 'package:hkh_app/personsearch/followed_connection_screen.dart';
import 'package:hkh_app/personsearch/live_search_screen.dart';
import 'package:hkh_app/personsearch/person_search_models.dart';
import 'package:hkh_app/personsearch/search_ready_screen.dart';
import 'package:hkh_app/personsearch/source_outage_screen.dart';
import 'package:hkh_app/personsearch/supported_answer_screen.dart';

PersonSearchAnswer _nicolaasAnswer() {
  return PersonSearchAnswer(
    sentences: const [
      PersonSearchAnswerSentence(
        text:
            'Nicolaas Jacobus Sinnige is geboren op 25 juli 1878 in Heemskerk.',
        sourceNumbers: [1],
      ),
      PersonSearchAnswerSentence(
        text: 'Pieter Sinnige was de vader van Nicolaas Jacobus Sinnige.',
        sourceNumbers: [1],
      ),
    ],
    sources: [
      PersonSearchSourceCitation(
        number: 1,
        institution: 'Noord-Hollands Archief',
        sourceType: 'Geboorteakte',
        archiveCode: 'nha',
        identifier: '002ED0F3-F08C-4223-A5EA-BA385D04336E',
        archiveNumber: '123',
        registerNumber: '4',
        deedNumber: '56',
        recordNumber: '789',
        openArchivesLink:
            'https://www.openarchieven.nl/nha:002ED0F3-F08C-4223-A5EA-BA385D04336E',
        checkedAt: DateTime.utc(2026, 8, 28, 10),
      ),
    ],
    connections: const [
      PersonSearchConnectionOption(role: 'Vader', personName: 'Pieter Sinnige'),
      PersonSearchConnectionOption(
        role: 'Moeder',
        personName: 'Anna Geertruida Eenhuis',
      ),
    ],
    disclaimer:
        'Deze ene geboorteakte is geen volledig levensverhaal van Nicolaas '
        'Jacobus Sinnige en geen overzicht van alle gebeurtenissen in '
        'Heemskerk in 1878.',
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

  group('LiveSearchScreen', () {
    testWidgets('toont de vraag en een leesbare laadstatus', (tester) async {
      await useGenerousViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: LiveSearchScreen(
              originalQuery: 'Wie was Jan Jansen?',
              stillRunning: false,
              onBackToStart: () {},
            ),
          ),
        ),
      );

      expect(find.text('Wie was Jan Jansen?'), findsOneWidget);
      final statusNodes = find.semantics
          .byPredicate(
            (node) => node.getSemanticsData().role == SemanticsRole.status,
            view: tester.view,
          )
          .evaluate();
      expect(statusNodes, isNotEmpty);
    });

    testWidgets(
      'toont een aangepaste melding wanneer de deadline is overschreden',
      (tester) async {
        await useGenerousViewport(tester);
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: LiveSearchScreen(
                originalQuery: 'Wie was Jan Jansen?',
                stillRunning: true,
                onBackToStart: () {},
              ),
            ),
          ),
        );

        expect(
          find.textContaining('duurt langer dan verwacht'),
          findsOneWidget,
        );
      },
    );

    testWidgets('blijft bij 320px breed zonder overloop', (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: LiveSearchScreen(
              originalQuery: 'Wie was Jan Jansen?',
              stillRunning: false,
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
  });

  group('SupportedAnswerScreen', () {
    testWidgets(
      'toont zinnen met bronmarkering, bronnen, context en vervolgsporen',
      (tester) async {
        await useGenerousViewport(tester);
        PersonSearchConnectionOption? followed;
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: SupportedAnswerScreen(
                originalQuery: 'Wie was Nicolaas Jacobus Sinnige?',
                answer: _nicolaasAnswer(),
                wikidataContext: const PersonSearchWikidataContext(
                  label: 'Heemskerk',
                  description: 'Gemeente in Noord-Holland',
                ),
                onFollowConnection: (connection) => followed = connection,
                onBackToStart: () {},
              ),
            ),
          ),
        );

        expect(
          find.textContaining(
            'Nicolaas Jacobus Sinnige is geboren op 25 juli 1878',
          ),
          findsOneWidget,
        );
        expect(find.textContaining('[1]'), findsWidgets);
        expect(find.text('Context'), findsOneWidget);
        expect(find.text('Heemskerk'), findsOneWidget);
        expect(
          find.textContaining('geen volledig levensverhaal'),
          findsWidgets,
        );

        await tester.tap(find.text('Volg vader: Pieter Sinnige'));
        await tester.pump();
        expect(followed?.personName, 'Pieter Sinnige');
      },
    );

    testWidgets(
      'caps followed connection buttons at the two provided, never more',
      (tester) async {
        await useGenerousViewport(tester);
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: SupportedAnswerScreen(
                originalQuery: 'Q',
                answer: _nicolaasAnswer(),
                wikidataContext: null,
                onFollowConnection: (_) {},
                onBackToStart: () {},
              ),
            ),
          ),
        );

        expect(find.textContaining('Volg '), findsNWidgets(2));
        expect(find.text('Context'), findsNothing);
      },
    );

    testWidgets('blijft bij 320px breed zonder overloop', (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SupportedAnswerScreen(
              originalQuery: 'Wie was Nicolaas Jacobus Sinnige?',
              answer: _nicolaasAnswer(),
              wikidataContext: const PersonSearchWikidataContext(
                label: 'Heemskerk',
              ),
              onFollowConnection: (_) {},
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
            body: SupportedAnswerScreen(
              originalQuery: 'Q',
              answer: _nicolaasAnswer(),
              wikidataContext: null,
              onFollowConnection: (_) {},
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

  group('SourceOutageScreen', () {
    testWidgets(
      'duidt Open Archieven exact aan als tijdelijk niet geraadpleegd',
      (tester) async {
        await useGenerousViewport(tester);
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: SourceOutageScreen(
                originalQuery: 'Wie was Jan Jansen?',
                wikidataContext: const PersonSearchWikidataContext(
                  label: 'Heemskerk',
                ),
                onBackToStart: () {},
              ),
            ),
          ),
        );

        expect(
          find.text('Open Archieven is tijdelijk niet geraadpleegd'),
          findsOneWidget,
        );
        expect(find.text('Context'), findsOneWidget);
        expect(find.text('Heemskerk'), findsOneWidget);
      },
    );

    testWidgets('toont geen Context-sectie zonder Wikidata-resultaat', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SourceOutageScreen(
              originalQuery: 'Wie was Jan Jansen?',
              wikidataContext: null,
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
            body: SourceOutageScreen(
              originalQuery: 'Wie was Jan Jansen?',
              wikidataContext: null,
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
  });

  group('BackgroundSearchScreen', () {
    testWidgets('toont de vraag, starttijdstip en per-bron voortgang', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: BackgroundSearchScreen(
              originalQuery: 'Wie was Jan Jansen?',
              startedAt: DateTime.utc(2026, 8, 28, 10),
              status: PersonSearchStatus.running,
              openArchievenStatus:
                  PersonSearchSourceConsultationStatus.inProgress,
              wikidataStatus: PersonSearchSourceConsultationStatus.notStarted,
              onAskAnotherQuestion: () {},
              onStop: () {},
            ),
          ),
        ),
      );

      expect(find.text('Wie was Jan Jansen?'), findsOneWidget);
      expect(find.text('Open Archieven'), findsOneWidget);
      expect(find.text('Bezig'), findsWidgets);
      expect(find.text('Wikidata · Context'), findsOneWidget);
      expect(find.text('Nog niet gestart'), findsOneWidget);
      final statusNodes = find.semantics
          .byPredicate(
            (node) => node.getSemanticsData().role == SemanticsRole.status,
            view: tester.view,
          )
          .evaluate();
      expect(statusNodes, isNotEmpty);
    });

    testWidgets('een andere vraag stellen en stoppen roepen hun callback aan', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      var askedAnother = false;
      var stopped = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: BackgroundSearchScreen(
              originalQuery: 'Wie was Jan Jansen?',
              startedAt: DateTime.utc(2026, 8, 28, 10),
              status: PersonSearchStatus.queued,
              openArchievenStatus:
                  PersonSearchSourceConsultationStatus.notStarted,
              wikidataStatus: PersonSearchSourceConsultationStatus.notStarted,
              onAskAnotherQuestion: () => askedAnother = true,
              onStop: () => stopped = true,
            ),
          ),
        ),
      );

      await tester.tap(find.text('Stel intussen een andere vraag'));
      await tester.pump();
      expect(askedAnother, isTrue);

      await tester.tap(find.text('Stop opdracht'));
      await tester.pump();
      expect(stopped, isTrue);
    });

    testWidgets('Tab bereikt de stopknop en Enter activeert deze', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      var stopped = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: BackgroundSearchScreen(
              originalQuery: 'Wie was Jan Jansen?',
              startedAt: DateTime.utc(2026, 8, 28, 10),
              status: PersonSearchStatus.running,
              openArchievenStatus:
                  PersonSearchSourceConsultationStatus.inProgress,
              wikidataStatus: PersonSearchSourceConsultationStatus.notStarted,
              onAskAnotherQuestion: () {},
              onStop: () => stopped = true,
            ),
          ),
        ),
      );

      for (var i = 0; i < 20; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();

      expect(stopped, isTrue);
    });

    testWidgets('blijft bij 320px breed zonder overloop', (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: BackgroundSearchScreen(
              originalQuery: 'Wie was Jan Jansen?',
              startedAt: DateTime.utc(2026, 8, 28, 10),
              status: PersonSearchStatus.running,
              openArchievenStatus:
                  PersonSearchSourceConsultationStatus.inProgress,
              wikidataStatus: PersonSearchSourceConsultationStatus.notStarted,
              onAskAnotherQuestion: () {},
              onStop: () {},
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
  });

  group('SearchReadyScreen', () {
    testWidgets(
      'toont voltooiingstijdstip en de daadwerkelijk geraadpleegde bronnen',
      (tester) async {
        await useGenerousViewport(tester);
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: SearchReadyScreen(
                originalQuery: 'Wie was Jan Jansen?',
                completedAt: DateTime.utc(2026, 8, 28, 10, 5),
                openArchievenStatus:
                    PersonSearchSourceConsultationStatus.succeeded,
                wikidataStatus: PersonSearchSourceConsultationStatus.succeeded,
                onViewAnswer: () {},
              ),
            ),
          ),
        );

        expect(find.text('Wie was Jan Jansen?'), findsOneWidget);
        expect(
          find.textContaining('Open Archieven en Wikidata'),
          findsOneWidget,
        );
        expect(find.text('Bekijk het antwoord'), findsOneWidget);
      },
    );

    testWidgets('heeft precies één actie die het antwoord opent', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      var opened = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SearchReadyScreen(
              originalQuery: 'Wie was Jan Jansen?',
              completedAt: DateTime.utc(2026, 8, 28, 10, 5),
              openArchievenStatus:
                  PersonSearchSourceConsultationStatus.succeeded,
              wikidataStatus: PersonSearchSourceConsultationStatus.notStarted,
              onViewAnswer: () => opened = true,
            ),
          ),
        ),
      );

      expect(find.byType(FilledButton), findsOneWidget);
      await tester.tap(find.byType(FilledButton));
      await tester.pump();
      expect(opened, isTrue);
    });

    testWidgets('Tab bereikt de antwoordknop en Enter activeert deze', (
      tester,
    ) async {
      await useGenerousViewport(tester);
      var opened = false;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SearchReadyScreen(
              originalQuery: 'Wie was Jan Jansen?',
              completedAt: DateTime.utc(2026, 8, 28, 10, 5),
              openArchievenStatus:
                  PersonSearchSourceConsultationStatus.succeeded,
              wikidataStatus: PersonSearchSourceConsultationStatus.succeeded,
              onViewAnswer: () => opened = true,
            ),
          ),
        ),
      );

      for (var i = 0; i < 10; i++) {
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();

      expect(opened, isTrue);
    });

    testWidgets('blijft bij 320px breed zonder overloop', (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SearchReadyScreen(
              originalQuery: 'Wie was Jan Jansen?',
              completedAt: DateTime.utc(2026, 8, 28, 10, 5),
              openArchievenStatus:
                  PersonSearchSourceConsultationStatus.succeeded,
              wikidataStatus: PersonSearchSourceConsultationStatus.succeeded,
              onViewAnswer: () {},
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
  });

  group('FollowedConnectionScreen', () {
    const connection = PersonSearchConnectionOption(
      role: 'Vader',
      personName: 'Pieter Sinnige',
    );

    testWidgets(
      'houdt de oorspronkelijke vraag en het spoor zichtbaar met disclaimer',
      (tester) async {
        await useGenerousViewport(tester);
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: FollowedConnectionScreen(
                originalQuery: 'Wie was Nicolaas Jacobus Sinnige?',
                connection: connection,
                onBackToAnswer: () {},
                onBackToStart: () {},
              ),
            ),
          ),
        );

        expect(find.text('Wie was Nicolaas Jacobus Sinnige?'), findsOneWidget);
        expect(find.text('Vader: Pieter Sinnige'), findsOneWidget);
        expect(
          find.textContaining(
            'is geen volledig levensverhaal van Pieter Sinnige',
          ),
          findsOneWidget,
        );
      },
    );

    testWidgets('blijft bij 320px breed zonder overloop', (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: FollowedConnectionScreen(
              originalQuery: 'Wie was Nicolaas Jacobus Sinnige?',
              connection: connection,
              onBackToAnswer: () {},
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
  });
}
