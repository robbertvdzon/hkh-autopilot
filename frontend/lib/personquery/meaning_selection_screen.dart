import 'package:flutter/material.dart';

import 'person_query_widgets.dart';
import 'wikidata_meaning_client.dart';

/// Scherm `meaning-selection`: uitsluitend getoond bij een ambigu geval.
/// Haalt bij tonen live Wikidata-labels/beschrijvingen op voor Q9926 en
/// Q91564725; toont bij een mislukte live oproep vaste fallback-labels plus
/// een zichtbare storingsmelding. Volgt qua structuur
/// `hkh-sessiezoek-v23-02-betekenis-kiezen-*`.
class MeaningSelectionScreen extends StatefulWidget {
  const MeaningSelectionScreen({
    required this.originalQuery,
    required this.meaningSource,
    required this.onConfirm,
    required this.onEditQuery,
    super.key,
  });

  final String originalQuery;
  final WikidataMeaningSource meaningSource;
  final ValueChanged<String> onConfirm;
  final VoidCallback onEditQuery;

  static const fallbackPlaceLabel = 'Q9926 · Heemskerk (plaats)';
  static const fallbackSurnameLabel = 'Q91564725 · Heemskerk (achternaam)';
  static const fallbackNotice =
      'De actuele Wikidata-omschrijving kon niet worden opgehaald. '
      'De vaste omschrijving hieronder wordt getoond; kiezen blijft mogelijk.';

  @override
  State<MeaningSelectionScreen> createState() => _MeaningSelectionScreenState();
}

class _MeaningSelectionScreenState extends State<MeaningSelectionScreen> {
  late Future<WikidataMeaningResult> _future;
  String _selectedQid = WikidataMeaningIds.place;

  @override
  void initState() {
    super.initState();
    _future = widget.meaningSource.fetchMeanings();
  }

  void _select(String qid) {
    setState(() => _selectedQid = qid);
  }

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
              child: FutureBuilder<WikidataMeaningResult>(
                future: _future,
                builder: (context, snapshot) {
                  final hasError = snapshot.hasError;
                  final data = snapshot.data;
                  final loading =
                      snapshot.connectionState != ConnectionState.done;
                  final placeOption =
                      data?.place ??
                      const WikidataMeaningOption(
                        qid: WikidataMeaningIds.place,
                        label: MeaningSelectionScreen.fallbackPlaceLabel,
                        description:
                            'De gemeente en het dorp Heemskerk in Noord-Holland.',
                      );
                  final surnameOption =
                      data?.surname ??
                      const WikidataMeaningOption(
                        qid: WikidataMeaningIds.surname,
                        label: MeaningSelectionScreen.fallbackSurnameLabel,
                        description:
                            'Heemskerk gebruikt als achternaam, ongeacht waar iemand woonde.',
                      );
                  return isMobile
                      ? _buildMobile(
                          context,
                          loading: loading,
                          hasError: hasError,
                          place: placeOption,
                          surname: surnameOption,
                        )
                      : _buildDesktop(
                          context,
                          loading: loading,
                          hasError: hasError,
                          place: placeOption,
                          surname: surnameOption,
                        );
                },
              ),
            ),
          ),
        );
      },
    );
  }

  Widget _buildDesktop(
    BuildContext context, {
    required bool loading,
    required bool hasError,
    required WikidataMeaningOption place,
    required WikidataMeaningOption surname,
  }) {
    return _buildContent(
      context,
      loading: loading,
      hasError: hasError,
      place: place,
      surname: surname,
      buttonsInRow: true,
    );
  }

  Widget _buildMobile(
    BuildContext context, {
    required bool loading,
    required bool hasError,
    required WikidataMeaningOption place,
    required WikidataMeaningOption surname,
  }) {
    return _buildContent(
      context,
      loading: loading,
      hasError: hasError,
      place: place,
      surname: surname,
      buttonsInRow: false,
    );
  }

  Widget _buildContent(
    BuildContext context, {
    required bool loading,
    required bool hasError,
    required WikidataMeaningOption place,
    required WikidataMeaningOption surname,
    required bool buttonsInRow,
  }) {
    final textTheme = Theme.of(context).textTheme;
    final buttons = [
      FilledButton(
        onPressed: () => widget.onConfirm(_selectedQid),
        style: personQueryFocusedButtonStyle(
          Theme.of(context).colorScheme.onPrimary,
        ),
        child: const Text('Zoek met deze betekenis'),
      ),
      OutlinedButton(
        onPressed: widget.onEditQuery,
        style: personQueryFocusedButtonStyle(
          Theme.of(context).colorScheme.primary,
        ),
        child: const Text('Vraag aanpassen'),
      ),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('BETEKENIS KIEZEN', style: textTheme.labelLarge),
        const SizedBox(height: 8),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Je vraag', style: textTheme.labelLarge),
              const SizedBox(height: 4),
              Text(widget.originalQuery),
            ],
          ),
        ),
        const SizedBox(height: 16),
        Text(
          'Heemskerk kan hier twee dingen betekenen',
          style: textTheme.headlineSmall,
        ),
        const SizedBox(height: 8),
        const Text(
          'Er staat geen woord als "in", "te", "uit" of "van" vlak vóór '
          'Heemskerk, dus kunnen we niet automatisch afleiden of dit de '
          'plaats is of iemands achternaam. Kies hieronder wat je bedoelt — '
          'resultaten van beide betekenissen worden nooit samengevoegd.',
        ),
        const SizedBox(height: 16),
        if (loading)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 12),
            child: PersonQueryStatusMessage(
              label: 'Betekenissen worden opgehaald.',
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  ),
                  SizedBox(width: 12),
                  Expanded(child: Text('Betekenissen worden opgehaald…')),
                ],
              ),
            ),
          ),
        if (!loading && hasError)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: PersonQueryStatusMessage(
              label: MeaningSelectionScreen.fallbackNotice,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ExcludeSemantics(
                    child: Icon(
                      Icons.warning_amber,
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                  const SizedBox(width: 8),
                  const Expanded(
                    child: Text(MeaningSelectionScreen.fallbackNotice),
                  ),
                ],
              ),
            ),
          ),
        if (!loading)
          _MeaningRadioGroup(
            selectedQid: _selectedQid,
            onChanged: _select,
            place: place,
            surname: surname,
          ),
        const SizedBox(height: 20),
        buttonsInRow
            ? Row(children: [buttons[0], const SizedBox(width: 12), buttons[1]])
            : Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [buttons[0], const SizedBox(height: 12), buttons[1]],
              ),
        const SizedBox(height: 16),
        const Text(
          'Ter vergelijking: bij "geboren in Heemskerk" staat er wél een '
          'voorzetsel vlak vóór Heemskerk, dus is de plaatsbetekenis dan '
          'ondubbelzinnig en verschijnt deze keuze niet.',
          style: TextStyle(fontStyle: FontStyle.italic),
        ),
      ],
    );
  }
}

