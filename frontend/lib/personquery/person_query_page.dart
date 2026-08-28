import 'dart:async';

import 'package:flutter/material.dart';

import '../config/app_config.dart';
import '../personsearch/background_search_screen.dart';
import '../personsearch/followed_connection_screen.dart';
import '../personsearch/live_search_screen.dart';
import '../personsearch/person_search_client.dart';
import '../personsearch/person_search_models.dart';
import '../personsearch/search_ready_screen.dart';
import '../personsearch/session_indicator_badge.dart';
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
  backgroundSearch,
  searchReady,
  unavailable,
  supportedAnswer,
  followedConnection,
  sourceOutage,
}

/// Hoe vaak de client tijdens `background-search` de status van een job
/// opnieuw opvraagt. Niet gespecificeerd door de story; een vast, redelijk
/// interval is een implementatiedetail zonder acceptatiecriterium.
const _pollInterval = Duration(seconds: 3);

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

  @override
  Future<PersonSearchStatusResult> pollStatus(String jobId) {
    return PersonSearchClient(AppConfig.apiBaseUrl).pollStatus(jobId);
  }

  @override
  Future<PersonSearchStatusResult> cancel(String jobId) {
    return PersonSearchClient(AppConfig.apiBaseUrl).cancel(jobId);
  }

  @override
  Future<PersonSearchStatusResult> open(String jobId) {
    return PersonSearchClient(AppConfig.apiBaseUrl).open(jobId);
  }

  @override
  Future<PersonSearchSessionIndicator> sessionIndicator() {
    return PersonSearchClient(AppConfig.apiBaseUrl).sessionIndicator();
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

  /// Laatst bekende voortgang van de job die op `background-search`/
  /// `search-ready` wordt getoond; `null` buiten die twee schermen.
  PersonSearchStatusResult? _statusResult;
  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    _resumeFromSession();
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    _controller.dispose();
    _fieldFocusNode.dispose();
    super.dispose();
  }

  /// Hervat na in-app-navigatie, herlading of terugkeer binnen dezelfde
  /// geldige sessie automatisch de statuscontrole voor de eerste
  /// niet-terminale of nog-niet-geopende `READY`-job van deze sessie.
  Future<void> _resumeFromSession() async {
    try {
      final indicator = await widget.personSearchSource.sessionIndicator();
      if (!mounted || _screen != _PersonQueryScreen.start) return;
      final resumableJobId = indicator.runningJobIds.isNotEmpty
          ? indicator.runningJobIds.first
          : (indicator.readyUnopenedJobIds.isNotEmpty
                ? indicator.readyUnopenedJobIds.first
                : null);
      if (resumableJobId == null) return;
      final generation = ++_searchGeneration;
      final status = await widget.personSearchSource.pollStatus(resumableJobId);
      if (!mounted || generation != _searchGeneration) return;
      setState(() {
        _submittedQuery = status.originalQuery;
        _applyStatusResult(generation, status);
      });
    } catch (_) {
      // Hervatten is een gemak; bij een fout blijft gewoon het startscherm staan.
    }
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
    _statusResult = null;
    _stopPolling();
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
        case PersonSearchStatus.queued:
        case PersonSearchStatus.running:
          _stillRunning = true;
          _screen = _PersonQueryScreen.liveSearch;
          _enterBackgroundSearch(generation, result.jobId);
        case PersonSearchStatus.ready:
          _screen = _PersonQueryScreen.supportedAnswer;
        case PersonSearchStatus.noEvidence:
        case PersonSearchStatus.partial:
          _screen = _PersonQueryScreen.noReliableSource;
        case PersonSearchStatus.failed:
          _screen = _PersonQueryScreen.sourceOutage;
        case PersonSearchStatus.cancelled:
        case PersonSearchStatus.expired:
          _screen = _PersonQueryScreen.unavailable;
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

  /// Haalt de eerste volledige statusweergave op (starttijd, per-bron
  /// voortgang) voor een job die het synchrone budget overschreed, en
  /// schakelt dan pas naar `background-search`.
  Future<void> _enterBackgroundSearch(int generation, String jobId) async {
    try {
      final status = await widget.personSearchSource.pollStatus(jobId);
      if (!mounted || generation != _searchGeneration) return;
      setState(() => _applyStatusResult(generation, status));
    } catch (_) {
      if (!mounted || generation != _searchGeneration) return;
      setState(() => _screen = _PersonQueryScreen.sourceOutage);
    }
  }

  /// Verwerkt een statusaanvraag-resultaat: blijft op `background-search` en
  /// plant de volgende statuscontrole zolang de job niet terminaal is, of
  /// schakelt bij een terminale status naar het passende scherm.
  void _applyStatusResult(int generation, PersonSearchStatusResult status) {
    _statusResult = status;
    if (!status.status.isTerminal) {
      _screen = _PersonQueryScreen.backgroundSearch;
      _schedulePoll(generation, status.jobId);
      return;
    }
    _stopPolling();
    switch (status.status) {
      case PersonSearchStatus.ready:
        _screen = _PersonQueryScreen.searchReady;
      case PersonSearchStatus.noEvidence:
      case PersonSearchStatus.partial:
        _lastResult = PersonSearchResult(
          jobId: status.jobId,
          status: status.status,
          originalQuery: status.originalQuery,
          refinementMessage: status.refinementMessage,
          answer: status.answer,
          context: status.context,
        );
        _screen = _PersonQueryScreen.noReliableSource;
      case PersonSearchStatus.failed:
        _lastResult = PersonSearchResult(
          jobId: status.jobId,
          status: status.status,
          originalQuery: status.originalQuery,
          context: status.context,
        );
        _screen = _PersonQueryScreen.sourceOutage;
      case PersonSearchStatus.cancelled:
      case PersonSearchStatus.expired:
        _screen = _PersonQueryScreen.unavailable;
      case PersonSearchStatus.queued:
      case PersonSearchStatus.running:
        break; // onbereikbaar: al afgehandeld door de vroege return hierboven
    }
  }

  void _schedulePoll(int generation, String jobId) {
    _pollTimer?.cancel();
    _pollTimer = Timer(_pollInterval, () => _pollOnce(generation, jobId));
  }

  Future<void> _pollOnce(int generation, String jobId) async {
    if (!mounted || generation != _searchGeneration) return;
    try {
      final status = await widget.personSearchSource.pollStatus(jobId);
      if (!mounted || generation != _searchGeneration) return;
      setState(() => _applyStatusResult(generation, status));
    } on PersonSearchJobUnavailableException {
      if (!mounted || generation != _searchGeneration) return;
      setState(() {
        _statusResult = null;
        _screen = _PersonQueryScreen.unavailable;
      });
    } catch (_) {
      // Voorbijgaande netwerkfout: gewoon opnieuw proberen bij de volgende tik.
      _schedulePoll(generation, jobId);
    }
  }

  void _stopPolling() {
    _pollTimer?.cancel();
    _pollTimer = null;
  }

  /// Vanuit `background-search`: navigeert naar `start` zonder de lopende
  /// job te onderbreken. Alleen de voorgrondpolling van dit scherm stopt.
  void _askAnotherQuestionFromBackground() {
    _searchGeneration++;
    _stopPolling();
    setState(() => _screen = _PersonQueryScreen.start);
    _fieldFocusNode.requestFocus();
  }

  Future<void> _stopBackgroundSearch() async {
    final jobId = _statusResult?.jobId;
    _searchGeneration++;
    _stopPolling();
    if (jobId != null) {
      try {
        await widget.personSearchSource.cancel(jobId);
      } catch (_) {
        // Best effort: retentie-opschoning ruimt de job anders vanzelf op.
      }
    }
    if (!mounted) return;
    setState(() => _screen = _PersonQueryScreen.start);
    _fieldFocusNode.requestFocus();
  }

  /// Vanuit `search-ready`: precies één actie die het bijbehorende antwoord
  /// opent.
  Future<void> _viewReadyAnswer() async {
    final status = _statusResult;
    if (status == null) return;
    final generation = _searchGeneration;
    try {
      await widget.personSearchSource.open(status.jobId);
    } catch (_) {
      // Best effort: het antwoord is al opgehaald en kan alsnog getoond worden.
    }
    if (!mounted || generation != _searchGeneration) return;
    setState(() {
      _lastResult = PersonSearchResult(
        jobId: status.jobId,
        status: status.status,
        originalQuery: status.originalQuery,
        answer: status.answer,
        context: status.context,
      );
      _screen = _PersonQueryScreen.supportedAnswer;
    });
  }

  /// Na verwijdering/verlopen: biedt aan de vraag opnieuw in te dienen in
  /// plaats van een oud antwoord als actuele uitkomst te tonen.
  void _retryFromUnavailable() {
    _searchGeneration++;
    _stopPolling();
    setState(() {
      _controller.text = _submittedQuery;
      _controller.selection = TextSelection.collapsed(
        offset: _submittedQuery.length,
      );
      _screen = _PersonQueryScreen.start;
    });
    _fieldFocusNode.requestFocus();
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
      appBar: AppBar(
        title: const Text('Historisch Heemskerk'),
        actions: [SessionIndicatorBadge(source: widget.personSearchSource)],
      ),
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
      case _PersonQueryScreen.backgroundSearch:
        final status = _statusResult!;
        return BackgroundSearchScreen(
          originalQuery: _submittedQuery,
          startedAt: status.createdAt,
          status: status.status,
          openArchievenStatus: status.openArchievenStatus,
          wikidataStatus: status.wikidataStatus,
          onAskAnotherQuestion: _askAnotherQuestionFromBackground,
          onStop: _stopBackgroundSearch,
        );
      case _PersonQueryScreen.searchReady:
        final status = _statusResult!;
        return SearchReadyScreen(
          originalQuery: _submittedQuery,
          completedAt: status.updatedAt,
          openArchievenStatus: status.openArchievenStatus,
          wikidataStatus: status.wikidataStatus,
          onViewAnswer: _viewReadyAnswer,
        );
      case _PersonQueryScreen.unavailable:
        return _JobUnavailableScreen(onRetry: _retryFromUnavailable);
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
        openArchivenSubtitle:
            'Meer dan 100 mogelijke resultaten · verfijning nodig',
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

/// Getoond wanneer een job door de bezoeker gestopt is of door
/// retentie-opschoning verwijderd/verlopen is: toont nooit een oud antwoord
/// als actuele uitkomst, maar meldt duidelijk dat het niet meer beschikbaar
/// is en biedt aan de vraag opnieuw in te dienen.
class _JobUnavailableScreen extends StatelessWidget {
  const _JobUnavailableScreen({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 900),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text('NIET MEER BESCHIKBAAR', style: textTheme.labelLarge),
              const SizedBox(height: 8),
              Text(
                'Deze zoekopdracht is niet meer beschikbaar',
                style: textTheme.headlineMedium,
              ),
              const SizedBox(height: 12),
              PersonQueryStatusMessage(
                label:
                    'Deze zoekopdracht is gestopt of verlopen; het antwoord '
                    'is niet meer beschikbaar in deze sessie.',
                child: const Text(
                  'Deze zoekopdracht is gestopt of verlopen; het antwoord is '
                  'niet meer beschikbaar in deze sessie. Je kunt de vraag '
                  'opnieuw indienen.',
                ),
              ),
              const SizedBox(height: 20),
              Align(
                alignment: Alignment.centerLeft,
                child: OutlinedButton(
                  key: const Key('unavailable-retry'),
                  onPressed: onRetry,
                  style: personQueryFocusedButtonStyle(
                    Theme.of(context).colorScheme.primary,
                  ),
                  child: const Text('Vraag opnieuw indienen'),
                ),
              ),
            ],
          ),
        ),
      ),
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
