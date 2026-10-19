variable "region" {
  type    = string
  default = "eu-south-1"
}

variable "environment" {
  type        = string
  description = "dryrun | parallel | prod"
  validation {
    condition     = contains(["dryrun", "parallel", "prod"], var.environment)
    error_message = "environment must be dryrun, parallel or prod."
  }
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "Subnets for ECS tasks and RDS (at least 2 AZs)."
}

variable "public_subnet_ids" {
  type        = list(string)
  description = "Subnets for the ALB."
}

variable "image" {
  type        = string
  description = "carddemo-app image (ECR URI:tag), built by modern/Dockerfile in CI."
}

variable "certificate_arn" {
  type        = string
  description = "ACM certificate for the HTTPS listener."
}

variable "app_count" {
  type    = number
  default = 2
}

variable "app_cpu" {
  type    = number
  default = 1024
}

variable "app_memory" {
  type    = number
  default = 2048
}

variable "db_instance_class" {
  type    = string
  default = "db.m6g.large"
}

variable "db_allocated_storage" {
  type    = number
  default = 100
}

variable "gdg_retention_days" {
  type        = number
  default     = 35
  description = "Expiry of GDG/report objects in S3 (GdgStore keeps 5 generations logically, ADR-006)."
}

variable "glacier_archive_retention_days" {
  type        = number
  default     = 3650
  description = "Retention of the mainframe decommissioning archive (docs/cutover/decommissioning.md)."
}

variable "allowed_ingress_cidrs" {
  type    = list(string)
  default = ["10.0.0.0/8"]
}

variable "maintenance_page" {
  type        = bool
  default     = true
  description = "503 on /api/* (CLOSEFIL window). Set false at T-0 step 0.8 to open traffic."
}

variable "db_ca_bundle_path" {
  type        = string
  default     = "/opt/carddemo/rds-global-bundle.pem"
  description = "Path in the image of https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem (sslmode=verify-full)."
}
