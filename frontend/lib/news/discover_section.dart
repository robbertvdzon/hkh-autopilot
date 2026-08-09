import 'package:flutter/material.dart';

import 'latest_news.dart';

/// Vaste badge-voorgrondkleuren per entiteitstype, tegen een witte achtergrond. Elke kleur haalt
/// een contrastratio van minimaal 4.5:1 (WCAG 2.1 AA voor normale tekst), naar het patroon van
/// `PrivacyClassificationStatusColors`: PLEK 7.87:1, PERSOON 8.63:1, GEBEURTENIS 6.57:1.
class NewsEntityBadgeColors {
  const NewsEntityBadgeColors._();

  static const Color background = Color(0xFFFFFFFF);

  static const Map<String, Color> foregroundByType = {
    'PLEK': Color(0xFF1B5E20),
    'PERSOON': Color(0xFF0D47A1),
    'GEBEURTENIS': Color(0xFFB71C1C),
  };

  static const Color fallbackForeground = Color(0xFF1B5E20);
}

const Map<String, String> _entityTypeLabels = {
  'PLEK': 'Plek',
  'PERSOON': 'Persoon',
  'GEBEURTENIS': 'Gebeurtenis',
};

String _entityTypeLabel(String type) => _entityTypeLabels[type] ?? type;

String _formatDate(DateTime value) {
  final local = value.toLocal();
  return '${local.day.toString().padLeft(2, '0')}-'
      '${local.month.toString().padLeft(2, '0')}-${local.year}';
}

ButtonStyle _focusBorderStyle(Color focusBorderColor) {
  return ButtonStyle(
    side: WidgetStateProperty.resolveWith((states) {
      if (!states.contains(WidgetState.focused)) return null;
      return BorderSide(color: focusBorderColor, width: 3);
    }),
  );
}

/// Homepage-ontdekblok: een gelabeld zoekveld plus entiteitchips (`PLEK`/`PERSOON`/`GEBEURTENIS`)
/// over uitsluitend gepubliceerd nieuws (`GET /api/news`). Een zoekopdracht of chipklik toont een
/// resultatenlijst of, bij nul resultaten, een niet-lege lege staat met suggestiechips. Elk
/// resultaat opent een detailweergave met een terugknop naar de resultatenlijst.
class DiscoverSection extends StatefulWidget {
  const DiscoverSection({required this.source, super.key});

  final LatestNewsSource source;

  @override
  State<DiscoverSection> createState() => _DiscoverSectionState();
}

class _DiscoverSectionState extends State<DiscoverSection> {
  final _searchController = TextEditingController();
  late Future<NewsSearchResult> _initial;
  Future<NewsSearchResult>? _activeSearch;
  String? _selectedEntity;

