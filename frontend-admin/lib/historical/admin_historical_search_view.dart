import 'package:flutter/material.dart';

import '../auth/admin_session.dart';
import 'admin_historical_search.dart';

class AdminHistoricalStatusColors {
  const AdminHistoricalStatusColors._();

  static const Color confirmed = Color(0xFF1B5E20);
  static const Color unknown = Color(0xFF7A4100);
  static const Color rejected = Color(0xFFB71C1C);
  static const Color notApplicable = Color(0xFF424242);
  static const Color background = Colors.white;
}

class AdminHistoricalSearchView extends StatefulWidget {
  const AdminHistoricalSearchView({
    required this.identity,
    required this.source,
    super.key,
  });

  final AdminIdentity identity;
  final AdminHistoricalSearchSource source;

  @override
  State<AdminHistoricalSearchView> createState() =>
      _AdminHistoricalSearchViewState();
}

class _AdminHistoricalSearchViewState extends State<AdminHistoricalSearchView> {
  final _queryController = TextEditingController();
  AdminHistoricalSearchResultPage? _page;
  String? _error;
  bool _loading = false;

  @override
  void dispose() {
    _queryController.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final page = await widget.source.search(
        identity: widget.identity,
        query: _queryController.text,
      );
      if (!mounted) return;
      setState(() => _page = page);
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = 'Historische resultaten konden niet worden geladen.',
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final page = _page;
    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: 'Historische bronresultaten beheren',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            'Historische bronresultaten',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          const Text(
            'Bekijk alleen de veilige bronmetadata en de serverzijdig afgeleide statussen.',
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _queryController,
            textInputAction: TextInputAction.search,
            onSubmitted: (_) => _search(),
            decoration: const InputDecoration(
              labelText: 'Zoek historische bronresultaten',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: _loading ? null : _search,
            icon: _loading
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.search),
            label: const Text('Zoeken'),
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Semantics(
              liveRegion: true,
              child: Text(
                _error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
            ),
          ],
          if (page != null) ...[
            const SizedBox(height: 20),
            Semantics(
              liveRegion: true,
              child: Text(
                '${page.results.length} historische resultaten geladen.',
                key: const Key('admin-historical-result-count'),
              ),
            ),
            const SizedBox(height: 12),
            if (page.results.isEmpty)
              const Text('Geen historische bronresultaten gevonden.')
            else
              ...page.results.indexed.map(
                (entry) => Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: _HistoricalResultCard(
                    index: entry.$1,
                    result: entry.$2,
                  ),
                ),
              ),
          ],
        ],
      ),
    );
  }
}

class _HistoricalResultCard extends StatelessWidget {
  const _HistoricalResultCard({required this.index, required this.result});

  final int index;
  final AdminHistoricalSearchResult result;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: 'Historisch bronresultaat ${index + 1}',
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _MetadataLine(label: 'Bron', value: result.sourceName),
              _MetadataLine(
                label: 'Stabiele identifier',
                value: result.stableIdentifier,
              ),
              _MetadataLine(
                label: 'Permanente bronlink',
                value: result.originalSourceUrl,
              ),
              const SizedBox(height: 12),
              _StatusLine(
                label: 'Bronverificatie',
                status: result.sourceVerification,
              ),
              _StatusLine(
                label: 'Metadatarechten',
                status: result.metadataRights,
              ),
              _StatusLine(label: 'Privacy', status: result.privacy),
              _StatusLine(
                label: 'Publieke vrijgave',
                status: result.publicRelease,
              ),
              _StatusLine(
                label: 'Object-/mediarechten',
                status: result.objectMediaRights,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _MetadataLine extends StatelessWidget {
  const _MetadataLine({required this.label, required this.value});

  final String label;
  final String? value;

  @override
  Widget build(BuildContext context) {
    if (value == null) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Text('$label: $value'),
    );
  }
}

class _StatusLine extends StatelessWidget {
  const _StatusLine({required this.label, required this.status});

  final String label;
  final AdminHistoricalStatus status;

  String get _statusLabel => switch (status.status) {
    'CONFIRMED' => 'Bevestigd',
    'REJECTED' => 'Afgewezen',
    'NOT_APPLICABLE' => 'Niet van toepassing',
    _ => 'Onbekend',
  };

  Color get _color => switch (status.status) {
    'CONFIRMED' => AdminHistoricalStatusColors.confirmed,
    'REJECTED' => AdminHistoricalStatusColors.rejected,
    'NOT_APPLICABLE' => AdminHistoricalStatusColors.notApplicable,
    _ => AdminHistoricalStatusColors.unknown,
  };

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: '$label: $_statusLabel. ${status.reason}',
      child: Padding(
        padding: const EdgeInsets.only(top: 6),
        child: ExcludeSemantics(
          child: Text.rich(
            TextSpan(
              children: [
                TextSpan(
                  text: '$label: $_statusLabel. ',
                  style: TextStyle(color: _color, fontWeight: FontWeight.bold),
                ),
                TextSpan(
                  text: status.reason,
                  style: TextStyle(color: _color),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
