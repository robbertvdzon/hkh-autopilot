import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';

/// Onder deze breedte (logische pixels) wordt de mobile-uitwerking van elk
/// personquery-scherm getoond; erboven de desktop-uitwerking. Elk scherm
/// heeft precies één van elk.
const double kPersonQueryMobileBreakpoint = 700;

/// Gedeelde `ButtonStyle` naar de bestaande repo-conventie: een
/// contrasterende rand van 3px wanneer de knop toetsenbordfocus heeft.
ButtonStyle personQueryFocusedButtonStyle(Color focusBorderColor) {
  return ButtonStyle(
    side: WidgetStateProperty.resolveWith((states) {
      if (!states.contains(WidgetState.focused)) return null;
      return BorderSide(color: focusBorderColor, width: 3);
    }),
  );
}

/// Statuskopie naar de bestaande `SemanticsRole.status`-conventie uit
/// `technical-spec.md`: een eigen container met exact één betekenisvol label,
/// zonder aanvullende `liveRegion`-vlag of programmatische focus.
class PersonQueryStatusMessage extends StatelessWidget {
  const PersonQueryStatusMessage({
    required this.label,
    required this.child,
    super.key,
  });

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      role: SemanticsRole.status,
      label: label,
      excludeSemantics: true,
      child: child,
    );
  }
}
