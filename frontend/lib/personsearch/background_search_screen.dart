import 'package:flutter/material.dart';

import '../personquery/person_query_widgets.dart';
import 'person_search_models.dart';

/// Scherm `background-search`: getoond zodra een job na het synchrone budget
/// van 2000ms niet terminaal is (`QUEUED`/`RUNNING`). Toont de oorspronkelijke
/// vraag, het starttijdstip en aparte voortgang per bron; de bezoeker kan
/// intussen een andere vraag stellen zonder de lopende job te onderbreken, of
/// de job expliciet stoppen.
class BackgroundSearchScreen extends StatelessWidget {
  const BackgroundSearchScreen({
    required this.originalQuery,
    required this.startedAt,
    required this.status,
    required this.openArchievenStatus,
    required this.wikidataStatus,
    required this.onAskAnotherQuestion,
    required this.onStop,
    super.key,
  });

  final String originalQuery;
  final DateTime startedAt;
  final PersonSearchStatus status;
  final PersonSearchSourceConsultationStatus openArchievenStatus;
  final PersonSearchSourceConsultationStatus wikidataStatus;
  final VoidCallback onAskAnotherQuestion;
  final VoidCallback onStop;

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

  Widget _buildDesktop(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [_buildContent(context)],
    );
  }

  Widget _buildMobile(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [_buildContent(context)],
    );
  }

  Widget _buildContent(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final statusLabel = status == PersonSearchStatus.queued
        ? 'In wachtrij'
        : 'Bezig';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('ZOEKEN OP DE ACHTERGROND', style: textTheme.labelLarge),
        const SizedBox(height: 8),
        Text('Je opdracht loopt verder', style: textTheme.headlineMedium),
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
              'Je hoeft niet te wachten. $statusLabel sinds ${startedAt.toIso8601String()}',
          child: Text(
            'Je hoeft niet te wachten. Je kunt een andere vraag stellen; deze '
            'opdracht blijft in je huidige sessie staan.',
          ),
        ),
        const SizedBox(height: 20),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        'Bronnen vergelijken · $statusLabel',
                        style: textTheme.titleMedium,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  'Gestart ${startedAt.toIso8601String()}',
                  style: textTheme.bodySmall,
                ),
                const SizedBox(height: 16),
                _SourceProgressRow(
                  key: const Key('background-search-open-archieven'),
                  label: 'Open Archieven',
                  status: openArchievenStatus,
                ),
                const SizedBox(height: 12),
                _SourceProgressRow(
                  key: const Key('background-search-wikidata'),
                  label: 'Wikidata · Context',
                  status: wikidataStatus,
                ),
                const SizedBox(height: 20),
                Wrap(
                  spacing: 12,
                  runSpacing: 12,
                  children: [
                    FilledButton(
                      key: const Key('background-search-ask-another'),
                      onPressed: onAskAnotherQuestion,
                      style: personQueryFocusedButtonStyle(
                        Theme.of(context).colorScheme.onPrimary,
                      ),
                      child: const Text('Stel intussen een andere vraag'),
                    ),
                    OutlinedButton(
                      key: const Key('background-search-stop'),
                      onPressed: onStop,
                      style: personQueryFocusedButtonStyle(
                        Theme.of(context).colorScheme.primary,
                      ),
                      child: const Text('Stop opdracht'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// Toont de consultatiestatus van één bron met zowel een vorm (icoon) als
/// tekst, zodat de betekenis nooit uitsluitend op kleur berust.
class _SourceProgressRow extends StatelessWidget {
  const _SourceProgressRow({
    required this.label,
    required this.status,
    super.key,
  });

  final String label;
  final PersonSearchSourceConsultationStatus status;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final (icon, statusText) = switch (status) {
      PersonSearchSourceConsultationStatus.notStarted => (
        Icons.circle_outlined,
        'Nog niet gestart',
      ),
      PersonSearchSourceConsultationStatus.inProgress => (
        Icons.autorenew,
        'Bezig',
      ),
      PersonSearchSourceConsultationStatus.succeeded => (
        Icons.check_circle,
        'Geslaagd',
      ),
      PersonSearchSourceConsultationStatus.failed => (Icons.error, 'Mislukt'),
    };
    return Semantics(
      label: '$label: $statusText',
      child: ExcludeSemantics(
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, size: 20),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label, style: textTheme.titleSmall),
                  Text(statusText, style: textTheme.bodySmall),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
