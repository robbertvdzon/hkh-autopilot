import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Zuivere WCAG 2.x-berekening van de contrastverhouding tussen twee
/// ondoorzichtige kleuren. Geen netwerk, geen fixtures: puur rekenwerk, zodat
/// de leesbaarheidscontrole van hkh-298 (AC4) lokaal en in CI draait.
///
/// Relatieve luminantie volgens
/// https://www.w3.org/TR/WCAG21/#dfn-relative-luminance.
double relativeLuminance(Color color) {
  double channel(double value) {
    return value <= 0.03928
        ? value / 12.92
        : math.pow((value + 0.055) / 1.055, 2.4).toDouble();
  }

  return 0.2126 * channel(color.r) +
      0.7152 * channel(color.g) +
      0.0722 * channel(color.b);
}

/// Contrastverhouding volgens
/// https://www.w3.org/TR/WCAG21/#dfn-contrast-ratio: `(L1 + 0.05) /
/// (L2 + 0.05)` met L1 de lichtste en L2 de donkerste relatieve luminantie.
double contrastRatio(Color foreground, Color background) {
  final a = relativeLuminance(foreground);
  final b = relativeLuminance(background);
  final lighter = math.max(a, b);
  final darker = math.min(a, b);
  return (lighter + 0.05) / (darker + 0.05);
}
