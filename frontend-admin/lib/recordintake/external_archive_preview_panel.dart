import 'package:flutter/material.dart';

import 'admin_record_intake.dart';

/// Vaste kleurwaarden voor het statuslabel van de externe-archiefpreview. Elke voorgrondkleur
/// haalt tegen [background] een contrastratio van minimaal 4.5:1 (WCAG 2.1 AA voor normale tekst):
/// `verifiedForeground` haalt 7.87:1, `noMatchForeground` haalt 5.93:1 en `unreachableForeground`
/// haalt 6.57:1.
class ExternalArchivePreviewStatusColors {
  const ExternalArchivePreviewStatusColors._();

  static const Color verifiedForeground = Color(0xFF1B5E20);
  static const Color noMatchForeground = Color(0xFF8A5A00);
  static const Color unreachableForeground = Color(0xFFB71C1C);
  static const Color background = Color(0xFFFFFFFF);
}

/// Niet-blokkerend paneel "Brongegevens (extern, ter controle)": toont het resultaat van het
/// niet-persisterende previewendpoint en biedt de twee acties om de externe brongegevens al dan
/// niet te gebruiken bij opslaan. De status wordt nooit uitsluitend via kleur gecommuniceerd (een
/// tekstlabel begeleidt de kleur).
class ExternalArchivePreviewPanel extends StatelessWidget {
  const ExternalArchivePreviewPanel({
    required this.loading,
    required this.result,
    required this.confirmed,
    required this.onConfirm,
    required this.onDecline,
    super.key,
  });

  final bool loading;
  final RecordIntakeExternalArchivePreviewResult? result;

  /// `null` zolang de beheerder nog geen keuze heeft gemaakt, anders `true` (bevestigd) of `false`
  /// (expliciet zonder externe brongegevens opgeslagen).
  final bool? confirmed;
  final VoidCallback onConfirm;
  final VoidCallback onDecline;

  bool get _isVerified =>
      result?.status == RecordIntakeExternalArchivePreviewStatus.verified;

  String get _statusLabel {
    switch (result?.status) {
      case RecordIntakeExternalArchivePreviewStatus.verified:
        return 'Geverifieerd';
      case RecordIntakeExternalArchivePreviewStatus.noMatch:
        return 'Geen match';
      default:
        return 'Niet bereikbaar';
    }
  }

  Color get _statusColor {
    switch (result?.status) {
      case RecordIntakeExternalArchivePreviewStatus.verified:
        return ExternalArchivePreviewStatusColors.verifiedForeground;
      case RecordIntakeExternalArchivePreviewStatus.noMatch:
        return ExternalArchivePreviewStatusColors.noMatchForeground;
      default:
        return ExternalArchivePreviewStatusColors.unreachableForeground;
    }
  }

  IconData get _statusIcon {
    switch (result?.status) {
      case RecordIntakeExternalArchivePreviewStatus.verified:
        return Icons.check_circle;
      case RecordIntakeExternalArchivePreviewStatus.noMatch:
        return Icons.help_outline;
      default:
        return Icons.cloud_off;
    }
  }

  String get _iconSemanticLabel => 'Icoon $_statusLabel';

  @override
  Widget build(BuildContext context) {
    return Semantics(
      liveRegion: true,
      container: true,
      explicitChildNodes: true,
      label: 'Brongegevens (extern, ter controle)',
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: ExternalArchivePreviewStatusColors.background,
          border: Border.all(color: Theme.of(context).colorScheme.outline),
          borderRadius: BorderRadius.circular(4),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              'Brongegevens (extern, ter controle)',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            if (loading)
              const Text('Bezig met ophalen...')
            else
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    _statusIcon,
                    color: _statusColor,
                    semanticLabel: _iconSemanticLabel,
                  ),
                  const SizedBox(width: 8),
                  Text(
                    _statusLabel,
                    style: TextStyle(
                      color: _statusColor,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ],
              ),
            if (!loading && _isVerified) ...[
              const SizedBox(height: 8),
              Text('Naam: ${result?.name ?? '-'}'),
              Text('Geboortedatum: ${result?.birthDate ?? '-'}'),
              Text('Sterftedatum: ${result?.deathDate ?? '-'}'),
              Text('Licentie: ${result?.license ?? '-'}'),
              Text('Bron-URI: ${result?.sourceUri ?? '-'}'),
            ],
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                OutlinedButton(
                  key: const Key('confirmExternalArchiveData'),
                  onPressed: onConfirm,
                  child: const Text(
                    'Bevestig brongegevens en gebruik bij record',
                  ),
                ),
                OutlinedButton(
                  key: const Key('declineExternalArchiveData'),
                  onPressed: onDecline,
                  child: const Text('Sla op zonder externe brongegevens'),
                ),
              ],
            ),
            if (confirmed != null) ...[
              const SizedBox(height: 8),
              Text(
                confirmed!
                    ? 'Externe brongegevens worden bij opslaan hergebruikt indien verwerkbaar.'
                    : 'Externe brongegevens worden niet gebruikt bij opslaan.',
              ),
            ],
          ],
        ),
      ),
    );
  }
}
