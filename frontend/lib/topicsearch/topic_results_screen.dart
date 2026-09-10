import 'package:flutter/material.dart';

import '../personquery/person_query_widgets.dart';
import 'topic_search_models.dart';

/// Scherm `topic-results`: onderwerptitel, `checkedAt`, aantal gevonden
/// items, een raster met per-record kaartjes (titel, dataProvider-naam,
/// leesbare licentiebadge, directe link) en het losse Context-blok indien
/// van toepassing. Nooit een samenvattende zin uit meerdere records.
class TopicResultsScreen extends StatelessWidget {
  const TopicResultsScreen({
    required this.originalQuery,
    required this.answer,
    required this.onBackToStart,
    super.key,
  });

  final String originalQuery;
  final TopicSearchAnswer answer;
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
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildHeader(context),
        const SizedBox(height: 20),
        if (answer.context != null) ...[
          _ContextSection(context_: answer.context!),
          const SizedBox(height: 20),
        ],
        _RecordGrid(records: answer.records, columns: 2),
        const SizedBox(height: 20),
        _buildBackAction(context),
      ],
    );
  }

  Widget _buildMobile(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildHeader(context),
        const SizedBox(height: 20),
        if (answer.context != null) ...[
          _ContextSection(context_: answer.context!),
          const SizedBox(height: 20),
        ],
        _RecordGrid(records: answer.records, columns: 1),
        const SizedBox(height: 20),
        _buildBackAction(context),
      ],
    );
  }

  Widget _buildHeader(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('EUROPEANA-RESULTATEN', style: textTheme.labelLarge),
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
        const SizedBox(height: 12),
        PersonQueryStatusMessage(
          label: '${answer.topicSearchTerm}: ${answer.records.length} resultaten gevonden',
          child: Text(answer.topicSearchTerm, style: textTheme.headlineSmall),
        ),
        const SizedBox(height: 4),
        Text(
          '${answer.records.length} gevonden item(s) · live opgehaald op '
          '${answer.checkedAt.toIso8601String()}',
          style: textTheme.bodySmall,
        ),
      ],
    );
  }

  Widget _buildBackAction(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: OutlinedButton(
        key: const Key('topic-results-back-to-start'),
        onPressed: onBackToStart,
        style: personQueryFocusedButtonStyle(
          Theme.of(context).colorScheme.primary,
        ),
        child: const Text('Nieuwe vraag stellen'),
      ),
    );
  }
}

class _ContextSection extends StatelessWidget {
  const _ContextSection({required this.context_});

  final TopicSearchContext context_;

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
          Text(context_.label, style: textTheme.titleSmall),
          if (context_.description != null) ...[
            const SizedBox(height: 4),
            Text(context_.description!),
          ],
        ],
      ),
    );
  }
}

class _RecordGrid extends StatelessWidget {
  const _RecordGrid({required this.records, required this.columns});

  final List<TopicSearchRecord> records;
  final int columns;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('RESULTATEN', style: textTheme.labelLarge),
        const SizedBox(height: 12),
        if (columns <= 1)
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (final record in records)
                Padding(
                  padding: const EdgeInsets.only(bottom: 16),
                  child: _RecordCard(
                    key: ValueKey('topic-record-${record.sourceUrl}'),
                    record: record,
                  ),
                ),
            ],
          )
        else
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(child: _recordColumn(records, 0)),
              const SizedBox(width: 16),
              Expanded(child: _recordColumn(records, 1)),
            ],
          ),
      ],
    );
  }

  Widget _recordColumn(List<TopicSearchRecord> allRecords, int startOffset) {
    final columnRecords = [
      for (var i = startOffset; i < allRecords.length; i += 2) allRecords[i],
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final record in columnRecords)
          Padding(
            padding: const EdgeInsets.only(bottom: 16),
            child: _RecordCard(
              key: ValueKey('topic-record-${record.sourceUrl}'),
              record: record,
            ),
          ),
      ],
    );
  }
}

class _RecordCard extends StatelessWidget {
  const _RecordCard({required this.record, super.key});

  final TopicSearchRecord record;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(record.title, style: textTheme.titleSmall),
            const SizedBox(height: 4),
            Text(record.dataProvider, style: textTheme.bodySmall),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                border: Border.all(color: Theme.of(context).colorScheme.outline),
                borderRadius: BorderRadius.circular(4),
              ),
              child: Text(record.license.text, style: textTheme.bodySmall),
            ),
            const SizedBox(height: 8),
            Semantics(
              link: true,
              label:
                  'Bekijk bron op Europeana (opent Europeana in een nieuw tabblad)',
              child: Text(
                'Bekijk bron',
                style: TextStyle(
                  color: Theme.of(context).colorScheme.primary,
                  decoration: TextDecoration.underline,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
