import 'package:flutter/material.dart';

import '../personquery/person_query_widgets.dart';
import 'place_search_models.dart';

/// Scherm `place-empty`: getoond bij 0 of >1 match binnen Heemskerk. Toont
/// exact "Hiervoor vinden we geen betrouwbare bron", de bronnenstatus en, bij
/// meer dan één match, de gevonden kandidaatlabels als verfijningsvoorstel
/// (zonder resultaten van verschillende kandidaten samen te voegen).
class PlaceEmptyScreen extends StatelessWidget {
  const PlaceEmptyScreen({
    required this.originalQuery,
    required this.refinementCandidates,
    required this.onPickCandidate,
    required this.onBackToStart,
    super.key,
  });

  static const statusLabel = 'Hiervoor vinden we geen betrouwbare bron';

  final String originalQuery;
  final List<PlaceSearchCandidate> refinementCandidates;
  final ValueChanged<String> onPickCandidate;
  final VoidCallback onBackToStart;

  bool get _hasMultipleCandidates => refinementCandidates.isNotEmpty;

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
        key: const Key('place-empty-back-to-start'),
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
        Text(
          _hasMultipleCandidates
              ? 'Er zijn meerdere mogelijke plekken/gebouwen binnen Heemskerk gevonden. '
                    'We tonen daarom geen samengevoegd antwoord.'
              : 'Er is geen enkele plek, gebouw of monument binnen Heemskerk gevonden voor '
                    'deze zoekterm.',
        ),
        const SizedBox(height: 16),
        Card(
          child: ListTile(
            leading: Icon(_hasMultipleCandidates ? Icons.list : Icons.close),
            title: const Text('Wikidata'),
            subtitle: Text(
              _hasMultipleCandidates
                  ? 'Live geraadpleegd · meerdere mogelijke kandidaten'
                  : 'Live geraadpleegd · geen match binnen Heemskerk',
            ),
          ),
        ),
        if (_hasMultipleCandidates) ...[
          const SizedBox(height: 24),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Bedoelde je een van deze?',
                    style: textTheme.titleMedium,
                  ),
                  const SizedBox(height: 12),
                  for (final candidate in refinementCandidates)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8),
                      child: Align(
                        alignment: Alignment.centerLeft,
                        child: TextButton(
                          key: ValueKey('place-suggestion-${candidate.qid}'),
                          onPressed: () => onPickCandidate(candidate.label),
                          style: personQueryFocusedButtonStyle(
                            Theme.of(context).colorScheme.primary,
                          ),
                          child: Text(
                            candidate.label,
                            textAlign: TextAlign.left,
                          ),
                        ),
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
