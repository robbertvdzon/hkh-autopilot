import 'package:flutter/material.dart';

/// Classificatiestatus van een genealogisch record, analoog aan het backendresultaat van
/// `PrivacyClassifier` in de module `nl.vdzon.hkh.privacyclassification`.
enum PrivacyClassificationStatus { processable, blocked }

/// Vaste kleurwaarden voor de statusweergave. Beide voorgrondkleuren zijn gekozen zodat ze, tegen
/// [background], een contrastratio van minimaal 4.5:1 halen (WCAG 2.1 AA voor normale tekst):
/// `processableForeground` haalt 7.87:1, `blockedForeground` haalt 6.57:1 tegen wit.
class PrivacyClassificationStatusColors {
  const PrivacyClassificationStatusColors._();

  static const Color processableForeground = Color(0xFF1B5E20);
  static const Color blockedForeground = Color(0xFFB71C1C);
  static const Color background = Color(0xFFFFFFFF);
}

/// Toont de privacyclassificatiestatus van een genealogisch record met zowel een tekstlabel als
/// een icoon, zodat de status nooit uitsluitend via kleur wordt gecommuniceerd.
class PrivacyClassificationStatusView extends StatelessWidget {
  const PrivacyClassificationStatusView({
    required this.status,
    required this.reason,
    super.key,
  });

  final PrivacyClassificationStatus status;

  /// Leesbare classificatiereden, bijvoorbeeld `PrivacyClassificationReasons.PROCESSABLE` of
  /// `Bevat gegevens van levende nabestaande` uit het backendresultaat.
  final String reason;

  bool get _isProcessable => status == PrivacyClassificationStatus.processable;

  String get _label => _isProcessable ? 'Verwerkbaar' : 'Geblokkeerd';

  IconData get _icon => _isProcessable ? Icons.check_circle : Icons.block;

  String get _iconSemanticLabel => 'Icoon $_label';

  Color get _color => _isProcessable
      ? PrivacyClassificationStatusColors.processableForeground
      : PrivacyClassificationStatusColors.blockedForeground;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: 'Privacyclassificatie: $_label',
      child: ColoredBox(
        color: PrivacyClassificationStatusColors.background,
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
              const SizedBox(width: 8),
              Flexible(child: Text(reason, style: TextStyle(color: _color))),
            ],
          ),
        ),
      ),
    );
  }
}
