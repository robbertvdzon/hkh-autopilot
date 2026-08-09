import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/news/discover_section.dart';
import 'package:hkh_app/news/latest_news.dart';

final _newsItem = LatestNewsItem(
  id: 7,
  title: 'Kerkbrand in Heemskerk',
  message:
      'Op 1 augustus 2026 brak brand uit bij de kerk aan de Kerkweg. Niemand raakte gewond.',
  publishedAt: DateTime.utc(2026, 8, 1, 9),
  entities: const [NewsEntity(type: 'PLEK', label: 'Kerkweg')],
  source: 'Afkomstig uit gepubliceerd HKH-nieuwsbericht, gepubliceerd op 2026-08-01T09:00:00Z',
);

const _aggregatedEntities = [
  AggregatedNewsEntity(type: 'PLEK', label: 'Kerkweg', itemCount: 1),
];

class _FakeNewsSource implements LatestNewsSource {
  _FakeNewsSource(this.responses);

  final Map<String, NewsSearchResult> responses;
  final calls = <MapEntry<String?, String?>>[];

  @override
  Future<NewsSearchResult> loadLatestNews({String? q, String? entity}) async {
    calls.add(MapEntry(q, entity));
    return responses[_key(q, entity)] ??
        const NewsSearchResult(items: [], total: 0, entities: _aggregatedEntities);
  }

  static String _key(String? q, String? entity) => '${q ?? ''}|${entity ?? ''}';
}

NewsSearchResult _unfiltered() =>
    NewsSearchResult(items: [_newsItem], total: 1, entities: _aggregatedEntities);

NewsSearchResult _byEntity() =>
    NewsSearchResult(items: [_newsItem], total: 1, entities: _aggregatedEntities);

NewsSearchResult _emptySearch() =>
    const NewsSearchResult(items: [], total: 0, entities: _aggregatedEntities);

_FakeNewsSource _sourceWithResults() => _FakeNewsSource({
  '|': _unfiltered(),
  '|Kerkweg': _byEntity(),
});

Widget _wrap(Widget child) =>
    MaterialApp(home: Scaffold(body: SingleChildScrollView(child: child)));

const _searchFieldKey = Key('discover-search-field');
const _clearButtonKey = Key('discover-clear-button');
const _entityChipKey = Key('discover-entity-chip-Kerkweg');
const _resultCardKey = Key('discover-result-7');
const _backButtonKey = Key('discover-detail-back');

