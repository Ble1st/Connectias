import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:connectias/main.dart';

void main() {
  testWidgets('Home screen displays "Hallo"', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(const MyApp());

    // Verify that "Hallo" text is displayed
    expect(find.text('Hallo'), findsOneWidget);
  });

  testWidgets('Drawer can be opened', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(const MyApp());

    // Find the menu icon and tap it
    final menuIcon = find.byIcon(Icons.menu);
    expect(menuIcon, findsOneWidget);
    await tester.tap(menuIcon);
    await tester.pumpAndSettle();

    // Verify drawer is open by checking for drawer items
    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Log Viewer'), findsOneWidget);
  });
}
