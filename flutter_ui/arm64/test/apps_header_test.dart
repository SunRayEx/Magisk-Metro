import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:magiskube/screens/secondary_pages.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:magiskube/l10n/app_localizations.dart';
import 'package:magiskube/providers/dashboard_providers.dart';
import 'package:magiskube/models/models.dart';

class AppsNotifierMock extends AppsNotifier {
  AppsNotifierMock(super.ref) : super() {
    state = [
      const AppInfo(name: 'App 1', packageName: 'com.example.app1', isActive: true, hasRootAccess: true),
    ];
  }
}

void main() {
  testWidgets('Apps Page Header UI Test', (WidgetTester tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          appsProvider.overrideWith((ref) => AppsNotifierMock(ref)),
        ],
        child: const MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: AppsPage(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // Verify Title exists
    expect(find.text('Apps'), findsOneWidget);

    // Verify Semantics button exists and has right label
    final semanticsFinder = find.bySemanticsLabel('排序方式，按钮');
    expect(semanticsFinder, findsOneWidget);

    // Verify button size is at least 48x48 (we set width and height to 48 in code)
    final inkWellFinder = find.descendant(
      of: semanticsFinder,
      matching: find.byType(InkWell),
    );
    expect(inkWellFinder, findsOneWidget);
    
    final containerFinder = find.descendant(
      of: inkWellFinder,
      matching: find.byType(Container),
    );
    
    final containerSize = tester.getSize(containerFinder);
    expect(containerSize.width, greaterThanOrEqualTo(44));
    expect(containerSize.height, greaterThanOrEqualTo(44));

    // Verify the icon changes when tapped
    expect(find.byIcon(Icons.security), findsOneWidget);
    expect(find.byIcon(Icons.apps), findsNothing);

    await tester.tap(inkWellFinder);
    await tester.pumpAndSettle(); // Wait for animation

    expect(find.byIcon(Icons.security), findsNothing);
    expect(find.byIcon(Icons.apps), findsOneWidget);
  });
}
