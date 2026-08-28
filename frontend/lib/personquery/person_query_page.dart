import 'package:flutter/material.dart';

import 'meaning_selection_screen.dart';
import 'no_reliable_source_screen.dart';
import 'person_query_interpreter.dart';
import 'person_query_widgets.dart';
import 'wikidata_meaning_client.dart';

enum _PersonQueryScreen {
  start,
  meaningSelection,
  noReliableSource,
  confirmation,
}

/// Nieuw, losstaand instappunt (screenKey `start`) voor de sessiezoek-route.
/// Voert deterministische vraaginterpretatie en Heemskerk-disambiguatie
/// volledig client-side uit, zonder enige aanroep naar Open Archieven
/// Records/Search/Show. Ontsloten via een nieuwe actie op de bestaande
/// homepage; de homepage zelf blijft ongewijzigd.
class PersonQueryPage extends StatefulWidget {
  const PersonQueryPage({
    this.interpreter = const PersonQueryInterpreter(),
    WikidataMeaningSource? meaningSource,
    super.key,
  }) : meaningSource = meaningSource ?? const _LazyWikidataMeaningClient();

  final PersonQueryInterpreter interpreter;
  final WikidataMeaningSource meaningSource;

  @override
  State<PersonQueryPage> createState() => _PersonQueryPageState();
}

/// Vertraagt het aanmaken van de echte HTTP-client tot het scherm dat hem
/// nodig heeft daadwerkelijk gebouwd wordt, zodat widgettests die het scherm
/// nooit tonen ook nooit een `http.Client` aanmaken.
class _LazyWikidataMeaningClient implements WikidataMeaningSource {
  const _LazyWikidataMeaningClient();

  @override
  Future<WikidataMeaningResult> fetchMeanings() {
    return WikidataMeaningClient().fetchMeanings();
  }
}

class _PersonQueryPageState extends State<PersonQueryPage> {
  final _controller = TextEditingController();
  final _fieldFocusNode = FocusNode(debugLabel: 'person-query-field');

  _PersonQueryScreen _screen = _PersonQueryScreen.start;
  String _submittedQuery = '';
  PersonQueryInterpretation? _interpretation;
  String? _chosenMeaningQid;

  @override
  void dispose() {
    _controller.dispose();
    _fieldFocusNode.dispose();
    super.dispose();
  }

  void _submit() {
    final text = _controller.text.trim();
    if (text.isEmpty) return;
    final interpretation = widget.interpreter.interpret(text);
    setState(() {
      _submittedQuery = text;
      _interpretation = interpretation;
      _chosenMeaningQid = null;
      if (!interpretation.hasRecognizedName) {
        _screen = _PersonQueryScreen.noReliableSource;
      } else if (interpretation.heemskerkAmbiguous) {
        _screen = _PersonQueryScreen.meaningSelection;
      } else {
        _screen = _PersonQueryScreen.confirmation;
      }
    });
  }

  void _pickSuggestion(String suggestion) {
    setState(() {
      _controller.text = suggestion;
      _controller.selection = TextSelection.collapsed(
        offset: suggestion.length,
      );
      _screen = _PersonQueryScreen.start;
    });
    _fieldFocusNode.requestFocus();
  }

  void _backToStart() {
    setState(() => _screen = _PersonQueryScreen.start);
    _fieldFocusNode.requestFocus();
  }

  void _confirmMeaning(String qid) {
    setState(() {
      _chosenMeaningQid = qid;
      _screen = _PersonQueryScreen.confirmation;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Historisch Heemskerk')),
      body: SafeArea(child: _buildScreen(context)),
    );
  }

  Widget _buildScreen(BuildContext context) {
    switch (_screen) {
      case _PersonQueryScreen.start:
        return _StartScreen(
          controller: _controller,
          fieldFocusNode: _fieldFocusNode,
          onSubmit: _submit,
        );
      case _PersonQueryScreen.noReliableSource:
        return NoReliableSourceScreen(
          originalQuery: _submittedQuery,
          onPickSuggestion: _pickSuggestion,
          onBackToStart: _backToStart,
        );
      case _PersonQueryScreen.meaningSelection:
        return MeaningSelectionScreen(
          originalQuery: _submittedQuery,
          meaningSource: widget.meaningSource,
          onConfirm: _confirmMeaning,
          onEditQuery: _backToStart,
        );
      case _PersonQueryScreen.confirmation:
        return _ConfirmationScreen(
          interpretation: _interpretation!,
          chosenMeaningQid: _chosenMeaningQid,
          onBackToStart: _backToStart,
        );
    }
  }
}

class _StartScreen extends StatelessWidget {
  const _StartScreen({
    required this.controller,
    required this.fieldFocusNode,
    required this.onSubmit,
  });

