import 'package:flutter/material.dart';

const historicalRightsExplanation =
    'Metadatarechten en rechten op gekoppelde digitale objecten of media '
    'worden afzonderlijk beoordeeld. Toegestane metadatarechten betekenen '
    'niet automatisch dat gekoppelde digitale objecten of media zijn '
    'toegestaan. Onbekend betekent dat de bron geen expliciete, '
    'verifieerbare status levert; het betekent niet dat rechten zijn '
    'toegestaan of geweigerd.';

class HistoricalRightsExplanation extends StatefulWidget {
  const HistoricalRightsExplanation({required this.keyPrefix, super.key});

  final String keyPrefix;

  @override
  State<HistoricalRightsExplanation> createState() =>
      _HistoricalRightsExplanationState();
}

class _HistoricalRightsExplanationState
    extends State<HistoricalRightsExplanation> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Align(
        alignment: Alignment.centerLeft,
        child: TextButton.icon(
          key: Key('${widget.keyPrefix}-toggle'),
          onPressed: () => setState(() => _expanded = !_expanded),
          icon: const Icon(Icons.info_outline),
          label: Text(
            _expanded
                ? 'Verberg uitleg over rechten'
                : 'Toon uitleg over rechten',
          ),
        ),
      ),
      if (_expanded)
        Semantics(
          key: Key('${widget.keyPrefix}-text'),
          container: true,
          explicitChildNodes: true,
          label: historicalRightsExplanation,
          child: const Text(historicalRightsExplanation),
        ),
    ],
  );
}
