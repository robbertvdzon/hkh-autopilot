/// Fallback voor platforms zonder `dart:html` (o.a. de Dart VM waarop
/// `flutter test` standaard draait). De gebruikersfrontend wordt uitsluitend als
/// webbuild uitgeleverd; hier is geen echte navigatie nodig of mogelijk.
void openExternalLink(String uri) {}
