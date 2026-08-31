import 'package:flutter/material.dart';

import '../personquery/person_query_widgets.dart';
import 'place_search_models.dart';

/// Scherm `place-answer`: toont het door Wikidata onderbouwde antwoord over
/// een plek/gebouw/monument, met per feitelijke zin een genummerde
/// bronmarkering, de bronitems zelf, een apart gelabeld 'Context'-blok voor
/// de gemeentekoppeling (P131) en een beeldgalerij met per-afbeelding
/// licentiebadge.
class PlaceAnswerScreen extends StatelessWidget {
  const PlaceAnswerScreen({
    required this.originalQuery,
    required this.answer,
    required this.onBackToStart,
    super.key,
  });

  final String originalQuery;
  final PlaceSearchAnswer answer;
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
          label: 'Antwoord gevonden op basis van Wikidata',
          child: Text(
            'Antwoord gevonden op basis van Wikidata',
            style: textTheme.headlineSmall,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          'Actuele beschrijving van dit ene object, geen volledige geschiedschrijving. '
          'Live opgehaald op ${answer.checkedAt.toIso8601String()}.',
          style: textTheme.bodySmall,
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
        if (answer.contextSentence != null) ...[
          const SizedBox(height: 20),
          _ContextSection(sentence: answer.contextSentence!),
        ],
        const SizedBox(height: 20),
        _ImageGallery(answer: answer),
        const SizedBox(height: 20),
        Align(
          alignment: Alignment.centerLeft,
          child: OutlinedButton(
            key: const Key('place-back-to-start'),
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
            key: ValueKey('place-source-${source.number}'),
            margin: const EdgeInsets.only(bottom: 12),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '[${source.number}] Wikidata',
                    style: textTheme.titleSmall,
                  ),
                  const SizedBox(height: 4),
                  Text(source.qid),
                  const SizedBox(height: 8),
                  Semantics(
                    link: true,
                    label:
                        'Bekijk op Wikidata (opent Wikidata in een nieuw tabblad)',
                    child: Text(
                      'Bekijk op Wikidata',
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.primary,
                        decoration: TextDecoration.underline,
                      ),
                    ),
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
  const _ContextSection({required this.sentence});

  final PlaceSearchAnswerSentence sentence;

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
          Text.rich(
            TextSpan(
              children: [
                TextSpan(text: sentence.text),
                TextSpan(
                  text: ' ${sentence.sourceNumbers.map((n) => '[$n]').join()}',
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.primary,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ImageGallery extends StatelessWidget {
  const _ImageGallery({required this.answer});

  final PlaceSearchAnswer answer;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('BEELDGALERIJ', style: textTheme.labelLarge),
        const SizedBox(height: 8),
        if (answer.commonsOutage)
          Card(
            child: ListTile(
              leading: const Icon(Icons.cloud_off),
              title: const Text('Wikimedia Commons'),
              subtitle: const Text(
                'Niet uitgevoerd · afhankelijk van Wikidata',
              ),
            ),
          )
        else if (answer.images.isEmpty)
          const Text('Geen afbeeldingen beschikbaar op Wikimedia Commons.')
        else
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              for (final image in answer.images)
                _ImageTile(
                  image: image,
                  key: ValueKey('place-image-${image.fileName}'),
                ),
            ],
          ),
      ],
    );
  }
}

class _ImageTile extends StatelessWidget {
  const _ImageTile({required this.image, super.key});

  final PlaceSearchImage image;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return SizedBox(
      width: 160,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Semantics(
            image: true,
            label: '${image.fileName}, licentie ${image.license ?? 'onbekend'}',
            child: Image.network(image.url, height: 100, fit: BoxFit.cover),
          ),
          const SizedBox(height: 4),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
            decoration: BoxDecoration(
              border: Border.all(color: Theme.of(context).colorScheme.outline),
              borderRadius: BorderRadius.circular(4),
            ),
            child: Text(
              image.license ?? 'Licentie onbekend',
              style: textTheme.bodySmall,
            ),
          ),
          const SizedBox(height: 4),
          Semantics(
            link: true,
            label:
                'Bekijk bestandspagina op Commons (opent Wikimedia Commons in een nieuw tabblad)',
            child: Text(
              'Bekijk bestandspagina',
              style: TextStyle(
                color: Theme.of(context).colorScheme.primary,
                decoration: TextDecoration.underline,
                fontSize: 12,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
