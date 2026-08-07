import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/backend/backend_status.dart';
import 'package:hkh_app/main.dart';
import 'package:hkh_app/news/latest_news.dart';

const _backendStatus = BackendStatus(
  application: 'hkh',
  version: 'test',
  commit: 'abc123',
);

final _news = LatestNewsItem(
  id: 1,
  title: 'Nieuwe historische ontdekking',
  message: 'Een bijzonder verhaal uit Heemskerk.',
  publishedAt: DateTime.utc(2026, 8, 7),
);

class _SuccessfulStatusSource implements BackendStatusSource {
  @override
  Future<BackendStatus> load() async => _backendStatus;
}

class _DeferredStatusSource implements BackendStatusSource {
  final result = Completer<BackendStatus>();

  @override
  Future<BackendStatus> load() => result.future;
}

class _RetryStatusSource implements BackendStatusSource {
  final retryResult = Completer<BackendStatus>();
  int calls = 0;

  @override
  Future<BackendStatus> load() {
    calls++;
    if (calls == 1) return Future.error(StateError('offline'));
    return retryResult.future;
  }
}

class _NewsSource implements LatestNewsSource {
  _NewsSource(this.items, {this.error = false});

  final List<LatestNewsItem> items;
  final bool error;

  @override
  Future<List<LatestNewsItem>> loadLatestNews() async {
    if (error) throw StateError('offline');
    return items;
  }
}

class _DeferredNewsSource implements LatestNewsSource {
  final result = Completer<List<LatestNewsItem>>();

  @override
  Future<List<LatestNewsItem>> loadLatestNews() => result.future;
}

class _RetryNewsSource implements LatestNewsSource {
  final retryResult = Completer<List<LatestNewsItem>>();
  int calls = 0;

  @override
  Future<List<LatestNewsItem>> loadLatestNews() {
    calls++;
    if (calls == 1) return Future.error(StateError('offline'));
    return retryResult.future;
  }
}

