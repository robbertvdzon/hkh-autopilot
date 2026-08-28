import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/personquery/no_reliable_source_screen.dart';
import 'package:hkh_app/personquery/person_query_page.dart';
import 'package:hkh_app/personquery/wikidata_meaning_client.dart';

class _FakeMeaningSource implements WikidataMeaningSource {
  _FakeMeaningSource.success()
    : _result = const WikidataMeaningResult(
        place: WikidataMeaningOption(
          qid: WikidataMeaningIds.place,
          label: 'Q9926 · Heemskerk',
          description: 'De gemeente en het dorp Heemskerk.',
        ),
        surname: WikidataMeaningOption(
          qid: WikidataMeaningIds.surname,
          label: 'Q91564725 · Heemskerk',
          description: 'Heemskerk gebruikt als achternaam.',
        ),
      ),
      _shouldFail = false;

  _FakeMeaningSource.failure() : _result = null, _shouldFail = true;

  final WikidataMeaningResult? _result;
  final bool _shouldFail;

  int calls = 0;

  @override
  Future<WikidataMeaningResult> fetchMeanings() async {
    calls++;
    if (_shouldFail) {
      throw const WikidataMeaningException('offline in test');
    }
    return _result!;
  }
}

bool _isSelected(WidgetTester tester, String qid) {
  final widget = tester.widget(find.byKey(ValueKey('meaning-option-$qid')));
  // ignore: avoid_dynamic_calls
  return (widget as dynamic).selected as bool;
}

Future<void> _submit(WidgetTester tester, String query) async {
  await tester.enterText(find.byType(TextField), query);
  await tester.tap(find.widgetWithText(FilledButton, 'Zoek in bronnen'));
  await tester.pumpAndSettle();
}

