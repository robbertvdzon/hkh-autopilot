import 'package:web/web.dart' as web;

/// Opent [uri] in een nieuw tabblad zonder `window.opener` aan de nieuwe pagina
/// bloot te stellen (`rel="noopener"`-equivalent voor `window.open`).
void openExternalLink(String uri) {
  web.window.open(uri, '_blank', 'noopener');
}
