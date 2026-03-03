import 'package:flutter_test/flutter_test.dart';

import 'package:connectias/main.dart';

void main() {
  testWidgets('App smoke test', (WidgetTester tester) async {
    await tester.pumpWidget(const ConnectiasApp());

    expect(find.text('Connectias'), findsOneWidget);
    expect(find.text('Storage & Media'), findsOneWidget);
  });
}