void main() {
  Future<void> useGenerousViewport(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1400, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
  }

  testWidgets(
    'startscherm toont het verplichte veldlabel, voorbeeldvraag, dekking en sessiemededeling',
    (tester) async {
      await useGenerousViewport(tester);
      await tester.pumpWidget(const MaterialApp(home: PersonQueryPage()));
      await tester.pumpAndSettle();

      expect(find.byType(TextField), findsOneWidget);
      expect(
        find.bySemanticsLabel('Stel je vraag over Heemskerk'),
        findsOneWidget,
      );
      expect(find.textContaining('Nicolaas Jacobus Sinnige'), findsWidgets);
      expect(
        find.textContaining(
          'Open Archieven-genealogie voor Heemskerk, met Wikidata als aanvullende context',
        ),
        findsOneWidget,
      );
      expect(
        find.textContaining('kan binnen deze sessie gewoon doorlopen'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'een vraag zonder herkenbare naam toont het no-reliable-source-scherm zonder externe aanroep',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Wat gebeurde er hier?');

      expect(
        find.text('Hiervoor vinden we geen betrouwbare bron'),
        findsOneWidget,
      );
      expect(
        find.text('Niet uitgevoerd · persoonsnaam ontbreekt'),
        findsOneWidget,
      );
      for (final suggestion in NoReliableSourceScreen.suggestions) {
        expect(find.text(suggestion), findsOneWidget);
      }
      expect(meaningSource.calls, 0);

      await tester.tap(find.text(NoReliableSourceScreen.suggestions.first));
      await tester.pumpAndSettle();

      final field = tester.widget<TextField>(find.byType(TextField));
      expect(field.controller?.text, NoReliableSourceScreen.suggestions.first);
    },
  );

  testWidgets(
    'een naam zonder ambigue Heemskerk-vermelding gaat direct door zonder keuzescherm',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(
        tester,
        'Wie was Nicolaas Jacobus Sinnige, geboren in Heemskerk in 1878?',
      );

      expect(
        find.textContaining('Geïnterpreteerd: Nicolaas Jacobus Sinnige'),
        findsOneWidget,
      );
      expect(find.textContaining('Jaartal: 1878'), findsOneWidget);
      expect(meaningSource.calls, 0);
      expect(
        find.text('Heemskerk kan hier twee dingen betekenen'),
        findsNothing,
      );
    },
  );

  testWidgets(
    'ambigue Heemskerk-vraag toont meaning-selection met live labels en "Vraag aanpassen" bewaart de tekst',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Cornelis Heemskerk');

      expect(
        find.text('Heemskerk kan hier twee dingen betekenen'),
        findsOneWidget,
      );
      expect(find.textContaining('Q9926 · Heemskerk'), findsOneWidget);
      expect(find.textContaining('Q91564725 · Heemskerk'), findsOneWidget);
      expect(meaningSource.calls, 1);

      await tester.tap(find.widgetWithText(OutlinedButton, 'Vraag aanpassen'));
      await tester.pumpAndSettle();

      final field = tester.widget<TextField>(find.byType(TextField));
      expect(field.controller?.text, 'Cornelis Heemskerk');
    },
  );

  testWidgets(
    'bevestigen op meaning-selection toont de gekozen betekenis, nooit samengevoegd',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Cornelis Heemskerk');

      await tester.tap(find.byKey(const ValueKey('meaning-radio-Q91564725')));
      await tester.pumpAndSettle();
      expect(_isSelected(tester, WikidataMeaningIds.surname), isTrue);
      expect(_isSelected(tester, WikidataMeaningIds.place), isFalse);

      await tester.tap(
        find.widgetWithText(FilledButton, 'Zoek met deze betekenis'),
      );
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Gekozen betekenis voor Heemskerk: Q91564725'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'meaning-selection ondersteunt pijltjestoetsnavigatie tussen de twee opties',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Cornelis Heemskerk');

      expect(_isSelected(tester, WikidataMeaningIds.place), isTrue);

      // Tab tot de eerste radio-optie (plaats) toetsenbordfocus heeft; alleen
      // dan verwerkt de RadioGroup de pijltjestoetsen.
      for (var i = 0; i < 8; i++) {
        if (FocusManager.instance.primaryFocus?.debugLabel == 'meaning-place') {
          break;
        }
        await tester.sendKeyEvent(LogicalKeyboardKey.tab);
        await tester.pump();
      }
      expect(
        FocusManager.instance.primaryFocus?.debugLabel,
        'meaning-place',
        reason: 'De plaats-radio moet via Tab toetsenbordfocus kunnen krijgen.',
      );

      await tester.sendKeyEvent(LogicalKeyboardKey.arrowDown);
      await tester.pump();
      expect(_isSelected(tester, WikidataMeaningIds.place), isFalse);
      expect(_isSelected(tester, WikidataMeaningIds.surname), isTrue);

      await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
      await tester.pump();
      expect(_isSelected(tester, WikidataMeaningIds.place), isTrue);
      expect(_isSelected(tester, WikidataMeaningIds.surname), isFalse);
    },
  );

  testWidgets(
    'bij een mislukte Wikidata-oproep verschijnen vaste fallback-labels met een zichtbare storingsmelding, keuze blijft bruikbaar',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.failure();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Cornelis Heemskerk');

      expect(find.textContaining('Q9926 · Heemskerk (plaats)'), findsOneWidget);
      expect(
        find.textContaining('Q91564725 · Heemskerk (achternaam)'),
        findsOneWidget,
      );
      expect(find.textContaining('kon niet worden opgehaald'), findsOneWidget);

      await tester.tap(
        find.widgetWithText(FilledButton, 'Zoek met deze betekenis'),
      );
      await tester.pumpAndSettle();

      expect(
        find.textContaining('Gekozen betekenis voor Heemskerk'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'statussen op alle drie schermen worden als leesbare tekst overgebracht, niet uitsluitend via kleur',
    (tester) async {
      await useGenerousViewport(tester);
      final meaningSource = _FakeMeaningSource.failure();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Cornelis Heemskerk');

      final statusNodes = find.semantics
          .byPredicate(
            (node) => node.getSemanticsData().role == SemanticsRole.status,
            view: tester.view,
          )
          .evaluate();
      expect(statusNodes, isNotEmpty);
      for (final element in statusNodes) {
        expect(element.label, isNotEmpty);
      }
      // Het storingsicoon is decoratief (net als de bestaande iconen in
      // main.dart) en wordt uitsluitend naast leesbare statustekst getoond,
      // nooit als enige drager van de informatie.
      expect(find.byIcon(Icons.warning_amber), findsOneWidget);
      expect(find.textContaining('kon niet worden opgehaald'), findsOneWidget);
    },
  );

  testWidgets('Tab/Shift+Tab en Enter werken op het startscherm', (
    tester,
  ) async {
    await useGenerousViewport(tester);
    await tester.pumpWidget(const MaterialApp(home: PersonQueryPage()));
    await tester.pumpAndSettle();

    await tester.tap(find.byType(TextField));
    await tester.enterText(
      find.byType(TextField),
      'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?',
    );
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();

    expect(
      find.textContaining('Geïnterpreteerd: Nicolaas Jacobus Sinnige'),
      findsOneWidget,
    );
  });

  Future<void> useNarrowMobileViewport(WidgetTester tester) async {
    tester.view.physicalSize = const Size(320, 4000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
  }

  testWidgets(
    'startscherm blijft bij 320px breed zonder overloop of horizontaal scrollen',
    (tester) async {
      await useNarrowMobileViewport(tester);
      await tester.pumpWidget(const MaterialApp(home: PersonQueryPage()));
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
      expect(
        tester.getSize(find.byType(MaterialApp)).width,
        lessThanOrEqualTo(320),
      );
    },
  );

  testWidgets(
    'no-reliable-source-scherm blijft bij 320px breed zonder overloop',
    (tester) async {
      await useNarrowMobileViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Wat gebeurde er hier?');

      expect(tester.takeException(), isNull);
      expect(
        find.text('Hiervoor vinden we geen betrouwbare bron'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'meaning-selection-scherm blijft bij 320px breed zonder overloop',
    (tester) async {
      await useNarrowMobileViewport(tester);
      final meaningSource = _FakeMeaningSource.success();
      await tester.pumpWidget(
        MaterialApp(home: PersonQueryPage(meaningSource: meaningSource)),
      );
      await tester.pumpAndSettle();

      await _submit(tester, 'Cornelis Heemskerk');

      expect(tester.takeException(), isNull);
      expect(
        find.text('Heemskerk kan hier twee dingen betekenen'),
        findsOneWidget,
      );
    },
  );
}
