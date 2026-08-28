import 'package:flutter/material.dart';

import '../personquery/person_query_widgets.dart';

/// Scherm `live-search`: getoond zodra de job is ingediend en de live
/// Records/Search-/Records/Show-aanroepen (en de Wikidata-contextaanroep)
/// binnen het request lopen. Geen `background-search`/`search-ready` als
/// verplichte tussenstap: dit scherm verdwijnt zodra het request terugkomt.
class LiveSearchScreen extends StatelessWidget {
  const LiveSearchScreen({
    required this.originalQuery,
    required this.stillRunning,
    required this.onBackToStart,
    super.key,
  });

  final String originalQuery;

  /// `true` wanneer het request na de 2000ms-deadline is teruggekomen zonder
  /// terminale uitkomst; de job loopt in dat geval onafhankelijk door.
  final bool stillRunning;
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
    final label = stillRunning
        ? 'Dit duurt langer dan verwacht; de opdracht loopt door'
        : 'Bezig met zoeken in Open Archieven';
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('LIVE ZOEKOPDRACHT', style: textTheme.labelLarge),
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
          label: label,
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
              const SizedBox(width: 12),
              Expanded(child: Text('$label…')),
            ],
          ),
        ),
        const SizedBox(height: 16),
        if (stillRunning)
          const Text(
            'Deze opdracht is nog niet klaar. Je hoeft niet te wachten; een '
            'vervolg hierop hoort bij een latere story.',
          )
        else
          const Text(
            'We raadplegen Open Archieven live, met Wikidata als aanvullende '
            'context.',
          ),
      ],
    );
  }
}
