import 'package:flutter/material.dart';

import '../personquery/person_query_widgets.dart';
import 'person_search_models.dart';

/// Scherm `search-ready`: getoond zodra een achtergrondjob `READY` bereikt.
/// Toont het voltooiingstijdstip en de daadwerkelijk geraadpleegde bronnen,
/// met precies één actie die het bijbehorende antwoord opent.
class SearchReadyScreen extends StatelessWidget {
  const SearchReadyScreen({
    required this.originalQuery,
    required this.completedAt,
    required this.openArchievenStatus,
    required this.wikidataStatus,
    required this.onViewAnswer,
    super.key,
  });

  final String originalQuery;
  final DateTime completedAt;
  final PersonSearchSourceConsultationStatus openArchievenStatus;
  final PersonSearchSourceConsultationStatus wikidataStatus;
  final VoidCallback onViewAnswer;

  List<String> get _consultedSources => [
    if (openArchievenStatus == PersonSearchSourceConsultationStatus.succeeded)
      'Open Archieven',
    if (wikidataStatus == PersonSearchSourceConsultationStatus.succeeded)
      'Wikidata · Context',
  ];

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final isMobile = constraints.maxWidth < kPersonQueryMobileBreakpoint;
        return SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 900),
              child: isMobile ? _buildMobile(context) : _buildDesktop(context),
            ),
          ),
        );
      },
    );
  }

  Widget _buildDesktop(BuildContext context) => _buildContent(context);

  Widget _buildMobile(BuildContext context) => _buildContent(context);

  Widget _buildContent(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('ZOEKOPDRACHT GEREED', style: textTheme.labelLarge),
        const SizedBox(height: 8),
        Text('Je antwoord staat klaar', style: textTheme.headlineMedium),
        const SizedBox(height: 16),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            border: Border(
              left: BorderSide(
                color: Theme.of(context).colorScheme.tertiary,
                width: 4,
              ),
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Je vraag', style: textTheme.labelLarge),
              const SizedBox(height: 4),
              Text(originalQuery),
            ],
          ),
        ),
        const SizedBox(height: 16),
        PersonQueryStatusMessage(
          label:
              'Onderbouwde uitkomst gevonden. Voltooid om '
              '${completedAt.toIso8601String()}'
              '${_consultedSources.isEmpty ? '' : ', ${_consultedSources.join(' en ')} geraadpleegd'}.',
          child: Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Onderbouwde uitkomst gevonden',
                    style: textTheme.titleMedium,
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Voltooid om ${completedAt.toIso8601String()}'
                    '${_consultedSources.isEmpty ? '' : ' · ${_consultedSources.join(' en ')} geraadpleegd'}',
                    style: textTheme.bodySmall,
                  ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 20),
        Align(
          alignment: Alignment.centerLeft,
          child: FilledButton(
            key: const Key('search-ready-view-answer'),
            onPressed: onViewAnswer,
            style: personQueryFocusedButtonStyle(
              Theme.of(context).colorScheme.onPrimary,
            ),
            child: const Text('Bekijk het antwoord'),
          ),
        ),
      ],
    );
  }
}
