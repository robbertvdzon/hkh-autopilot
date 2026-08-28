import 'package:flutter/material.dart';

import '../config/app_config.dart';
import '../personsearch/followed_connection_screen.dart';
import '../personsearch/live_search_screen.dart';
import '../personsearch/person_search_client.dart';
import '../personsearch/person_search_models.dart';
import '../personsearch/source_outage_screen.dart';
import '../personsearch/supported_answer_screen.dart';
import 'meaning_selection_screen.dart';
import 'no_reliable_source_screen.dart';
import 'person_query_interpreter.dart';
import 'person_query_widgets.dart';
import 'wikidata_meaning_client.dart';

enum _PersonQueryScreen {
  start,
  meaningSelection,
  noReliableSource,
  liveSearch,
  supportedAnswer,
  followedConnection,
  sourceOutage,
}

/// Nieuw, losstaand instappunt (screenKey `start`) voor de sessiezoek-route.
/// Voert deterministische vraaginterpretatie en Heemskerk-disambiguatie
/// volledig client-side uit, en dient bij een geldige interpretatie de live
/// persoonszoekjob in bij de backend (`POST /api/person-search`), waarna op
/// basis van het resultaattype naar het passende scherm wordt geschakeld.
/// Ontsloten via een nieuwe actie op de bestaande homepage; de homepage zelf
/// blijft ongewijzigd.
class PersonQueryPage extends StatefulWidget {
  const PersonQueryPage({
    this.interpreter = const PersonQueryInterpreter(),
    WikidataMeaningSource? meaningSource,
    PersonSearchSource? personSearchSource,
    super.key,
  }) : meaningSource = meaningSource ?? const _LazyWikidataMeaningClient(),
       personSearchSource =
           personSearchSource ?? const _LazyPersonSearchClient();

  final PersonQueryInterpreter interpreter;
  final WikidataMeaningSource meaningSource;
  final PersonSearchSource personSearchSource;

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

class _LazyPersonSearchClient implements PersonSearchSource {
  const _LazyPersonSearchClient();

  @override
  Future<PersonSearchResult> submit({
    required String recognizedName,
    String? secondName,
    String? eventType,
    String? yearOrPeriod,
    String? heemskerkMeaningQid,
    required String originalQuery,
  }) {
    return PersonSearchClient(AppConfig.apiBaseUrl).submit(
      recognizedName: recognizedName,
      secondName: secondName,
      eventType: eventType,
      yearOrPeriod: yearOrPeriod,
      heemskerkMeaningQid: heemskerkMeaningQid,
      originalQuery: originalQuery,
    );
  }
}

class _PersonQueryPageState extends State<PersonQueryPage> {
  final _controller = TextEditingController();
  final _fieldFocusNode = FocusNode(debugLabel: 'person-query-field');

  _PersonQueryScreen _screen = _PersonQueryScreen.start;
  String _submittedQuery = '';
  PersonQueryInterpretation? _interpretation;
  PersonSearchResult? _lastResult;
  bool _stillRunning = false;
  PersonSearchConnectionOption? _followedConnection;
  int _searchGeneration = 0;

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
      if (!interpretation.hasRecognizedName) {
        _screen = _PersonQueryScreen.noReliableSource;
      } else if (interpretation.heemskerkAmbiguous) {
        _screen = _PersonQueryScreen.meaningSelection;
      } else {
        _startLiveSearch(interpretation, null);
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
    final interpretation = _interpretation!;
    setState(() {
      _startLiveSearch(interpretation, qid);
    });
  }

  void _startLiveSearch(
    PersonQueryInterpretation interpretation,
    String? meaningQid,
  ) {
    _screen = _PersonQueryScreen.liveSearch;
    _stillRunning = false;
    _lastResult = null;
    final generation = ++_searchGeneration;
    final recognizedName = [
      interpretation.firstName,
      interpretation.lastName,
    ].whereType<String>().join(' ');

    widget.personSearchSource
        .submit(
          recognizedName: recognizedName,
          yearOrPeriod: interpretation.yearConstraint,
          eventType: interpretation.eventTypeConstraint,
          heemskerkMeaningQid: meaningQid,
          originalQuery: _submittedQuery,
        )
        .then((result) => _onSearchResult(generation, result))
        .catchError((_) => _onSearchFailure(generation));
  }

