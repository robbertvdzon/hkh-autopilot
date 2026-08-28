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
}