void main() {
  testWidgets(
    'renders a labelled search field and entity chips from the API entities, '
    'and a chip tap shows a results list with a non-empty source line',
    (tester) async {
      final source = _sourceWithResults();
      await tester.pumpWidget(_wrap(DiscoverSection(source: source)));
      await tester.pumpAndSettle();

      expect(find.byKey(_searchFieldKey), findsOneWidget);
      expect(find.byKey(_entityChipKey), findsOneWidget);
      expect(find.byKey(_resultCardKey), findsNothing);

      await tester.tap(find.byKey(_entityChipKey));
      await tester.pumpAndSettle();

      expect(find.byKey(_resultCardKey), findsOneWidget);
      expect(
        find.descendant(
          of: find.byKey(_resultCardKey),
          matching: find.textContaining('Afkomstig uit gepubliceerd HKH-nieuwsbericht'),
        ),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'an unknown search term shows the non-empty empty state with suggestion chips '
    'instead of a blank or broken view',
    (tester) async {
      final source = _FakeNewsSource({
        '|': _unfiltered(),
        'onvindbaarezoekterm|': _emptySearch(),
      });
      await tester.pumpWidget(_wrap(DiscoverSection(source: source)));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(_searchFieldKey), 'onvindbaarezoekterm');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('discover-empty-state')), findsOneWidget);
      expect(find.byKey(_resultCardKey), findsNothing);
      expect(
        find.byKey(const Key('discover-suggestion-chip-Kerkweg')),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'opening a result shows the full message, publication date and source '
    'plus a working back-to-results action',
    (tester) async {
      final source = _sourceWithResults();
      await tester.pumpWidget(_wrap(DiscoverSection(source: source)));
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(_entityChipKey));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(_resultCardKey));
      await tester.pumpAndSettle();

      expect(find.text(_newsItem.message), findsOneWidget);
      expect(find.textContaining('01-08-2026'), findsOneWidget);
      expect(find.textContaining('Afkomstig uit gepubliceerd HKH-nieuwsbericht'), findsOneWidget);
      expect(find.byKey(_backButtonKey), findsOneWidget);

      await tester.tap(find.byKey(_backButtonKey));
      await tester.pumpAndSettle();

      expect(find.byKey(_resultCardKey), findsOneWidget);
    },
  );

  testWidgets(
    'search field, chip, result card, back button and clear button are reachable '
    'purely via Tab focus traversal and activate only with Enter/Space',
    (tester) async {
      final source = _sourceWithResults();
      await tester.pumpWidget(_wrap(DiscoverSection(source: source)));
      await tester.pumpAndSettle();

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_hasPrimaryFocusWithin(find.byKey(_searchFieldKey)), isTrue);
      // Flutter's own EditableText docs state that a raw Enter key event does not trigger
      // onEditingComplete/onSubmitted in widget tests (the engine routes text-field submission
      // through the IME action channel, not a hardware key event); `testTextInput.receiveAction`
      // is the documented way to simulate the same Enter-activation without a tap/mouse gesture.
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pump();

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_hasPrimaryFocusWithin(find.byKey(_entityChipKey)), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pumpAndSettle();
      expect(find.byKey(_resultCardKey), findsOneWidget);

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_hasPrimaryFocusWithin(find.byKey(_resultCardKey)), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(find.byKey(_backButtonKey), findsOneWidget);

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_hasPrimaryFocusWithin(find.byKey(_backButtonKey)), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pumpAndSettle();
      expect(find.byKey(_resultCardKey), findsOneWidget);

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_hasPrimaryFocusWithin(find.byKey(_clearButtonKey)), isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pumpAndSettle();

      expect(find.byKey(_resultCardKey), findsNothing);
    },
  );

  group('semantics tree', () {
    testWidgets(
      'search field, chip, result card, back button and clear button expose a correct '
      'semantic label and role',
      (tester) async {
        final source = _sourceWithResults();
        await tester.pumpWidget(_wrap(DiscoverSection(source: source)));
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(_entityChipKey));
        await tester.pumpAndSettle();

        // TextField composes its own SemanticsNode from an internal render object rather
        // than the element found by key, so its label is asserted via bySemanticsLabel
        // (the pattern used elsewhere in this repo) instead of tester.getSemantics.
        expect(find.bySemanticsLabel('Zoek in nieuwsberichten'), findsOneWidget);

        final chipData = tester.getSemantics(find.byKey(_entityChipKey));
        expect(chipData.label, 'Plek: Kerkweg');
        // ignore: deprecated_member_use
        expect(chipData.hasFlag(SemanticsFlag.isButton), isTrue);

        final cardData = tester.getSemantics(find.byKey(_resultCardKey));
        expect(cardData.label, contains(_newsItem.title));
        expect(cardData.getSemanticsData().hasAction(SemanticsAction.tap), isTrue);

        final clearData = tester.getSemantics(find.byKey(_clearButtonKey));
        expect(clearData.label, contains('Wis zoekopdracht'));
        expect(clearData.getSemanticsData().hasAction(SemanticsAction.tap), isTrue);

        await tester.tap(find.byKey(_resultCardKey));
        await tester.pumpAndSettle();

        final backData = tester.getSemantics(find.byKey(_backButtonKey));
        expect(backData.getSemanticsData().hasAction(SemanticsAction.tap), isTrue);
      },
    );
  });

  group('color contrast', () {
    test('every entity badge foreground meets the 4.5:1 AA contrast minimum', () {
      for (final entry in NewsEntityBadgeColors.foregroundByType.entries) {
        final ratio = _contrastRatio(entry.value, NewsEntityBadgeColors.background);
        expect(
          ratio,
          greaterThanOrEqualTo(4.5),
          reason: '${entry.key} foreground contrast was $ratio',
        );
      }
    });
  });

  testWidgets(
    'the result count is exposed via a Semantics(liveRegion: true) widget whose text '
    'changes after every new search or chip selection',
    (tester) async {
      final source = _FakeNewsSource({
        '|': _unfiltered(),
        'kerk|': _byEntity(),
        '|Kerkweg': _emptySearch(),
      });
      await tester.pumpWidget(_wrap(DiscoverSection(source: source)));
      await tester.pumpAndSettle();

      await tester.enterText(find.byKey(_searchFieldKey), 'kerk');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pumpAndSettle();

      final countFinder = find.byKey(const Key('discover-result-count'));
      expect(tester.widget<Semantics>(countFinder).properties.liveRegion, isTrue);
      final firstAnnouncement = tester.getSemantics(countFinder).label;
      expect(firstAnnouncement, '1 resultaat gevonden.');

      await tester.tap(find.byKey(_entityChipKey));
      await tester.pumpAndSettle();

      final secondAnnouncement = tester.getSemantics(countFinder).label;
      expect(secondAnnouncement, '0 resultaten gevonden.');
      expect(secondAnnouncement, isNot(firstAnnouncement));
    },
  );

  test(
    'DiscoverSection only reads LatestNewsItem/NewsEntity/AggregatedNewsEntity fields, '
    'never record-intake, privacy-classification or external-verification data',
    () {
      final source = File('lib/news/discover_section.dart').readAsStringSync();
      expect(source.contains('recordintake'), isFalse);
      expect(source.contains('privacyclassification'), isFalse);
      expect(source.contains('externalverification'), isFalse);
      expect(source, contains("import 'latest_news.dart';"));
    },
  );

  testWidgets(
    'a result card and its detail view only render title, message, publishedAt, '
    'source and entities from LatestNewsItem/NewsEntity',
    (tester) async {
      final source = _sourceWithResults();
      await tester.pumpWidget(_wrap(DiscoverSection(source: source)));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(_entityChipKey));
      await tester.pumpAndSettle();

      expect(find.text(_newsItem.title), findsWidgets);
      expect(
        find.descendant(
          of: find.byKey(_resultCardKey),
          matching: find.textContaining(_newsItem.message.substring(0, 20)),
        ),
        findsOneWidget,
      );

      await tester.tap(find.byKey(_resultCardKey));
      await tester.pumpAndSettle();

      expect(find.text(_newsItem.message), findsOneWidget);
      expect(find.textContaining('01-08-2026'), findsOneWidget);
      expect(find.textContaining(_newsItem.source!), findsOneWidget);
    },
  );
}

double _relativeLuminance(Color color) {
  double channel(double value) =>
      value <= 0.03928 ? value / 12.92 : math.pow((value + 0.055) / 1.055, 2.4).toDouble();

  final r = channel(color.r);
  final g = channel(color.g);
  final b = channel(color.b);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

double _contrastRatio(Color a, Color b) {
  final lighter = math.max(_relativeLuminance(a), _relativeLuminance(b)) + 0.05;
  final darker = math.min(_relativeLuminance(a), _relativeLuminance(b)) + 0.05;
  return lighter / darker;
}

bool _hasPrimaryFocusWithin(Finder finder) {
  final focusContext = FocusManager.instance.primaryFocus?.context;
  if (focusContext == null) return false;
  return find
      .descendant(of: finder, matching: find.byWidget(focusContext.widget))
      .evaluate()
      .isNotEmpty;
}
