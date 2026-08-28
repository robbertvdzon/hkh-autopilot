import 'dart:async';

import 'package:flutter/material.dart';

import 'person_search_client.dart';
import 'person_search_models.dart';

/// Vast, op alle schermen van de sessiezoek-route zichtbaar element dat het
/// aantal lopende (niet-terminale) en gereedstaande-niet-geopende jobs van
/// uitsluitend de huidige sessie toont. Ververst zichzelf periodiek; ziet
/// nooit jobs van een andere sessie (de backend is sessiegebonden).
class SessionIndicatorBadge extends StatefulWidget {
  const SessionIndicatorBadge({
    required this.source,
    this.refreshInterval = const Duration(seconds: 5),
    super.key,
  });

  final PersonSearchSource source;
  final Duration refreshInterval;

  @override
  State<SessionIndicatorBadge> createState() => _SessionIndicatorBadgeState();
}

class _SessionIndicatorBadgeState extends State<SessionIndicatorBadge> {
  PersonSearchSessionIndicator? _indicator;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _refresh();
    _timer = Timer.periodic(widget.refreshInterval, (_) => _refresh());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _refresh() async {
    try {
      final indicator = await widget.source.sessionIndicator();
      if (!mounted) return;
      setState(() => _indicator = indicator);
    } catch (_) {
      // De indicator is een gemak, geen kritiek pad: een mislukte ververing
      // laat de laatst bekende waarde gewoon staan.
    }
  }

  @override
  Widget build(BuildContext context) {
    final indicator = _indicator;
    if (indicator == null) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      child: Semantics(
        container: true,
        label:
            'Deze sessie: ${indicator.runningCount} lopende en '
            '${indicator.readyUnopenedCount} gereedstaande, nog niet geopende '
            'zoekopdrachten',
        excludeSemantics: true,
        child: Chip(
          label: Text(
            '${indicator.runningCount} lopend · ${indicator.readyUnopenedCount} gereed',
          ),
        ),
      ),
    );
  }
}
