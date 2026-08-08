import 'package:flutter/material.dart';

/// Toont een link naar het externe archiefrecord op archieven.nl waarmee een lokaal record extern
/// is geverifieerd. De link opent semantisch een externe bron in een nieuw tabblad; het aria-label
/// kondigt dat programmatisch aan voor schermlezers, naar de bestaande toegankelijkheidsconventies
/// van `frontend-admin`.
class ExternalVerificationLinkView extends StatelessWidget {
  const ExternalVerificationLinkView({
    required this.externalUri,
    this.onTap,
    super.key,
  });

  /// De resolvebare archieven.nl-URI, bijvoorbeeld
  /// `http://opendata.archieven.nl/id/<adtid>/<guid>`.
  final String externalUri;

  final VoidCallback? onTap;

  static const String linkLabel = 'Bekijk bron op archieven.nl';
  static const String linkSemanticLabel =
      'Bekijk bron op archieven.nl (opent externe bron in nieuw tabblad)';

  @override
  Widget build(BuildContext context) {
    return Semantics(
      link: true,
      label: linkSemanticLabel,
      excludeSemantics: true,
      child: InkWell(
        onTap: onTap,
        child: const Text(
          linkLabel,
          style: TextStyle(color: Colors.blue, decoration: TextDecoration.underline),
        ),
      ),
    );
  }
}
