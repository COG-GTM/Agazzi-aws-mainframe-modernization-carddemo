terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.70"
    }
  }
  # Remote state is configured per environment at `terraform init -backend-config=...`
  # (S3 bucket + DynamoDB lock owned by the platform team); none is committed here.
  backend "s3" {}
}

provider "aws" {
  region = var.region
  default_tags {
    tags = {
      Application = "carddemo"
      Environment = var.environment
      ManagedBy   = "terraform"
      Phase       = "5d-cutover"
    }
  }
}
