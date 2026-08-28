import 'package:flutter/material.dart';

import 'person_query_widgets.dart';

/// Scherm `no-reliable-source`: getoond wanneer geen persoonsnaam herkend is.
/// Er wordt geen enkele Wikidata- of Open Archieven-aanroep gedaan. Volgt
/// qua structuur `hkh-sessiezoek-v19-08-geen-betrouwbare-bron-*`.
class NoReliableSourceScreen extends StatelessWidget {
  const NoReliableSourceScreen({
    required this.originalQuery,
    required this.onPickSuggestion,
    required this.onBackToStart,
    super.key,
  });

  static const suggestions = [
    'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?',
    'Wie waren de ouders van Nicolaas Jacobus Sinnige?',
  ];

  final String originalQuery;
  final ValueChanged<String> onPickSuggestion;
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
          label: 'Hiervoor vinden we geen betrouwbare bron',
          child: Text(
            'Hiervoor vinden we geen betrouwbare bron',
            style: textTheme.headlineSmall,
          ),
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
          'Deze vraag noemt geen herkenbare persoonsnaam. Daarom voeren we de '
          'persoonszoekroute niet uit.',
        ),
        const SizedBox(height: 16),
        Card(
          child: ListTile(
            leading: const Icon(Icons.close),
            title: const Text('Open Archieven'),
            subtitle: const Text('Niet uitgevoerd · persoonsnaam ontbreekt'),
          ),
        ),
        const SizedBox(height: 24),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Probeer een vraag binnen de dekking',
                  style: textTheme.titleMedium,
                ),
                const SizedBox(height: 12),
                for (final suggestion in suggestions)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: TextButton(
                        onPressed: () => onPickSuggestion(suggestion),
                        style: personQueryFocusedButtonStyle(
                          Theme.of(context).colorScheme.primary,
                        ),
                        child: Text(suggestion, textAlign: TextAlign.left),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