  @override
  void initState() {
    super.initState();
    _initial = widget.source.loadLatestNews();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _search() {
    final query = _searchController.text.trim();
    setState(() {
      _activeSearch = widget.source.loadLatestNews(
        q: query.isEmpty ? null : query,
        entity: _selectedEntity,
      );
    });
  }

  void _selectEntity(String label) {
    setState(() {
      _selectedEntity = label;
      _searchController.text = label;
      _activeSearch = widget.source.loadLatestNews(entity: label);
    });
  }

  void _clear() {
    setState(() {
      _searchController.clear();
      _selectedEntity = null;
      _activeSearch = null;
    });
  }

  void _openDetail(LatestNewsItem item) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (context) => NewsDetailPage(item: item)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<NewsSearchResult>(
      future: _initial,
      builder: (context, snapshot) {
        final entities = snapshot.connectionState == ConnectionState.done && !snapshot.hasError
            ? snapshot.requireData.entities
            : const <AggregatedNewsEntity>[];
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Ontdek nieuws',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 4),
            const Text(
              'Zoek op vrije tekst of kies een plek, persoon of gebeurtenis.',
            ),
            const SizedBox(height: 12),
            TextField(
              key: const Key('discover-search-field'),
              controller: _searchController,
              decoration: const InputDecoration(
                labelText: 'Zoek in nieuwsberichten',
                border: OutlineInputBorder(),
              ),
              textInputAction: TextInputAction.search,
              // onEditingComplete (not onSubmitted) is used deliberately: Flutter's default
              // onSubmitted handling unfocuses the field first, which would break keyboard
              // traversal by resetting Tab order back to the top of the page.
              onEditingComplete: _search,
            ),
            if (entities.isNotEmpty) ...[
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: entities
                    .map(
                      (entity) => ActionChip(
                        key: Key('discover-entity-chip-${entity.label}'),
                        label: Text(
                          '${_entityTypeLabel(entity.type)}: ${entity.label}',
                        ),
                        onPressed: () => _selectEntity(entity.label),
                      ),
                    )
                    .toList(growable: false),
              ),
            ],
            const SizedBox(height: 12),
            if (_activeSearch != null) _buildResults(context),
            const SizedBox(height: 12),
            Align(
              alignment: Alignment.centerLeft,
              child: OutlinedButton.icon(
                key: const Key('discover-clear-button'),
                onPressed: _clear,
                style: _focusBorderStyle(Theme.of(context).colorScheme.primary),
                icon: const Icon(Icons.clear),
                label: const Text('Wis zoekopdracht'),
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _buildResults(BuildContext context) {
    return FutureBuilder<NewsSearchResult>(
      future: _activeSearch,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Padding(
            padding: EdgeInsets.symmetric(vertical: 16),
            child: Center(child: CircularProgressIndicator()),
          );
        }
        if (snapshot.hasError) {
          return const Padding(
            padding: EdgeInsets.symmetric(vertical: 12),
            child: Text('Zoeken is mislukt. Probeer het opnieuw.'),
          );
        }
        final result = snapshot.requireData;
        final countLabel = result.total == 1
            ? '1 resultaat gevonden.'
            : '${result.total} resultaten gevonden.';
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Semantics(
              key: const Key('discover-result-count'),
              liveRegion: true,
              child: Text(countLabel),
            ),
            const SizedBox(height: 8),
            if (result.items.isEmpty)
              _EmptyState(suggestions: result.entities, onSelectSuggestion: _selectEntity)
            else
              for (final item in result.items)
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: _ResultCard(item: item, onOpen: () => _openDetail(item)),
                ),
          ],
        );
      },
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.suggestions, required this.onSelectSuggestion});

  final List<AggregatedNewsEntity> suggestions;
  final ValueChanged<String> onSelectSuggestion;

  @override
  Widget build(BuildContext context) {
    return Card(
      key: const Key('discover-empty-state'),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('Geen resultaten gevonden voor deze zoekopdracht.'),
            if (suggestions.isNotEmpty) ...[
              const SizedBox(height: 12),
              const Text('Probeer een van deze suggesties:'),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: suggestions
                    .map(
                      (entity) => ActionChip(
                        key: Key('discover-suggestion-chip-${entity.label}'),
                        label: Text(
                          '${_entityTypeLabel(entity.type)}: ${entity.label}',
                        ),
                        onPressed: () => onSelectSuggestion(entity.label),
                      ),
                    )
                    .toList(growable: false),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _ResultCard extends StatelessWidget {
  const _ResultCard({required this.item, required this.onOpen});

  final LatestNewsItem item;
  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        key: Key('discover-result-${item.id}'),
        onTap: onOpen,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(item.title, style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 4),
              Text(
                item.message,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
              ),
              if (item.entities.isNotEmpty) ...[
                const SizedBox(height: 8),
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: item.entities
                      .map((entity) => _EntityBadge(entity: entity))
                      .toList(growable: false),
                ),
              ],
              const SizedBox(height: 8),
              Text(
                item.source ?? '',
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EntityBadge extends StatelessWidget {
  const _EntityBadge({required this.entity});

  final NewsEntity entity;

  @override
  Widget build(BuildContext context) {
    final color =
        NewsEntityBadgeColors.foregroundByType[entity.type] ??
        NewsEntityBadgeColors.fallbackForeground;
    final label = '${_entityTypeLabel(entity.type)}: ${entity.label}';
    return Semantics(
      label: label,
      child: ExcludeSemantics(
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          decoration: BoxDecoration(
            color: NewsEntityBadgeColors.background,
            border: Border.all(color: color),
            borderRadius: BorderRadius.circular(4),
          ),
          child: Text(
            label,
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.bold,
              fontSize: 12,
            ),
          ),
        ),
      ),
    );
  }
}

/// Detailweergave van één nieuwsresultaat: volledige berichttekst, publicatiedatum en
/// bronvermelding, met een terugknop naar de resultatenlijst (standaard AppBar-terugactie, naar
/// het patroon van `ProductVisionPage`).
class NewsDetailPage extends StatelessWidget {
  const NewsDetailPage({required this.item, super.key});

  final LatestNewsItem item;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(item.title),
        leading: BackButton(
          key: const Key('discover-detail-back'),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  item.title,
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                Text(
                  _formatDate(item.publishedAt),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                const SizedBox(height: 16),
                Text(item.message),
                const SizedBox(height: 16),
                Text(
                  item.source ?? '',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
