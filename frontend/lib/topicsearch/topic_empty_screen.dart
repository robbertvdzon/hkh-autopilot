import 'package:flutter/material.dart';

import '../personquery/person_query_widgets.dart';

/// Scherm `topic-empty`: getoond bij 0 geldige Europeana-records (wel
/// bereikbaar). Toont exact "Hiervoor vinden we geen betrouwbare bron", de
/// raadplegingsstatus per bron en, waar mogelijk, concrete
/// verfijningsvoorstellen.
class TopicEmptyScreen extends StatelessWidget {
  const TopicEmptyScreen({
    required this.originalQuery,
    required this.refinementSuggestions,
    required this.onBackToStart,
    super.key,
  });

  static const statusLabel = 'Hiervoor vinden we geen betrouwbare bron';

  final String originalQuery;
  final List<String> refinementSuggestions;
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
        Expanded(flex: 3, child: _buildContent(context)),
        const SizedBox(width: 24),
        Expanded(flex: 2, child: _buildBackAction(context)),
      ],
    );
  }

  Widget _buildMobile(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildContent(context),
        const SizedBox(height: 24),
        _buildBackAction(context),
      ],
    );
  }

  Widget _buildBackAction(BuildContext context) {
    return Align(
      alignment: Alignment.topLeft,
      child: OutlinedButton(
        key: const Key('topic-empty-back-to-start'),
        onPressed: onBackToStart,
        style: personQueryFocusedButtonStyle(
          Theme.of(context).colorScheme.primary,
        ),
        child: const Text('Terug naar het startscherm'),
      ),
    );
  }

  Widget _buildContent(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('GEEN GEDRAGEN UITKOMST', style: textTheme.labelLarge),
        const SizedBox(height: 8),
        PersonQueryStatusMessage(
          label: statusLabel,
          child: Text(statusLabel, style: textTheme.headlineSmall),
        ),
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
        const Text(
          'Er is geen enkel betrouwbaar Europeana-record gevonden voor deze '
          'zoekterm in combinatie met Heemskerk.',
        ),
        const SizedBox(height: 16),
        Card(
          child: ListTile(
            leading: const Icon(Icons.close),
            title: const Text('Europeana'),
            subtitle: const Text('Live geraadpleegd · nul geldige records'),
          ),
        ),
        const SizedBox(height: 8),
        Card(
          child: ListTile(
            leading: const Icon(Icons.info_outline),
            title: const Text('Wikidata'),
            subtitle: const Text('Niet geraadpleegd · geen geldige records'),
          ),
        ),
        if (refinementSuggestions.isNotEmpty) ...[
          const SizedBox(height: 24),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Verfijningsvoorstellen', style: textTheme.titleMedium),
                  const SizedBox(height: 12),
                  for (final suggestion in refinementSuggestions)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Icon(Icons.lightbulb_outline, size: 18),
                          const SizedBox(width: 8),
                          Expanded(child: Text(suggestion)),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }
}
