# Agent Runtime-integratie

HKH Autopilot gebruikt voor langlopende AI-opdrachten de gedeelde Agent Runtime. De applicatie
voert geen model direct in een HTTP-requestthread uit en bouwt geen eigen technische workerqueue.
Agent Runtime beheert queueing, attempts, leases, retries, deadlines, schema-validatie, resultaten
en artifacts. Het HKH-domein blijft zelf eigenaar van de gebruikersopdracht, voortgang en uitkomst.

Het versievaste externe contract staat in de openbare
[`agent-runtime-v1.yaml`](https://github.com/robbertvdzon/agent-runtime/blob/main/agent-runtime-contracts/src/main/resources/openapi/agent-runtime-v1.yaml).
De gedragsbeschrijving staat in
[`jobs-en-uitvoering.md`](https://github.com/robbertvdzon/agent-runtime/blob/main/docs/jobs-en-uitvoering.md).

## Omgevingen en identiteit

| HKH-omgeving | Runtime-URL | Provider |
| --- | --- | --- |
| lokaal/productie | `https://agent-runtime.vdzonsoftware.nl` | standaard `CODEX` |
| acceptatie | `https://agent-runtime-acceptance.vdzonsoftware.nl` | uitsluitend `MOCKED` |

HKH Autopilot heeft een eigen tenantcredential. Deze credential:

- mag alleen `APPLICATION_WORK` maken, lezen, annuleren en de eigen resultaten/artifacts ophalen;
- ziet geen jobs van Product Factory, Software Factory of het gewone HKH-project;
- heeft geen worker-, beheer- of repositorypublicatierechten;
- mag alleen projectcredentials met prefix `HKH_AUTOPILOT__...` selecteren.

Gebruik nooit het Product Factory-, Software Factory-, worker- of admintoken. Het gewone
`hkh-autopilot` product-ID is evenmin een credentialprefix: voor Runtime-projectcredentials geldt
exact `HKH_AUTOPILOT` en voor concrete keys `HKH_AUTOPILOT__NAAM`.

## Applicatieconfiguratie

De backend leest de volgende namen uit het bestaande `secrets.env`-/procesenvironmentmechanisme:

| Naam | Betekenis |
| --- | --- |
| `HKH_AGENT_RUNTIME_URL` | Basis-URL zonder `/v1` |
| `HKH_AGENT_RUNTIME_TOKEN` | Eigen bearercredential; verplicht en geheim |
| `HKH_AGENT_RUNTIME_PROJECT_PREFIX` | Vast `HKH_AUTOPILOT` |
| `HKH_AGENT_RUNTIME_PROVIDER` | `CODEX` in productie, `MOCKED` op acceptatie |
| `HKH_AGENT_RUNTIME_MODEL` | Standaardmodel voor echte jobs |
| `HKH_AGENT_RUNTIME_EXECUTION_TIMEOUT_SECONDS` | Harde Runtime-deadline, standaard 3600 seconden |

`deploy/configure-agent-runtime-secrets.sh` neemt de eigen consumentcredential zonder weergave over
uit de siblingcheckout `../agent-runtime/secrets.env`, zet lokale en clusterbronbestanden op mode
`0600` en vult alle bovenstaande waarden. `deploy/seal-secrets.sh` maakt daarna afzonderlijke,
namespacegebonden Sealed Secrets voor acceptatie en productie. Alleen ciphertext staat in Git.

## Jobflow

Een toekomstige backendmodule implementeert de `/v1`-client achter een eigen domeinpoort en volgt
deze flow:

1. Leg de lokale domeinopdracht en een stabiele idempotency key duurzaam vast.
2. Dien met `Authorization: Bearer <HKH_AGENT_RUNTIME_TOKEN>` een `POST /v1/jobs` in. Gebruik
   `jobKind: APPLICATION_WORK`, de geconfigureerde provider en het model, één zelfstandige prompt,
   een begrensd JSON-responseschema en de harde uitvoeringstime-out.
3. Bewaar het geretourneerde Runtime-job-ID bij de lokale opdracht en antwoord de gebruiker direct;
   houd geen serverthread open terwijl AI werkt.
4. Lees voortgang via `GET /v1/jobs/{jobId}` en eventueel events via
   `GET /v1/jobs/{jobId}/events`. Poll begrensd en hervat na een procesrestart vanuit het opgeslagen
   job-ID.
5. Lees alleen na `SUCCEEDED` het onveranderlijke resultaat via
   `GET /v1/jobs/{jobId}/result`. Valideer het nogmaals naar het HKH-domein voordat lokale effecten
   worden gepubliceerd.
6. Vertaal `FAILED` en `CANCELLED` naar een veilige, zichtbare lokale eindstatus. Annuleren loopt
   via `POST /v1/jobs/{jobId}/cancel` en blijft idempotent.

Gebruik `environmentKeys` alleen wanneer een job daadwerkelijk projectwaarden nodig heeft. Vraag
uitsluitend de kleinste set namen uit `GET /v1/environment-keys?project=HKH_AUTOPILOT`; stuur nooit
een waarde mee in de jobpayload. De worker injecteert alleen de gekozen keys tijdelijk in de
execution-container.

## Betrouwbaarheids- en testgrenzen

- Een submitfout mag geen tweede logische HKH-opdracht veroorzaken; herhaal met dezelfde
  idempotency key.
- Time-outs op de HTTP-client zijn kort en staan los van de harde uitvoeringstime-out van de job.
- Tokens, prompts met persoonsgegevens en geselecteerde secretwaarden komen niet in logs of
  gebruikersfouten.
- Acceptatie gebruikt alleen server-side `MOCKED`. Een testfixture wordt met een beheercredential
  buiten de HKH-app voorbereid; de applicatie krijgt nooit dat beheercredential.
- Unit- en integratietests gebruiken een fake van de domeinpoort. Contracttests bewijzen daarnaast
  de exacte `/v1`-payload, bearerheader, idempotentie, statusvertaling en hervatting na restart.

Deze integratie is het technische uitvoeringsmechanisme. Een gebruikerssignaal blijft richting en
wordt eerst vertaald naar een concrete, privacyveilige HKH-feature met eigen acceptatiecriteria.