  void _onSearchResult(int generation, PersonSearchResult result) {
    if (!mounted || generation != _searchGeneration) return;
    setState(() {
      _lastResult = result;
      switch (result.status) {
        case PersonSearchStatus.running:
          _stillRunning = true;
          _screen = _PersonQueryScreen.liveSearch;
        case PersonSearchStatus.supportedAnswer:
          _screen = _PersonQueryScreen.supportedAnswer;
        case PersonSearchStatus.noResults:
          _screen = _PersonQueryScreen.noReliableSource;
        case PersonSearchStatus.partial:
          _screen = _PersonQueryScreen.noReliableSource;
        case PersonSearchStatus.sourceOutage:
          _screen = _PersonQueryScreen.sourceOutage;
      }
    });
  }

  void _onSearchFailure(int generation) {
    if (!mounted || generation != _searchGeneration) return;
    setState(() {
      _lastResult = null;
      _screen = _PersonQueryScreen.sourceOutage;
    });
  }

  void _followConnection(PersonSearchConnectionOption connection) {
    setState(() {
      _followedConnection = connection;
      _screen = _PersonQueryScreen.followedConnection;
    });
  }

  void _backToAnswer() {
    setState(() => _screen = _PersonQueryScreen.supportedAnswer);
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
        return _buildNoReliableSourceVariant();
      case _PersonQueryScreen.meaningSelection:
        return MeaningSelectionScreen(
          originalQuery: _submittedQuery,
          meaningSource: widget.meaningSource,
          onConfirm: _confirmMeaning,
          onEditQuery: _backToStart,
        );
      case _PersonQueryScreen.liveSearch:
        return LiveSearchScreen(
          originalQuery: _submittedQuery,
          stillRunning: _stillRunning,
          onBackToStart: _backToStart,
        );
      case _PersonQueryScreen.supportedAnswer:
        return SupportedAnswerScreen(
          originalQuery: _submittedQuery,
          answer: _lastResult!.answer!,
          wikidataContext: _lastResult!.context,
          onFollowConnection: _followConnection,
          onBackToStart: _backToStart,
        );
      case _PersonQueryScreen.followedConnection:
        return FollowedConnectionScreen(
          originalQuery: _submittedQuery,
          connection: _followedConnection!,
          onBackToAnswer: _backToAnswer,
          onBackToStart: _backToStart,
        );
      case _PersonQueryScreen.sourceOutage:
        return SourceOutageScreen(
          originalQuery: _submittedQuery,
          wikidataContext: _lastResult?.context,
          onBackToStart: _backToStart,
        );
    }
  }

  Widget _buildNoReliableSourceVariant() {
    final result = _lastResult;
    if (result == null) {
      return NoReliableSourceScreen(
        originalQuery: _submittedQuery,
        onPickSuggestion: _pickSuggestion,
        onBackToStart: _backToStart,
      );
    }
    if (result.status == PersonSearchStatus.partial) {
      return NoReliableSourceScreen(
        originalQuery: _submittedQuery,
        onPickSuggestion: _pickSuggestion,
        onBackToStart: _backToStart,
        statusLabel: 'Te veel mogelijke resultaten',
        explanation:
            result.refinementMessage ??
            'Er zijn meer dan honderd mogelijke resultaten. Verfijn je vraag.',
        openArchivenSubtitle: 'Meer dan 100 mogelijke resultaten · verfijning nodig',
      );
    }
    return NoReliableSourceScreen(
      originalQuery: _submittedQuery,
      onPickSuggestion: _pickSuggestion,
      onBackToStart: _backToStart,
      statusLabel: 'Geen resultaten gevonden in Open Archieven',
      explanation:
          'Open Archieven bevat voor deze vraag geen enkel resultaat binnen '
          'Heemskerk.',
      openArchivenSubtitle: 'Live geraadpleegd · nul resultaten',
    );
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
