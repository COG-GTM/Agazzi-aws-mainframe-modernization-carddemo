output "alb_dns_name" {
  value = aws_lb.app.dns_name
}

output "ecs_cluster" {
  value = aws_ecs_cluster.carddemo.name
}

output "batch_task_definition" {
  value = aws_ecs_task_definition.batch.arn
}

output "app_security_group" {
  value = aws_security_group.app.id
}

output "db_endpoint" {
  value = aws_db_instance.carddemo.endpoint
}

output "db_secret_arn" {
  value = aws_db_instance.carddemo.master_user_secret[0].secret_arn
}

output "gdg_bucket" {
  value = aws_s3_bucket.gdg.bucket
}

output "archive_bucket" {
  value = aws_s3_bucket.archive.bucket
}

output "queue_urls" {
  value = { for k, q in aws_sqs_queue.queue : k => q.url }
}