  static const exampleQuestions = [
    'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?',
    'Wie waren de ouders van Trijntje Beentjes?',
  ];

  final TextEditingController controller;
  final FocusNode fieldFocusNode;
  final VoidCallback onSubmit;

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
        Expanded(flex: 3, child: _buildQuestionCard(context)),
        const SizedBox(width: 24),
        Expanded(flex: 2, child: _buildCoveragePanel(context)),
      ],
    );
  }

  Widget _buildMobile(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _buildQuestionCard(context),
        const SizedBox(height: 24),
        _buildCoveragePanel(context),
      ],
    );
  }

  Widget _buildQuestionCard(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text('ONTDEK VANUIT JE EIGEN VRAAG', style: textTheme.labelLarge),
        const SizedBox(height: 8),
        Text('Waar ben je nieuwsgierig naar?', style: textTheme.headlineMedium),
        const SizedBox(height: 12),
        const Text(
          'Stel een gewone vraag over een genoemde persoon. We interpreteren '
          'de vraag eerst betrouwbaar en herkenbaar, vóórdat er iets '
          'opgezocht wordt.',
        ),
        const SizedBox(height: 20),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextField(
                  key: const Key('person-query-field'),
                  controller: controller,
                  focusNode: fieldFocusNode,
                  decoration: const InputDecoration(
                    labelText: 'Stel je vraag over Heemskerk',
                    hintText: 'Bijvoorbeeld: Wie was Nicolaas Jacobus Sinnige?',
                    border: OutlineInputBorder(),
                  ),
                  onEditingComplete: onSubmit,
                ),
                const SizedBox(height: 12),
                Align(
                  alignment: Alignment.centerLeft,
                  child: FilledButton(
                    key: const Key('person-query-submit'),
                    onPressed: onSubmit,
                    style: personQueryFocusedButtonStyle(
                      Theme.of(context).colorScheme.onPrimary,
                    ),
                    child: const Text('Zoek in bronnen'),
                  ),
                ),
                const SizedBox(height: 16),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final example in exampleQuestions)
                      ActionChip(
                        label: Text(example),
                        onPressed: () {
                          controller.text = example;
                          controller.selection = TextSelection.collapsed(
                            offset: example.length,
                          );
                        },
                      ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildCoveragePanel(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Wat zoeken we nu?', style: textTheme.titleLarge),
          const SizedBox(height: 12),
          const Text(
            'Open Archieven-genealogie voor Heemskerk, met Wikidata als '
            'aanvullende context.',
          ),
          const SizedBox(height: 12),
          const Text(
            'Een langer lopende zoekopdracht kan binnen deze sessie gewoon '
            'doorlopen; je hoeft niet te wachten voordat je verdergaat.',
          ),
        ],
      ),
    );
  }
}

class _ConfirmationScreen extends StatelessWidget {
  const _ConfirmationScreen({
    required this.interpretation,
    required this.chosenMeaningQid,
    required this.onBackToStart,
  });

  final PersonQueryInterpretation interpretation;
  final String? chosenMeaningQid;
  final VoidCallback onBackToStart;

  @override
  Widget build(BuildContext context) {
    final buffer = StringBuffer(
      'Geïnterpreteerd: ${interpretation.firstName} ${interpretation.lastName}.',
    );
    if (interpretation.yearConstraint != null) {
      buffer.write(' Jaartal: ${interpretation.yearConstraint}.');
    }
    if (interpretation.eventTypeConstraint != null) {
      buffer.write(' Gebeurtenis: ${interpretation.eventTypeConstraint}.');
    }
    if (chosenMeaningQid != null) {
      buffer.write(' Gekozen betekenis voor Heemskerk: $chosenMeaningQid.');
    }
    final summary = buffer.toString();

    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 680),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              PersonQueryStatusMessage(
                label: summary,
                child: Text(
                  summary,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
              const SizedBox(height: 8),
              const Text(
                'De volgende stap (zoeken in Open Archieven) hoort bij een '
                'latere story en wordt hier nog niet uitgevoerd.',
              ),
              const SizedBox(height: 20),
              Align(
                alignment: Alignment.centerLeft,
                child: OutlinedButton(
                  onPressed: onBackToStart,
                  style: personQueryFocusedButtonStyle(
                    Theme.of(context).colorScheme.primary,
                  ),
                  child: const Text('Nieuwe vraag stellen'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
