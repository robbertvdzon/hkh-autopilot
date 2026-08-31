/// Deterministische, volledig client-side interpretatie van een vraag over
/// Heemskerk: naamherkenning en voorzetsel-gebaseerde Heemskerk-disambiguatie.
///
/// Er wordt bewust geen enkele externe aanroep (Open Archieven, Wikidata) in
/// deze klasse gedaan; de interpretatie is een pure functie van de ingevoerde
/// tekst.
class PersonQueryInterpretation {
  const PersonQueryInterpretation({
    required this.firstName,
    required this.lastName,
    required this.yearConstraint,
    required this.eventTypeConstraint,
    required this.heemskerkMentioned,
    required this.heemskerkUnambiguousPlace,
    required this.heemskerkAmbiguous,
    this.placeCandidate,
  });

  /// Voornaam-kandidaat: het eerste woord van de herkende opeenvolgende
  /// hoofdletterwoorden. `null` wanneer geen naam is herkend.
  final String? firstName;

  /// Achternaam-kandidaat: de resterende herkende hoofdletterwoorden,
  /// gescheiden door een spatie. `null` wanneer geen naam is herkend.
  final String? lastName;

  /// Een resterend jaartal (4 cijfers), ongewijzigd bewaard als optionele
  /// zoekbeperking. Deze story toont er geen vervolgscherm voor.
  final String? yearConstraint;

  /// Een resterend gebeurtenistype-woord (geboorte/huwelijk/overlijden/doop),
  /// ongewijzigd bewaard als optionele zoekbeperking.
  final String? eventTypeConstraint;

  /// Of het letterlijke woord "Heemskerk" (case-insensitive) ergens in de
  /// oorspronkelijke tekst voorkomt.
  final bool heemskerkMentioned;

  /// Of "Heemskerk" ergens direct wordt voorafgegaan door `in`/`te`/`uit`/
  /// `van` op de oorspronkelijke tekst: ondubbelzinnig plaats (Q9926), geen
  /// keuzescherm nodig.
  final bool heemskerkUnambiguousPlace;

  /// Of "Heemskerk" als los hoofdletterwoord náást een herkende persoonsnaam
  /// voorkomt, zonder direct voorafgaand voorzetsel: ambigu, keuzescherm
  /// nodig.
  final bool heemskerkAmbiguous;

  /// Of minstens twee opeenvolgende hoofdletterwoorden zijn overgebleven na
  /// normalisatie, d.w.z. of een persoonsnaam herkend is.
  bool get hasRecognizedName => firstName != null && lastName != null;

  /// Plek/gebouw-zoekterm (trefwoord + naam, bv. "Kasteel Assumburg"), `null`
  /// wanneer geen landmark-trefwoord direct naast een hoofdletterwoord is
  /// herkend. Heeft voorrang op [hasRecognizedName] wanneer beide gevonden
  /// zouden worden: de vraag gaat dan naar de plek/gebouw-route.
  final String? placeCandidate;

  bool get hasPlaceCandidate => placeCandidate != null;
}

/// Past de exacte, deterministische drie-staps verwijderregel uit de story toe
/// (vraagwoorden -> functiewoorden/lidwoorden -> plaats-/maandnamenlijst),
/// gevolgd door de opeenvolgende-hoofdletterwoorden-naamherkenning en de
/// voorzetsel-gebaseerde Heemskerk-disambiguatie.
class PersonQueryInterpreter {
  const PersonQueryInterpreter();

  static const _questionWords = {
    'wie',
    'wat',
    'waar',
    'wanneer',
    'welke',
    'hoe',
  };

  static const _functionWords = {
    'was',
    'is',
    'geboren',
    'getrouwd',
    'overleden',
    'gedoopt',
    'de',
    'het',
    'een',
    'van',
    'in',
    'op',
    'te',
    'uit',
  };

  // "Heemskerk" wordt hier bewust niet onvoorwaardelijk opgenomen: zie de
  // aanname in de story-worklog. Het wordt alleen verwijderd wanneer de
  // disambiguatie het al ondubbelzinnig als plaats classificeert.
  static const _fixedContextWords = {
    'noord-holland',
    'nederland',
    'januari',
    'februari',
    'maart',
    'april',
    'mei',
    'juni',
    'juli',
    'augustus',
    'september',
    'oktober',
    'november',
    'december',
  };

  static const _eventTypeWords = {'geboorte', 'huwelijk', 'overlijden', 'doop'};

  /// Landmark-trefwoorden voor de plek/gebouw-route: direct naast minstens
  /// één hoofdletterwoord vormen ze samen de plek/gebouw-zoekterm en krijgen
  /// voorrang op de persoonsherkenning.
  static const _landmarkWords = {
    'kasteel',
    'kerk',
    'molen',
    'toren',
    'gemaal',
    'station',
    'brug',
    'huis',
    'hof',
    'plein',
    'sluis',
    'kapel',
    'klooster',
  };

  static final RegExp _heemskerkWordPattern = RegExp(
    r'\bHeemskerk\b',
    caseSensitive: false,
  );

  static final RegExp _heemskerkUnambiguousPattern = RegExp(
    r'\b(?:in|te|uit|van)\s+Heemskerk\b',
    caseSensitive: false,
  );

  static final RegExp _yearPattern = RegExp(r'^\d{4}$');

