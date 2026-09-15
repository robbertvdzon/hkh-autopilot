import 'package:flutter/material.dart';
import 'package:url_launcher/link.dart' as launcher;

/// A real web anchor with keyboard activation and an explicit external target.
class ExternalSourceLink extends StatelessWidget {
  const ExternalSourceLink({required this.label, required this.url, super.key});
  final String label;
  final String url;

  @override
  Widget build(BuildContext context) {
    final uri = Uri.tryParse(url);
    if (uri == null ||
        !uri.hasAuthority ||
        !const ['http', 'https'].contains(uri.scheme)) {
      return Text(label);
    }
    return launcher.Link(
      uri: uri,
      target: launcher.LinkTarget.blank,
      builder: (context, followLink) => TextButton(
        onPressed: followLink,
        child: Text(
          '$label ↗',
          semanticsLabel: '$label (opent in een nieuw tabblad)',
        ),
      ),
    );
  }
}
