locals {
  name = "carddemo-${var.environment}"
  # SQS queues replacing the MQ queues of app/app-authorization-ims-db2-mq and the
  # ReportJobLauncherPort (TRANREPT submission from CR00, replaces the CICS TDQ/JOBS writer).
  queues = ["report-requests", "auth-requests", "auth-responses"]
}

# ---------------------------------------------------------------- networking / security groups
resource "aws_security_group" "alb" {
  name   = "${local.name}-alb"
  vpc_id = var.vpc_id
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = var.allowed_ingress_cidrs
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "app" {
  name   = "${local.name}-app"
  vpc_id = var.vpc_id
  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "db" {
  name   = "${local.name}-db"
  vpc_id = var.vpc_id
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }
}

# ---------------------------------------------------------------- KMS / secrets
resource "aws_kms_key" "carddemo" {
  description         = "${local.name} data at rest (RDS, S3, SQS, Secrets Manager)"
  enable_key_rotation = true
}

# The master password is generated and stored by RDS in Secrets Manager (never in state/vars).
resource "aws_db_subnet_group" "db" {
  name       = local.name
  subnet_ids = var.private_subnet_ids
}

resource "aws_db_parameter_group" "pg16" {
  name   = "${local.name}-pg16"
  family = "postgres16"
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
}

resource "aws_db_instance" "carddemo" {
  identifier                    = local.name
  engine                        = "postgres"
  engine_version                = "16"
  instance_class                = var.db_instance_class
  allocated_storage             = var.db_allocated_storage
  storage_encrypted             = true
  kms_key_id                    = aws_kms_key.carddemo.arn
  db_name                       = "carddemo"
  username                      = "carddemo"
  manage_master_user_password   = true
  master_user_secret_kms_key_id = aws_kms_key.carddemo.arn
  db_subnet_group_name          = aws_db_subnet_group.db.name
  parameter_group_name          = aws_db_parameter_group.pg16.name
  vpc_security_group_ids        = [aws_security_group.db.id]
  multi_az                      = var.environment == "prod"
  backup_retention_period       = 14
  deletion_protection           = var.environment == "prod"
  skip_final_snapshot           = var.environment != "prod"
  final_snapshot_identifier     = "${local.name}-final"
  copy_tags_to_snapshot         = true
}

# Application secrets other than the DB password (e.g. JWT signing key of 1C) - values are set
# out of band with `aws secretsmanager put-secret-value`, never from Terraform.
resource "aws_secretsmanager_secret" "app" {
  name       = "${local.name}/app"
  kms_key_id = aws_kms_key.carddemo.arn
}

# ---------------------------------------------------------------- SQS
resource "aws_sqs_queue" "dlq" {
  for_each          = toset(local.queues)
  name              = "${local.name}-${each.key}-dlq"
  kms_master_key_id = aws_kms_key.carddemo.id
}

resource "aws_sqs_queue" "queue" {
  for_each                   = toset(local.queues)
  name                       = "${local.name}-${each.key}"
  kms_master_key_id          = aws_kms_key.carddemo.id
  visibility_timeout_seconds = 300
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = 5
  })
}

# ---------------------------------------------------------------- S3: GDG generations + reports
resource "aws_s3_bucket" "gdg" {
  bucket_prefix = "${local.name}-gdg-"
}

resource "aws_s3_bucket" "archive" {
  bucket_prefix = "${local.name}-mf-archive-"
}

resource "aws_s3_bucket_versioning" "gdg" {
  bucket = aws_s3_bucket.gdg.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_versioning" "archive" {
  bucket = aws_s3_bucket.archive.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "buckets" {
  for_each = { gdg = aws_s3_bucket.gdg.id, archive = aws_s3_bucket.archive.id }
  bucket   = each.value
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.carddemo.arn
    }
  }
}

