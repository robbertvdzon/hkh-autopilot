# hkh-156 - Worklog

## Documenter

- De story-diff, parent-worklog en relevante factory-instructies gecontroleerd.
- README’s, de algemene development-handleiding, de functionele en technische factory-specs en de
  story-eindsamenvatting aangevuld met de retry-context, behouds- en vervangingssemantiek.
- Vastgelegd dat retry bij gedeeltelijke bronuitval, `SOURCE_FAILURE` en transportfouten dezelfde
  genormaliseerde context gebruikt; een lopende retry geen tweede aanvraag accepteert; een geslaagde
  retry de vorige uitkomst volledig vervangt; en een mislukte retry veilige foutinformatie apart
  toont terwijl geldige deelresultaten behouden blijven.
- Geen productiecode, tests of infrastructuur gewijzigd.
