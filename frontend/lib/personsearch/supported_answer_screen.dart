import 'package:flutter/material.dart';

import '../personquery/person_query_widgets.dart';
import 'person_search_models.dart';

/// Scherm `supported-answer`: toont het door Open Archieven onderbouwde
/// antwoord, met per feitelijke zin een genummerde bronmarkering, de
/// bronitems zelf, optionele Wikidata-'Context' en tot twee vervolgsporen.
class SupportedAnswerScreen extends StatelessWidget {
  const SupportedAnswerScreen({
    required this.originalQuery,
    required this.answer,
    required this.wikidataContext,
    required this.onFollowConnection,
    required this.onBackToStart,
    super.key,
  });

  final String originalQuery;
  final PersonSearchAnswer answer;
  final PersonSearchWikidataContext? wikidataContext;
  final ValueChanged<PersonSearchConnectionOption> onFollowConnection;
  final VoidCallback onBackToStart;

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
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(flex: 3, child: _buildAnswerColumn(context)),
        const SizedBox(width: 24),
        Expanded(flex: 2, child: _buildSourcesColumn(context)),
      ],
    );
  }

  Widget _buildMobile(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildAnswerColumn(context),
        const SizedBox(height: 24),
        _buildSourcesColumn(context),
      ],
    );
  }

  Widget _buildAnswerColumn(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('ONDERBOUWD ANTWOORD', style: textTheme.labelLarge),
        const SizedBox(height: 8),
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
          label: 'Antwoord gevonden op basis van Open Archieven',
          child: Text(
            'Antwoord gevonden op basis van Open Archieven',
            style: textTheme.headlineSmall,
          ),
        ),
        const SizedBox(height: 12),
        for (final sentence in answer.sentences)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text.rich(
              TextSpan(
                children: [
                  TextSpan(text: sentence.text),
                  TextSpan(
                    text:
                        ' ${sentence.sourceNumbers.map((n) => '[$n]').join()}',
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.primary,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            ),
          ),
        const SizedBox(height: 8),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Text(answer.disclaimer, style: textTheme.bodyMedium),
        ),
        if (answer.connections.isNotEmpty) ...[
          const SizedBox(height: 20),
          Text('Vervolgspoor volgen', style: textTheme.titleMedium),
          const SizedBox(height: 8),
          const Text(
            'Een bronrol is geen volledig levensverhaal van die persoon.',
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              for (final connection in answer.connections)
                OutlinedButton(
                  key: ValueKey(
                    'follow-connection-${connection.role}-${connection.personName}',
                  ),
                  onPressed: () => onFollowConnection(connection),
                  style: personQueryFocusedButtonStyle(
                    Theme.of(context).colorScheme.primary,
                  ),
                  child: Text(
                    'Volg ${connection.role.toLowerCase()}: ${connection.personName}',
                  ),
                ),
            ],
          ),
        ],
        if (wikidataContext != null) ...[
          const SizedBox(height: 20),
          _ContextSection(wikidataContext: wikidataContext!),
        ],
        const SizedBox(height: 20),
        Align(
          alignment: Alignment.centerLeft,
          child: OutlinedButton(
            onPressed: onBackToStart,
            style: personQueryFocusedButtonStyle(
              Theme.of(context).colorScheme.primary,
            ),
            child: const Text('Nieuwe vraag stellen'),
          ),
        ),
      ],
    );
  }

  Widget _buildSourcesColumn(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('BRONNEN', style: textTheme.labelLarge),
        const SizedBox(height: 12),
        for (final source in answer.sources)
          Card(
            key: ValueKey('source-${source.number}'),
            margin: const EdgeInsets.only(bottom: 12),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '[${source.number}] ${source.institution}',
                    style: textTheme.titleSmall,
                  ),
                  const SizedBox(height: 4),
                  Text(source.sourceType),
                  Text(
                    [
                      if (source.archiveNumber != null)
                        'archief ${source.archiveNumber}',
                      if (source.registerNumber != null)
                        'register ${source.registerNumber}',
                      if (source.deedNumber != null)
                        'akte ${source.deedNumber}',
                      'record ${source.recordNumber}',
                    ].join(' · '),
                  ),
                  const SizedBox(height: 8),
                  Link(
                    label: 'Bekijk op Open Archieven',
                    url: source.openArchivesLink,
                  ),
                  if (source.digitalOriginalLink != null)
                    Link(
                      label: 'Bekijk digitaal origineel',
                      url: source.digitalOriginalLink!,
                    ),
                  const SizedBox(height: 4),
                  Text(
                    'Live opgehaald op ${source.checkedAt.toIso8601String()}',
                    style: textTheme.bodySmall,
                  ),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

class _ContextSection extends StatelessWidget {
  const _ContextSection({required this.wikidataContext});

  final PersonSearchWikidataContext wikidataContext;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Context', style: textTheme.titleMedium),
          const SizedBox(height: 8),
          Text(wikidataContext.label),
          if (wikidataContext.description != null) ...[
            const SizedBox(height: 4),
            Text(wikidataContext.description!),
          ],
        ],
      ),
    );
  }
}

/// Actielink die een externe bron beschrijft als een nieuw tabblad openend.
class Link extends StatelessWidget {
  const Link({required this.label, required this.url, super.key});

  final String label;
  final String url;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      link: true,
      label: '$label (opent Open Archieven in een nieuw tabblad)',
      child: Text(
        label,
        style: TextStyle(
          color: Theme.of(context).colorScheme.primary,
          decoration: TextDecoration.underline,
        ),
      ),
    );
  }
}