class _MeaningRadioGroup extends StatefulWidget {
  const _MeaningRadioGroup({
    required this.selectedQid,
    required this.onChanged,
    required this.place,
    required this.surname,
  });

  final String selectedQid;
  final ValueChanged<String> onChanged;
  final WikidataMeaningOption place;
  final WikidataMeaningOption surname;

  @override
  State<_MeaningRadioGroup> createState() => _MeaningRadioGroupState();
}

class _MeaningRadioGroupState extends State<_MeaningRadioGroup> {
  final _placeFocusNode = FocusNode(debugLabel: 'meaning-place');
  final _surnameFocusNode = FocusNode(debugLabel: 'meaning-surname');

  @override
  void dispose() {
    _placeFocusNode.dispose();
    _surnameFocusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: 'Wat bedoel je met "Heemskerk"?',
      child: RadioGroup<String>(
        groupValue: widget.selectedQid,
        onChanged: (value) {
          if (value != null) widget.onChanged(value);
        },
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Wat bedoel je met "Heemskerk"?',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            _MeaningOptionTile(
              key: ValueKey('meaning-option-${WikidataMeaningIds.place}'),
              qid: WikidataMeaningIds.place,
              focusNode: _placeFocusNode,
              option: widget.place,
              selected: widget.selectedQid == WikidataMeaningIds.place,
            ),
            const SizedBox(height: 8),
            _MeaningOptionTile(
              key: ValueKey('meaning-option-${WikidataMeaningIds.surname}'),
              qid: WikidataMeaningIds.surname,
              focusNode: _surnameFocusNode,
              option: widget.surname,
              selected: widget.selectedQid == WikidataMeaningIds.surname,
            ),
          ],
        ),
      ),
    );
  }
}

/// Eén optie in de betekenis-radiogroep. Selectie en pijltjestoetsnavigatie
/// worden afgehandeld door de omringende [RadioGroup]; deze widget volgt
/// alleen de focusstatus om de gedeelde 3px-focusrandconventie zichtbaar te
/// maken.
class _MeaningOptionTile extends StatefulWidget {
  const _MeaningOptionTile({
    required this.qid,
    required this.focusNode,
    required this.option,
    required this.selected,
    super.key,
  });

  final String qid;
  final FocusNode focusNode;
  final WikidataMeaningOption option;
  final bool selected;

  @override
  State<_MeaningOptionTile> createState() => _MeaningOptionTileState();
}

class _MeaningOptionTileState extends State<_MeaningOptionTile> {
  @override
  void initState() {
    super.initState();
    widget.focusNode.addListener(_onFocusChange);
  }

  @override
  void dispose() {
    widget.focusNode.removeListener(_onFocusChange);
    super.dispose();
  }

  void _onFocusChange() => setState(() {});

  void _select(BuildContext context) {
    RadioGroup.maybeOf<String>(context)?.onChanged(widget.qid);
  }

  @override
  Widget build(BuildContext context) {
    final hasFocus = widget.focusNode.hasFocus;
    final borderColor = hasFocus
        ? Theme.of(context).colorScheme.primary
        : Theme.of(context).colorScheme.outline;
    final borderWidth = hasFocus ? 3.0 : 1.0;
    return GestureDetector(
      onTap: () => _select(context),
      child: Semantics(
        inMutuallyExclusiveGroup: true,
        checked: widget.selected,
        label: '${widget.option.label}. ${widget.option.description}',
        child: Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            border: Border.all(color: borderColor, width: borderWidth),
            borderRadius: BorderRadius.circular(8),
            color: widget.selected
                ? Theme.of(context).colorScheme.primaryContainer
                : null,
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Radio<String>(
                key: ValueKey('meaning-radio-${widget.qid}'),
                value: widget.qid,
                focusNode: widget.focusNode,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      widget.option.label,
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(widget.option.description),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
