import 'package:flutter/material.dart';

/// Licentiestatus van één specifiek archiefrecord, analoog aan het backendresultaat van
/// `ExternalVerificationLicenseEvaluator` in de module `nl.vdzon.hkh.externalverification`. Los van
/// de bestaande verificatiestatus (`ExternalVerificationStatus`).
enum LicenseStatus { known, unknown }

/// Vaste kleurwaarden voor de licentiestatusweergave. Beide voorgrondkleuren zijn gekozen zodat ze,
/// tegen [background], een contrastratio van minimaal 4.5:1 halen (WCAG 2.1 AA voor normale tekst):
/// `knownForeground` haalt 7.87:1, `unknownForeground` haalt 6.57:1 tegen wit.
class LicenseStatusColors {
  const LicenseStatusColors._();

  static const Color knownForeground = Color(0xFF1B5E20);
  static const Color unknownForeground = Color(0xFFB71C1C);
  static const Color background = Color(0xFFFFFFFF);
}

/// Toont de per-record licentiestatus van een archieven.nl-koppeling met zowel een tekstlabel als
/// een icoon, naast de bestaande verificatie- en privacystatusbadges, zodat de status nooit
/// uitsluitend via kleur wordt gecommuniceerd. Een record zonder zichtbare licentie krijgt altijd
/// het label `License unknown`, ook wanneer andere records uit dezelfde archiefcollectie wel een
/// bekende licentie hebben.
class LicenseStatusView extends StatelessWidget {
  const LicenseStatusView({
    required this.status,
    this.licenseValue,
    super.key,
  });

  final LicenseStatus status;

  /// De vastgestelde licentiewaarde (bijvoorbeeld `CC0`). Uitsluitend gezet wanneer [status]
  /// [LicenseStatus.known] is.
  final String? licenseValue;

  bool get _isKnown => status == LicenseStatus.known;

  String get _label => _isKnown ? 'Licentie bekend' : 'License unknown';

  String get _detail => _isKnown
      ? (licenseValue ?? '')
      : 'Geen hergebruikslicentie gevonden voor dit record.';

  IconData get _icon => _isKnown ? Icons.verified : Icons.help_outline;

  String get _iconSemanticLabel => 'Icoon $_label';

  Color get _color =>
      _isKnown ? LicenseStatusColors.knownForeground : LicenseStatusColors.unknownForeground;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: 'Licentiestatus: $_label',
      child: ColoredBox(
        color: LicenseStatusColors.background,
        child: Padding(
          padding: const EdgeInsets.all(8),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Icon(_icon, color: _color, semanticLabel: _iconSemanticLabel),
              const SizedBox(width: 8),
              Flexible(
                child: Text(
                  _label,
                  style: TextStyle(color: _color, fontWeight: FontWeight.bold),
                ),
              ),
              if (_detail.isNotEmpty) ...[
                const SizedBox(width: 8),
                Flexible(child: Text(_detail, style: TextStyle(color: _color))),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
