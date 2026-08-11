import 'package:flutter/material.dart';

import 'external_link_launcher.dart';
import 'record_public_view.dart';

/// Vaste kleurwaarden voor de statusweergave van de sectie "Externe bronverificatie".
/// Beide voorgrondkleuren halen, tegen [background], een contrastratio van minimaal
/// 4.5:1 (WCAG 2.1 AA voor normale tekst): `confirmedForeground` haalt 7.87:1,
/// `neutralForeground` haalt 10.05:1 tegen wit.
class ExternalSourceVerificationColors {
  const ExternalSourceVerificationColors._();

  static const Color confirmedForeground = Color(0xFF1B5E20);
  static const Color neutralForeground = Color(0xFF424242);
  static const Color background = Color(0xFFFFFFFF);
}

/// De ene neutrale, niet-technische melding voor `SAVED_WITHOUT_SOURCE`, `NO_INTAKE`
/// en een gedegradeerd (live opnieuw naar `Blocked` geclassificeerd) `CONFIRMED`-record.
/// Bewust identiek voor alle drie de gevallen, om geen metadata over een eventuele
/// eerdere publicatie te lekken.
const String neutralExternalVerificationMessage =
    'Voor dit record is geen extern geverifieerde bron beschikbaar.';

const String confirmedExternalVerificationStatusLabel = 'Extern geverifieerd';

const String _contentSemanticsId = 'external-source-verification-content';

/// Publieke sectie "Externe bronverificatie" op de recorddetailpagina. Toont bij
/// [RecordPublicStatus.confirmed] naam, geboortejaar, sterftejaar (indien aanwezig),
/// licentie, een externe bronlink en de bevestigingsdatum; toont in alle andere
/// gevallen [neutralExternalVerificationMessage] zonder velden en zonder link. De
/// sectie is in-/uitklapbaar via een toegankelijke knop met `expanded`/`controlsNodes`
/// (het `aria-expanded`/`aria-controls`-equivalent op Flutter web).
class ExternalSourceVerificationSection extends StatefulWidget {
  const ExternalSourceVerificationSection({
    required this.view,
    this.openLink = openExternalLink,
    super.key,
  });

  final RecordPublicView view;
  final void Function(String uri) openLink;

  @override
  State<ExternalSourceVerificationSection> createState() =>
      _ExternalSourceVerificationSectionState();
}

class _ExternalSourceVerificationSectionState
    extends State<ExternalSourceVerificationSection> {
  bool _expanded = true;

  void _toggle() => setState(() => _expanded = !_expanded);

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Semantics(
          header: true,
          headingLevel: 2,
          label: 'Externe bronverificatie',
          excludeSemantics: true,
          child: Text(
            'Externe bronverificatie',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
        ),
        const SizedBox(height: 8),
        _ToggleButton(expanded: _expanded, onPressed: _toggle),
        if (_expanded) ...[
          const SizedBox(height: 8),
          Semantics(
            identifier: _contentSemanticsId,
            container: true,
            explicitChildNodes: true,
            child: _SectionBody(view: widget.view, openLink: widget.openLink),
          ),
        ],
      ],
    );
  }
}

class _ToggleButton extends StatelessWidget {
  const _ToggleButton({required this.expanded, required this.onPressed});

  final bool expanded;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final label = expanded
        ? 'Externe bronverificatie inklappen'
        : 'Externe bronverificatie uitklappen';
    return Semantics(
      button: true,
      expanded: expanded,
      controlsNodes: const {_contentSemanticsId},
      label: label,
      excludeSemantics: true,
      child: InkWell(
        key: const Key('external-source-verification-toggle'),
        onTap: onPressed,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(label),
              const SizedBox(width: 4),
              Icon(expanded ? Icons.expand_less : Icons.expand_more),
            ],
          ),
        ),
      ),
    );
  }
}

class _SectionBody extends StatelessWidget {
  const _SectionBody({required this.view, required this.openLink});

  final RecordPublicView view;
  final void Function(String uri) openLink;

  @override
  Widget build(BuildContext context) {
    if (view.status != RecordPublicStatus.confirmed) {
      return Semantics(
        container: true,
        label: neutralExternalVerificationMessage,
        excludeSemantics: true,
        child: const Text(neutralExternalVerificationMessage),
      );
    }
    return _ConfirmedBody(view: view, openLink: openLink);
  }
}