resource "aws_s3_bucket_public_access_block" "buckets" {
  for_each                = { gdg = aws_s3_bucket.gdg.id, archive = aws_s3_bucket.archive.id }
  bucket                  = each.value
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "gdg" {
  bucket = aws_s3_bucket.gdg.id
  rule {
    id     = "gdg-and-reports"
    status = "Enabled"
    filter {}
    expiration {
      days = var.gdg_retention_days
    }
    noncurrent_version_expiration {
      noncurrent_days = 7
    }
  }
}

# Mainframe decommissioning archive (VSAM/GDG unloads, CSD, JCL, SYSOUT): straight to Glacier
# Deep Archive, object lock against deletion for the legal retention period.
resource "aws_s3_bucket_lifecycle_configuration" "archive" {
  bucket = aws_s3_bucket.archive.id
  rule {
    id     = "glacier"
    status = "Enabled"
    filter {}
    transition {
      days          = 0
      storage_class = "DEEP_ARCHIVE"
    }
    expiration {
      days = var.glacier_archive_retention_days
    }
  }
}

# ---------------------------------------------------------------- IAM
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${local.name}-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_db_instance.carddemo.master_user_secret[0].secret_arn, aws_secretsmanager_secret.app.arn]
  }
  statement {
    actions   = ["kms:Decrypt"]
    resources = [aws_kms_key.carddemo.arn]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

resource "aws_iam_role" "task" {
  name               = "${local.name}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

data "aws_iam_policy_document" "task" {
  statement {
    actions   = ["sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes", "sqs:GetQueueUrl"]
    resources = [for q in aws_sqs_queue.queue : q.arn]
  }
  statement {
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
    resources = [aws_s3_bucket.gdg.arn, "${aws_s3_bucket.gdg.arn}/*"]
  }
  statement {
    actions   = ["kms:Decrypt", "kms:GenerateDataKey"]
    resources = [aws_kms_key.carddemo.arn]
  }
}

resource "aws_iam_role_policy" "task" {
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task.json
}

# ---------------------------------------------------------------- ECS Fargate
resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.name}"
  retention_in_days = 90
}

resource "aws_ecs_cluster" "carddemo" {
  name = local.name
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

locals {
  container_env = [
    { name = "CARDDEMO_DB_URL", value = "jdbc:postgresql://${aws_db_instance.carddemo.endpoint}/carddemo?sslmode=require" },
    { name = "CARDDEMO_DB_USER", value = "carddemo" },
    { name = "CARDDEMO_MESSAGING_TYPE", value = "sqs" },
    { name = "CARDDEMO_GDG_TYPE", value = "s3" },
    { name = "CARDDEMO_GDG_BUCKET", value = aws_s3_bucket.gdg.bucket },
    { name = "CARDDEMO_REPORT_QUEUE_URL", value = aws_sqs_queue.queue["report-requests"].url },
    { name = "AWS_REGION", value = var.region },
  ]
  container_secrets = [
    { name = "CARDDEMO_DB_PASSWORD", valueFrom = "${aws_db_instance.carddemo.master_user_secret[0].secret_arn}:password::" },
  ]
  log_config = {
    logDriver = "awslogs"
    options = {
      awslogs-group         = aws_cloudwatch_log_group.app.name
      awslogs-region        = var.region
      awslogs-stream-prefix = "carddemo"
    }
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = "${local.name}-app"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.app_cpu
  memory                   = var.app_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn
  container_definitions = jsonencode([{
    name             = "app"
    image            = var.image
    essential        = true
    portMappings     = [{ containerPort = 8080, protocol = "tcp" }]
    environment      = local.container_env
    secrets          = local.container_secrets
    logConfiguration = local.log_config
  }])
}

# One-off batch task (tools/cutover/run-job.sh CARDDEMO_JOB_MODE=ecs, EventBridge schedules of 3A).
resource "aws_ecs_task_definition" "batch" {
  family                   = "${local.name}-batch"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.app_cpu
  memory                   = var.app_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn
  container_definitions = jsonencode([{
    name             = "batch"
    image            = var.image
    essential        = true
    command          = ["--server.port=0", "--spring.batch.job.enabled=true"]
    environment      = local.container_env
    secrets          = local.container_secrets
    logConfiguration = local.log_config
  }])
}

resource "aws_ecs_service" "app" {
  name            = "${local.name}-app"
  cluster         = aws_ecs_cluster.carddemo.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.app_count
  launch_type     = "FARGATE"
  network_configuration {
    subnets         = var.private_subnet_ids
    security_groups = [aws_security_group.app.id]
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = 8080
  }
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }
  depends_on = [aws_lb_listener.https]
}

# ---------------------------------------------------------------- ALB
resource "aws_lb" "app" {
  name                       = local.name
  load_balancer_type         = "application"
  internal                   = var.environment != "prod"
  subnets                    = var.public_subnet_ids
  security_groups            = [aws_security_group.alb.id]
  drop_invalid_header_fields = true
}

resource "aws_lb_target_group" "app" {
  name        = local.name
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"
  health_check {
    path    = "/actuator/health/readiness"
    matcher = "200"
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.app.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn
  # T-0 switch: the default action is flipped from the maintenance page to the target group
  # (runbook step 0.8); rollback flips it back (R.2).
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

resource "aws_lb_listener_rule" "maintenance" {
  count        = var.maintenance_page ? 1 : 0
  listener_arn = aws_lb_listener.https.arn
  priority     = 1
  action {
    type = "fixed-response"
    fixed_response {
      content_type = "application/json"
      status_code  = "503"
      message_body = jsonencode({ messages = [{ severity = "ERROR", text = "CardDemo is in maintenance (cutover)" }] })
    }
  }
  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }
}
