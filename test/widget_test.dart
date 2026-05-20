import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:content_studio/main.dart';

void main() {
  testWidgets('renders the philosophy card on launch', (tester) async {
    await tester.pumpWidget(const ContentStudioApp());
    await tester.pump();

    expect(find.text('Why build a custom studio at all?'), findsOneWidget);
    expect(find.text('Zero Friction'), findsOneWidget);
    expect(find.text('Brand Consistency'), findsOneWidget);
    expect(find.text('Infinite Scale'), findsOneWidget);
  });

  testWidgets('shows the Generate Video button', (tester) async {
    await tester.pumpWidget(const ContentStudioApp());
    await tester.pump();

    expect(find.text('Generate Video'), findsOneWidget);
    expect(find.byIcon(Icons.movie_creation_rounded), findsOneWidget);
  });
}