  static final RegExp _wordTrimPattern = RegExp(
    r'^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$',
    unicode: true,
  );

  PersonQueryInterpretation interpret(String rawQuery) {
    final heemskerkMentioned = _heemskerkWordPattern.hasMatch(rawQuery);
    final heemskerkUnambiguousPlace = _heemskerkUnambiguousPattern.hasMatch(
      rawQuery,
    );

    var working = rawQuery;
    working = _stripWords(working, _questionWords);
    working = _stripWords(working, _functionWords);
    working = _stripWords(working, _fixedContextWords);
    if (heemskerkUnambiguousPlace) {
      working = working.replaceAll(_heemskerkWordPattern, ' ');
    }

    final tokens = working
        .split(RegExp(r'\s+'))
        .map((token) => token.replaceAll(_wordTrimPattern, ''))
        .where((token) => token.isNotEmpty)
        .toList(growable: false);

    String? yearConstraint;
    String? eventTypeConstraint;
    for (final token in tokens) {
      if (yearConstraint == null && _yearPattern.hasMatch(token)) {
        yearConstraint = token;
      }
      final lower = token.toLowerCase();
      if (eventTypeConstraint == null && _eventTypeWords.contains(lower)) {
        eventTypeConstraint = lower;
      }
    }

    final name = _findRecognizedName(tokens);

    final heemskerkAmbiguous =
        heemskerkMentioned && !heemskerkUnambiguousPlace && name != null;

    // Plek/gebouw-herkenning: dezelfde vraagwoorden-/functiewoorden-/vaste-
    // lijst-verwijdering, maar met het losstaande woord "Heemskerk"
    // onvoorwaardelijk verwijderd (in tegenstelling tot de naamherkenning
    // hierboven, waar dat afhangt van de disambiguatie).
    var placeWorking = rawQuery;
    placeWorking = _stripWords(placeWorking, _questionWords);
    placeWorking = _stripWords(placeWorking, _functionWords);
    placeWorking = _stripWords(placeWorking, _fixedContextWords);
    placeWorking = placeWorking.replaceAll(_heemskerkWordPattern, ' ');
    final placeTokens = placeWorking
        .split(RegExp(r'\s+'))
        .map((token) => token.replaceAll(_wordTrimPattern, ''))
        .where((token) => token.isNotEmpty)
        .toList(growable: false);
    final placeCandidate = _findPlaceCandidate(placeTokens);

    return PersonQueryInterpretation(
      firstName: name?.$1,
      lastName: name?.$2,
      yearConstraint: yearConstraint,
      eventTypeConstraint: eventTypeConstraint,
      heemskerkMentioned: heemskerkMentioned,
      heemskerkUnambiguousPlace: heemskerkUnambiguousPlace,
      heemskerkAmbiguous: heemskerkAmbiguous,
      placeCandidate: placeCandidate,
    );
  }

  /// Zoekt een landmark-trefwoord dat direct naast minstens één
  /// hoofdletterwoord staat en bouwt de plek/gebouw-zoekterm (trefwoord +
  /// naam) in de oorspronkelijke tokenvolgorde, bv. "Kasteel Assumburg".
  String? _findPlaceCandidate(List<String> tokens) {
    for (var i = 0; i < tokens.length; i++) {
      if (!_landmarkWords.contains(tokens[i].toLowerCase())) continue;

      var leftStart = i;
      while (leftStart > 0 && _isCapitalizedWord(tokens[leftStart - 1])) {
        leftStart--;
      }
      var rightEnd = i;
      while (rightEnd < tokens.length - 1 &&
          _isCapitalizedWord(tokens[rightEnd + 1])) {
        rightEnd++;
      }
      if (leftStart == i && rightEnd == i) continue;

      final parts = [
        ...tokens.sublist(leftStart, i),
        _titleCase(tokens[i]),
        ...tokens.sublist(i + 1, rightEnd + 1),
      ];
      return parts.join(' ');
    }
    return null;
  }

  String _titleCase(String token) {
    if (token.isEmpty) return token;
    return token[0].toUpperCase() + token.substring(1).toLowerCase();
  }

  (String, String)? _findRecognizedName(List<String> tokens) {
    var runStart = -1;
    for (var i = 0; i < tokens.length; i++) {
      if (_isCapitalizedWord(tokens[i])) {
        if (runStart == -1) runStart = i;
        final isLastToken = i == tokens.length - 1;
        final nextIsCapitalized =
            !isLastToken && _isCapitalizedWord(tokens[i + 1]);
        if (!nextIsCapitalized) {
          final runLength = i - runStart + 1;
          if (runLength >= 2) {
            final firstName = tokens[runStart];
            final lastName = tokens.sublist(runStart + 1, i + 1).join(' ');
            return (firstName, lastName);
          }
          runStart = -1;
        }
      } else {
        runStart = -1;
      }
    }
    return null;
  }

  bool _isCapitalizedWord(String token) {
    if (token.isEmpty) return false;
    final first = token[0];
    return first == first.toUpperCase() && first != first.toLowerCase();
  }

  String _stripWords(String text, Set<String> words) {
    var result = text;
    for (final word in words) {
      result = result.replaceAll(
        RegExp('\\b${RegExp.escape(word)}\\b', caseSensitive: false),
        ' ',
      );
    }
    return result;
  }
}