class _ConfirmedBody extends StatelessWidget {
  const _ConfirmedBody({required this.view, required this.openLink});

  final RecordPublicView view;
  final void Function(String uri) openLink;

  @override
  Widget build(BuildContext context) {
    final color = ExternalSourceVerificationColors.confirmedForeground;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.verified,
              color: color,
              semanticLabel: 'Icoon $confirmedExternalVerificationStatusLabel',
            ),
            const SizedBox(width: 8),
            Text(
              confirmedExternalVerificationStatusLabel,
              style: TextStyle(color: color, fontWeight: FontWeight.bold),
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (view.name != null) Text(view.name!),
        if (view.birthYear != null) Text('Geboortejaar: ${view.birthYear}'),
        if (view.deathYear != null) Text('Sterftejaar: ${view.deathYear}'),
        if (view.license != null) Text('Licentie: ${view.license}'),
        if (view.sourceUri != null) ...[
          const SizedBox(height: 8),
          _ExternalSourceLink(uri: view.sourceUri!, openLink: openLink),
        ],
        if (view.confirmedAt != null) ...[
          const SizedBox(height: 8),
          Text('Bevestigd door beheerder op ${_formatDate(view.confirmedAt!)}'),
        ],
      ],
    );
  }

  static String _formatDate(DateTime value) {
    final local = value.toLocal();
    return '${local.day.toString().padLeft(2, '0')}-'
        '${local.month.toString().padLeft(2, '0')}-${local.year}';
  }
}

/// Klikbare, met zichtbare tekst gelabelde link naar de externe bron. Opent in een
/// nieuw tabblad zonder `window.opener` bloot te stellen; het aria-label kondigt dat
/// programmatisch aan voor schermlezers, naar het patroon van
/// `ExternalVerificationLinkView` in `frontend-admin`. Volledig met het toetsenbord
/// (Tab + Enter) bereikbaar en activeerbaar via de onderliggende `InkWell`.
class _ExternalSourceLink extends StatelessWidget {
  const _ExternalSourceLink({required this.uri, required this.openLink});

  final String uri;
  final void Function(String uri) openLink;

  static const String linkLabel = 'Bekijk bron';
  static const String linkSemanticLabel =
      'Bekijk bron (opent externe bron in nieuw tabblad)';

  @override
  Widget build(BuildContext context) {
    return Semantics(
      link: true,
      label: linkSemanticLabel,
      excludeSemantics: true,
      child: InkWell(
        key: const Key('external-source-link'),
        onTap: () => openLink(uri),
        child: const Text(
          linkLabel,
          style: TextStyle(
            color: Colors.blue,
            decoration: TextDecoration.underline,
          ),
        ),
      ),
    );
  }
}

/// Publieke recorddetailpagina: laadt de afgeleide status via [RecordPublicSource]
/// en toont de sectie "Externe bronverificatie". Niet gekoppeld aan de "Ontdek"-
/// nieuwszoekfunctie (`GET /api/news`); levert er geen resultaten aan en ontvangt er
/// geen van.
class RecordDetailPage extends StatefulWidget {
  const RecordDetailPage({
    required this.localIdentifier,
    required this.source,
    super.key,
  });

  final String localIdentifier;
  final RecordPublicSource source;

  @override
  State<RecordDetailPage> createState() => _RecordDetailPageState();
}

class _RecordDetailPageState extends State<RecordDetailPage> {
  late Future<RecordPublicView> _record;

  @override
  void initState() {
    super.initState();
    _record = widget.source.loadRecord(widget.localIdentifier);
  }

  void _retry() {
    setState(() {
      _record = widget.source.loadRecord(widget.localIdentifier);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Record')),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: FutureBuilder<RecordPublicView>(
            future: _record,
            builder: (context, snapshot) {
              if (snapshot.connectionState != ConnectionState.done) {
                return const Center(child: CircularProgressIndicator());
              }
              if (snapshot.hasError) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Text('De recordgegevens konden niet worden geladen.'),
                    const SizedBox(height: 12),
                    OutlinedButton.icon(
                      key: const Key('record-detail-retry'),
                      onPressed: _retry,
                      icon: const Icon(Icons.refresh),
                      label: const Text('Opnieuw proberen'),
                    ),
                  ],
                );
              }
              return ExternalSourceVerificationSection(
                view: snapshot.requireData,
              );
            },
          ),
        ),
      ),
    );
  }
}
