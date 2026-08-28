import 'package:flutter/material.dart';

import '../personquery/person_query_widgets.dart';
import 'person_search_models.dart';

/// Scherm `source-outage`: getoond wanneer de vereiste Records/Search- of
/// Records/Show-aanroep faalde. Open Archieven wordt exact aangeduid als
/// 'tijdelijk niet geraadpleegd'; er verschijnt geen enkele archiefbewering,
/// ook niet wanneer Wikidata wel bereikbaar was (uitsluitend onder 'Context').
class SourceOutageScreen extends StatelessWidget {
  const SourceOutageScreen({
    required this.originalQuery,
    required this.wikidataContext,
    required this.onBackToStart,
    super.key,
  });

  final String originalQuery;
  final PersonSearchWikidataContext? wikidataContext;
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
        onPressed: onBackToStart,
        style: personQueryFocusedButtonStyle(
          Theme.of(context).colorScheme.primary,
        ),
        child: const Text('Nieuwe vraag stellen'),
      ),
    );
  }

  Widget _buildContent(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('BRONUITVAL', style: textTheme.labelLarge),
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
          label: 'Open Archieven is tijdelijk niet geraadpleegd',
          child: Text(
            'Open Archieven is tijdelijk niet geraadpleegd',
            style: textTheme.headlineSmall,
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          'Er verschijnt hierdoor geen enkele archiefbewering. Probeer het '
          'straks opnieuw.',
        ),
        const SizedBox(height: 16),
        Card(
          child: ListTile(
            leading: const Icon(Icons.cloud_off),
            title: const Text('Open Archieven'),
            subtitle: const Text('Tijdelijk niet geraadpleegd'),
          ),
        ),
        if (wikidataContext != null) ...[
          const SizedBox(height: 20),
          Container(
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
                Text(wikidataContext!.label),
                if (wikidataContext!.description != null) ...[
                  const SizedBox(height: 4),
                  Text(wikidataContext!.description!),
                ],
              ],
            ),
          ),
        ],
      ],
    );
  }
}