void main() {
  testWidgets('announces each loading and successful status exactly once', (
    tester,
  ) async {
    final statusSource = _DeferredStatusSource();
    final newsSource = _DeferredNewsSource();

    await tester.pumpWidget(
      HkhApp(statusSource: statusSource, newsSource: newsSource),
    );
    _expectStatuses(tester, ['De historische omgeving wordt voorbereid.']);

    statusSource.result.complete(_backendStatus);
    await tester.pump();
    await tester.pump();
    _expectStatuses(tester, [
      'Service beschikbaar.',
      'Laatste nieuws wordt geladen.',
    ]);

    newsSource.result.complete([_news]);
    await tester.pumpAndSettle();
    _expectStatuses(tester, [
      'Service beschikbaar.',
      'Laatste nieuws geladen.',
    ]);
  });

  testWidgets('announces the service error exactly once without focus', (
    tester,
  ) async {
    await tester.pumpWidget(
      HkhApp(
        statusSource: _RetryStatusSource(),
        newsSource: _NewsSource(const []),
      ),
    );
    await tester.pumpAndSettle();

    _expectStatuses(tester, ['De HKH-service is niet bereikbaar.']);
    _expectSemanticsBeforeRetry(
      tester,
      statusLabel: 'De HKH-service is niet bereikbaar.',
      retryKey: const Key('service-retry'),
    );
  });

  testWidgets('announces empty latest news exactly once', (tester) async {
    await tester.pumpWidget(
      HkhApp(
        statusSource: _SuccessfulStatusSource(),
        newsSource: _NewsSource(const []),
      ),
    );
    await tester.pumpAndSettle();

    _expectStatuses(tester, [
      'Service beschikbaar.',
      'Er zijn nog geen nieuwsberichten.',
    ]);
  });

  testWidgets('announces the latest-news error exactly once', (tester) async {
    await tester.pumpWidget(
      HkhApp(
        statusSource: _SuccessfulStatusSource(),
        newsSource: _NewsSource(const [], error: true),
      ),
    );
    await tester.pumpAndSettle();

    _expectStatuses(tester, [
      'Service beschikbaar.',
      'Het laatste nieuws kon niet worden geladen.',
    ]);
    _expectSemanticsBeforeRetry(
      tester,
      statusLabel: 'Het laatste nieuws kon niet worden geladen.',
      retryKey: const Key('news-retry'),
    );
  });

  for (final keyCase in [
    (name: 'Enter', key: LogicalKeyboardKey.enter),
    (name: 'space', key: LogicalKeyboardKey.space),
  ]) {
    testWidgets('service retry works with ${keyCase.name}', (tester) async {
      final statusSource = _RetryStatusSource();
      const retryKey = Key('service-retry');

      await tester.pumpWidget(
        HkhApp(statusSource: statusSource, newsSource: _NewsSource(const [])),
      );
      await tester.pumpAndSettle();

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_hasPrimaryFocusWithin(find.byKey(retryKey)), isTrue);
      _expectVisibleFocusBorder<FilledButton>(tester, retryKey);

      await tester.sendKeyEvent(keyCase.key);
      await tester.pump();
      expect(statusSource.calls, 2);
      _expectStatuses(tester, ['De historische omgeving wordt voorbereid.']);

      statusSource.retryResult.complete(_backendStatus);
      await tester.pumpAndSettle();
      _expectStatuses(tester, [
        'Service beschikbaar.',
        'Er zijn nog geen nieuwsberichten.',
      ]);
    });

    testWidgets('latest-news retry works with ${keyCase.name}', (tester) async {
      final newsSource = _RetryNewsSource();
      const retryKey = Key('news-retry');

      await tester.pumpWidget(
        HkhApp(statusSource: _SuccessfulStatusSource(), newsSource: newsSource),
      );
      await tester.pumpAndSettle();

      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(
        _hasPrimaryFocusWithin(
          find.widgetWithText(OutlinedButton, 'Lees onze productvisie'),
        ),
        isTrue,
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      expect(_hasPrimaryFocusWithin(find.byKey(retryKey)), isTrue);
      _expectVisibleFocusBorder<OutlinedButton>(tester, retryKey);

      await tester.sendKeyEvent(keyCase.key);
      await tester.pump();
      expect(newsSource.calls, 2);
      _expectStatuses(tester, [
        'Service beschikbaar.',
        'Laatste nieuws wordt geladen.',
      ]);

      newsSource.retryResult.complete([_news]);
      await tester.pumpAndSettle();
      _expectStatuses(tester, [
        'Service beschikbaar.',
        'Laatste nieuws geladen.',
      ]);
    });
  }

  testWidgets('keeps existing homepage content and product-vision route', (
    tester,
  ) async {
    await tester.pumpWidget(
      HkhApp(
        statusSource: _SuccessfulStatusSource(),
        newsSource: _NewsSource([_news]),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.textContaining(
        'Ontdek de geschiedenis van Heemskerk vanuit een vraag',
      ),
      findsOneWidget,
    );
    expect(find.text('hkh test · abc123'), findsOneWidget);
    expect(find.text('Laatste nieuws'), findsOneWidget);
    expect(find.text('Nieuwe historische ontdekking'), findsOneWidget);
    expect(find.text('Een bijzonder verhaal uit Heemskerk.'), findsOneWidget);

    await tester.tap(find.text('Lees onze productvisie'));
    await tester.pumpAndSettle();

    expect(find.text('Productvisie'), findsWidgets);
    expect(find.text('Productprincipes'), findsOneWidget);
    expect(find.text('Verbonden'), findsOneWidget);
  });
}

List<SemanticsNode> _allSemanticsNodes(WidgetTester tester) {
  return find.semantics
      .byPredicate((_) => true, view: tester.view)
      .evaluate()
      .toList(growable: false);
}

void _expectStatuses(WidgetTester tester, List<String> expectedLabels) {
  final allNodes = _allSemanticsNodes(tester);
  final statusNodes = allNodes
      .where((node) => node.getSemanticsData().role == SemanticsRole.status)
      .toList(growable: false);

  expect(
    statusNodes.map((node) => node.label),
    unorderedEquals(expectedLabels),
  );
  for (final label in expectedLabels) {
    expect(
      allNodes.where((node) => node.label == label),
      hasLength(1),
      reason: 'De status “$label” hoort exact één semantische kopie te hebben.',
    );
  }
  for (final node in statusNodes) {
    final data = node.getSemanticsData();
    // hasFlag remains compatible with both Flutter 3.35 and the newer
    // tri-state SemanticsFlags API.
    // ignore: deprecated_member_use
    expect(node.hasFlag(SemanticsFlag.isFocusable), isFalse);
    // ignore: deprecated_member_use
    expect(node.hasFlag(SemanticsFlag.isFocused), isFalse);
    expect(data.hasAction(SemanticsAction.focus), isFalse);
    expect(data.hasAction(SemanticsAction.didGainAccessibilityFocus), isFalse);
  }
}

void _expectSemanticsBeforeRetry(
  WidgetTester tester, {
  required String statusLabel,
  required Key retryKey,
}) {
  final nodes = _allSemanticsNodes(tester);
  final statusIndex = nodes.indexWhere((node) => node.label == statusLabel);
  final retryIndex = nodes.indexWhere(
    (node) =>
        node.label == 'Opnieuw proberen' &&
        node.getSemanticsData().hasAction(SemanticsAction.tap),
  );

  expect(statusIndex, greaterThanOrEqualTo(0));
  expect(retryIndex, greaterThan(statusIndex));
  expect(find.byKey(retryKey), findsOneWidget);
}

bool _hasPrimaryFocusWithin(Finder finder) {
  final focusContext = FocusManager.instance.primaryFocus?.context;
  if (focusContext == null) return false;
  return find
      .descendant(of: finder, matching: find.byWidget(focusContext.widget))
      .evaluate()
      .isNotEmpty;
}

void _expectVisibleFocusBorder<T extends ButtonStyleButton>(
  WidgetTester tester,
  Key key,
) {
  final button = tester.widget<T>(find.byKey(key));
  final focusedBorder = button.style?.side?.resolve({WidgetState.focused});
  expect(focusedBorder, isNotNull);
  expect(focusedBorder!.width, greaterThanOrEqualTo(3));
}
