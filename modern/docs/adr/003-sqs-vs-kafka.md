# ADR-003 — SQS vs Kafka per MQ/TDQ

- **Stato**: accettato
- **Contesto**: MQ request/reply (X1 CP00, X3 CDRD/CDRA) e TDQ (CR00 → JOBS) sono code punto-punto a basso volume, non stream.
- **Decisione**: **Amazon SQS** (LocalStack in locale/test) dietro la porta `MessageChannelPort`; request/reply con `correlationId` come attributo di messaggio. Adattatore `SqsMessageChannel` in `carddemo-app` (attivo con `carddemo.messaging.sqs.enabled=true`), `InMemoryMessageChannel` di Fase 0 come default. La TDQ di CR00 diventa `ReportJobLauncherPort` (nessuna coda).
- **Conseguenze**: nessun broker da gestire; ordering FIFO solo dove serve (code `.fifo`). Kafka resta un'opzione se in Fase 5 servisse event streaming.
