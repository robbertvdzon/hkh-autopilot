import 'package:flutter/material.dart';

import '../personquery/person_query_widgets.dart';
import 'person_search_models.dart';

/// Scherm `followed-connection`: getoond na het volgen van een rol/persoon
/// uit hetzelfde gevalideerde Show-record. Houdt de oorspronkelijke vraag en
/// het gekozen vervolgspoor zichtbaar en vermeldt expliciet dat een bronrol
/// geen volledig levensverhaal van die persoon is. Geen nieuwe externe
/// aanroep: alle gegevens komen al uit het reeds gevalideerde Show-record.
class FollowedConnectionScreen extends StatelessWidget {
  const FollowedConnectionScreen({
    required this.originalQuery,
    required this.connection,
    required this.onBackToAnswer,
    required this.onBackToStart,
    super.key,
  });

  final String originalQuery;
  final PersonSearchConnectionOption connection;
  final VoidCallback onBackToAnswer;
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
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        OutlinedButton(
          onPressed: onBackToAnswer,
          style: personQueryFocusedButtonStyle(
            Theme.of(context).colorScheme.primary,
          ),
          child: const Text('Terug naar het antwoord'),
        ),
        const SizedBox(height: 12),
        OutlinedButton(
          onPressed: onBackToStart,
          style: personQueryFocusedButtonStyle(
            Theme.of(context).colorScheme.primary,
          ),
          child: const Text('Nieuwe vraag stellen'),
        ),
      ],
    );
  }

  Widget _buildContent(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('VERVOLGSPOOR', style: textTheme.labelLarge),
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
              Text('Oorspronkelijke vraag', style: textTheme.labelLarge),
              const SizedBox(height: 4),
              Text(originalQuery),
              const SizedBox(height: 12),
              Text('Gevolgd spoor', style: textTheme.labelLarge),
              const SizedBox(height: 4),
              Text('${connection.role}: ${connection.personName}'),
            ],
          ),
        ),
        const SizedBox(height: 16),
        PersonQueryStatusMessage(
          label: '${connection.personName} als ${connection.role.toLowerCase()}',
          child: Text(
            '${connection.personName} als ${connection.role.toLowerCase()}',
            style: textTheme.headlineSmall,
          ),
        ),
        const SizedBox(height: 12),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Text(
            'Deze bronrol (${connection.role.toLowerCase()}) is geen volledig '
            'levensverhaal van ${connection.personName}.',
            style: textTheme.bodyMedium,
          ),
        ),
      ],
    );
  }
}
