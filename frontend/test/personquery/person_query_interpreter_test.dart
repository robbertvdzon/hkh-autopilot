import 'package:flutter_test/flutter_test.dart';
import 'package:hkh_app/personquery/person_query_interpreter.dart';

void main() {
  const interpreter = PersonQueryInterpreter();

  test('herkent de startscherm-voorbeeldvraag als naam met jaartal', () {
    final result = interpreter.interpret(
      'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?',
    );

    expect(result.hasRecognizedName, isTrue);
    expect(result.firstName, 'Nicolaas');
    expect(result.lastName, 'Jacobus Sinnige');
    expect(result.yearConstraint, '1878');
    expect(result.heemskerkMentioned, isFalse);
    expect(result.heemskerkUnambiguousPlace, isFalse);
    expect(result.heemskerkAmbiguous, isFalse);
  });

  test('herkent de epic-vraag over Nicolaas Jacobus Sinnige en behandelt '
      '"in Heemskerk" als ondubbelzinnige plaats zonder keuzescherm', () {
    final result = interpreter.interpret(
      'Wie was Nicolaas Jacobus Sinnige, geboren in Heemskerk in 1878?',
    );

    expect(result.hasRecognizedName, isTrue);
    expect(result.firstName, 'Nicolaas');
    expect(result.lastName, 'Jacobus Sinnige');
    expect(result.yearConstraint, '1878');
    expect(result.heemskerkMentioned, isTrue);
    expect(result.heemskerkUnambiguousPlace, isTrue);
    expect(result.heemskerkAmbiguous, isFalse);
  });

  test('"Cornelis Heemskerk" is ambigu: Heemskerk staat los naast een herkende '
      'naam zonder voorafgaand voorzetsel', () {
    final result = interpreter.interpret('Cornelis Heemskerk');

    expect(result.hasRecognizedName, isTrue);
    expect(result.firstName, 'Cornelis');
    expect(result.lastName, 'Heemskerk');
    expect(result.heemskerkMentioned, isTrue);
    expect(result.heemskerkUnambiguousPlace, isFalse);
    expect(result.heemskerkAmbiguous, isTrue);
  });

  test('"geboren in Heemskerk" alleen levert geen naam op maar wél een '
      'ondubbelzinnige plaatsbetekenis', () {
    final result = interpreter.interpret('geboren in Heemskerk');

    expect(result.hasRecognizedName, isFalse);
    expect(result.heemskerkMentioned, isTrue);
    expect(result.heemskerkUnambiguousPlace, isTrue);
    expect(result.heemskerkAmbiguous, isFalse);
  });

  test('een vraag zonder herkenbare naam levert geen naam op', () {
    final result = interpreter.interpret(
      'Wat gebeurde er in Heemskerk in 1878?',
    );

    expect(result.hasRecognizedName, isFalse);
    expect(result.firstName, isNull);
    expect(result.lastName, isNull);
    expect(result.yearConstraint, '1878');
    expect(result.heemskerkUnambiguousPlace, isTrue);
    expect(result.heemskerkAmbiguous, isFalse);
  });

  test(
    'precies één overblijvend hoofdletterwoord wordt nooit als naam herkend',
    () {
      final result = interpreter.interpret('Wie was Sinnige?');

      expect(result.hasRecognizedName, isFalse);
      expect(result.firstName, isNull);
      expect(result.lastName, isNull);
    },
  );

  test('herkent een resterend gebeurtenistype-woord als losse beperking', () {
    final result = interpreter.interpret(
      'Toon de geboorte van Nicolaas Jacobus Sinnige',
    );

    expect(result.hasRecognizedName, isTrue);
    expect(result.firstName, 'Nicolaas');
    expect(result.lastName, 'Jacobus Sinnige');
    expect(result.eventTypeConstraint, 'geboorte');
  });

  test(
    'verwijdert functiewoorden en plaats-/maandnamen bij de normalisatie',
    () {
      final result = interpreter.interpret(
        'Waar was Trijntje Beentjes getrouwd in Noord-Holland in juni?',
      );

      expect(result.hasRecognizedName, isTrue);
      expect(result.firstName, 'Trijntje');
      expect(result.lastName, 'Beentjes');
    },
  );

  test('herkent de voorbeeldvraag "Wat is Kasteel Assumburg?" als '
      'plek/gebouw-zoekterm', () {
    final result = interpreter.interpret('Wat is Kasteel Assumburg?');

    expect(result.hasPlaceCandidate, isTrue);
    expect(result.placeCandidate, 'Kasteel Assumburg');
  });

  test('herkent een landmark-trefwoord ná de naam', () {
    final result = interpreter.interpret('Wat is Assumburg Kasteel?');

    expect(result.hasPlaceCandidate, isTrue);
    expect(result.placeCandidate, 'Assumburg Kasteel');
  });

  test(
    'landmark-trefwoord naast een naam met "Heemskerk" ertussen verwijderd',
    () {
      final result = interpreter.interpret(
        'Wat is Kasteel Assumburg in Heemskerk?',
      );

      expect(result.hasPlaceCandidate, isTrue);
      expect(result.placeCandidate, 'Kasteel Assumburg');
    },
  );

  test('een landmark-trefwoord zonder aangrenzend hoofdletterwoord levert '
      'geen plek/gebouw-kandidaat op', () {
    final result = interpreter.interpret('Wat is een kasteel?');

    expect(result.hasPlaceCandidate, isFalse);
    expect(result.placeCandidate, isNull);
  });

  test('landmark-herkenning krijgt voorrang op persoonsherkenning wanneer '
      'beide zouden kunnen matchen', () {
    final result = interpreter.interpret('Wat is Kasteel Assumburg?');

    expect(result.hasPlaceCandidate, isTrue);
    expect(result.hasRecognizedName, isTrue);
    expect(result.placeCandidate, 'Kasteel Assumburg');
  });

  test('zonder landmark-trefwoord blijft het bestaande persoonsgedrag '
      'ongewijzigd (>=2 opeenvolgende hoofdletterwoorden)', () {
    final result = interpreter.interpret(
      'Wie was Nicolaas Jacobus Sinnige, geboren in 1878?',
    );

    expect(result.hasPlaceCandidate, isFalse);
    expect(result.hasRecognizedName, isTrue);
  });

  test('een vraag zonder naam en zonder landmark-kandidaat levert geen van '
      'beide op', () {
    final result = interpreter.interpret('Wat gebeurde er in Heemskerk?');

    expect(result.hasPlaceCandidate, isFalse);
    expect(result.hasRecognizedName, isFalse);
  });
}
