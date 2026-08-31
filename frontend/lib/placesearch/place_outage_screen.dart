import 'package:flutter/material.dart';

import '../personquery/person_query_widgets.dart';

/// Scherm `place-outage`: getoond wanneer de Wikidata-raadpleging faalde
/// (niet-2xx, timeout, ongeldige JSON, ontbrekend verplicht veld, of het
/// 2000ms-budget overschreden). Er verschijnt geen enkele claim; Wikimedia
/// Commons wordt nooit apart bevraagd omdat het afhankelijk is van Wikidata.
class PlaceOutageScreen extends StatelessWidget {
  const PlaceOutageScreen({
    required this.originalQuery,
    required this.onRetry,
    required this.onBackToStart,
    super.key,
  });

  static const statusLabel = 'Wikidata is tijdelijk niet geraadpleegd';

  final String originalQuery;
  final VoidCallback onRetry;
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
        Expanded(flex: 2, child: _buildActions(context)),
      ],
    );
  }

  Widget _buildMobile(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildContent(context),
        const SizedBox(height: 24),
        _buildActions(context),
      ],
    );
  }

  Widget _buildActions(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Align(
          alignment: Alignment.topLeft,
          child: FilledButton(
            key: const Key('place-outage-retry'),
            onPressed: onRetry,
            style: personQueryFocusedButtonStyle(
              Theme.of(context).colorScheme.onPrimary,
            ),
            child: const Text('Opnieuw proberen'),
          ),
        ),
        const SizedBox(height: 12),
        Align(
          alignment: Alignment.topLeft,
          child: OutlinedButton(
            key: const Key('place-outage-back-to-start'),
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
          label: statusLabel,
          child: Text(statusLabel, style: textTheme.headlineSmall),
        ),
        const SizedBox(height: 12),
        const Text(
          'Er verschijnt hierdoor geen enkele bewering. Probeer het straks '
          'opnieuw.',
        ),
        const SizedBox(height: 16),
        Card(
          child: ListTile(
            leading: const Icon(Icons.cloud_off),
            title: const Text('Wikidata'),
            subtitle: const Text('Tijdelijk niet geraadpleegd'),
          ),
        ),
        const SizedBox(height: 8),
        Card(
          child: ListTile(
            leading: const Icon(Icons.image_not_supported_outlined),
            title: const Text('Wikimedia Commons'),
            subtitle: const Text('Niet uitgevoerd · afhankelijk van Wikidata'),
          ),
        ),
      ],
    );
  }
}
