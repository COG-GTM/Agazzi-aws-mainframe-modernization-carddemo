# deploy — IaC AWS di CardDemo (Fase 5D)

`terraform/`: ECS Fargate (servizio `app` dietro ALB + task definition `batch` per i job), RDS
PostgreSQL 16 (password gestita da RDS in Secrets Manager), SQS (+ DLQ) per report/autorizzazioni,
Secrets Manager per i segreti applicativi, S3 per generazioni GDG/report e bucket di archivio Glacier per
il decommissioning, KMS, IAM, CloudWatch Logs.

## Perché Terraform (e non CDK)

- **Nessun toolchain aggiuntivo nel repo**: CDK richiederebbe Node.js o un secondo progetto Java/Maven
  (e modifiche al `pom.xml` padre, vietate in Fase 5D); Terraform è un binario singolo e `terraform validate`
  gira offline in CI senza account AWS.
- **Plan leggibile per il CAB**: `terraform plan` è l'artefatto di change management del runbook T-0
  (passo 0.10 commuta solo `maintenance_page`).
- **Ambienti `dryrun` / `parallel` / `prod`** come tfvars dello stesso modulo, distruggibili per il W5 del
  decommissioning senza toccare `prod`.

## Uso

```
cd deploy/terraform
terraform init -backend=false && terraform validate        # verificato in Fase 5D
terraform init -backend-config=env/<env>.backend.hcl       # solo pipeline PLT
terraform plan -var-file=env/<env>.tfvars
```

`example.tfvars` contiene solo segnaposto. Nessun deploy reale è stato eseguito in Fase 5D.
Prerequisito immagine: il bundle CA RDS (`global-bundle.pem`) in `db_ca_bundle_path`, perché la connessione usa
`sslmode=verify-full`; il container gira con `SPRING_PROFILES_ACTIVE=aws` (`application-aws.yml`).
Variabili d'ambiente passate al container (`CARDDEMO_MESSAGING_TYPE=sqs`, `CARDDEMO_GDG_TYPE=s3`,
`CARDDEMO_GDG_BUCKET`, `CARDDEMO_REPORT_QUEUE_URL`) sono i nomi attesi dagli adapter di 1C/3A: se gli
adapter usano chiavi diverse, aggiornare `local.container_env` in `main.tf`.
