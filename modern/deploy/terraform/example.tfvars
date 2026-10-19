# Example only (placeholders, no secrets). Real values live in the platform team's tfvars.
environment        = "dryrun"
vpc_id             = "vpc-00000000000000000"
private_subnet_ids = ["subnet-00000000000000001", "subnet-00000000000000002"]
public_subnet_ids  = ["subnet-00000000000000003", "subnet-00000000000000004"]
image              = "000000000000.dkr.ecr.eu-south-1.amazonaws.com/carddemo-app:0.1.0"
certificate_arn    = "arn:aws:acm:eu-south-1:000000000000:certificate/00000000-0000-0000-0000-000000000000"
