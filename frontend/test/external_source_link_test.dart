import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/external_source_link.dart';

void main() {
  testWidgets('Tab and Enter open the actual external source URL', (
    tester,
  ) async {
    final calls = <MethodCall>[];
    const channel = MethodChannel('plugins.flutter.io/url_launcher');
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(channel, (
      call,
    ) async {
      calls.add(call);
      return true;
    });
    addTearDown(
      () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
        channel,
        null,
      ),
    );
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: ExternalSourceLink(
            label: 'Bekijk bron',
            url: 'https://www.europeana.eu/item/123/abc',
          ),
        ),
      ),
    );
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pumpAndSettle();
    expect(calls.single.method, 'launch');
    expect(
      calls.single.arguments['url'],
      'https://www.europeana.eu/item/123/abc',
    );
    expect(calls.single.arguments['useWebView'], isFalse);
  });

  testWidgets('invalid and executable URLs are never actionable', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: ExternalSourceLink(
            label: 'Bekijk bron',
            url: 'javascript:alert(1)',
          ),
        ),
      ),
    );
    expect(find.byType(TextButton), findsNothing);
  });
}
