variable "enabled" {
  description = "Build the VPC connector + Cloud NAT. Only needed when Supabase/Qdrant must allowlist a static source IP."
  type        = bool
  default     = false
}

variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "region" {
  description = "Region. Must match the Cloud Run region — a connector is regional and cannot be used cross-region."
  type        = string
}

variable "environment" {
  description = "Environment name (dev|staging|prod)."
  type        = string
}

variable "name_prefix" {
  description = "Prefix for network resource names."
  type        = string
}

variable "connector_cidr" {
  description = "A /28 for the connector subnet. Must not overlap anything you peer with."
  type        = string
  default     = "10.8.0.0/28"

  validation {
    condition     = can(cidrnetmask(var.connector_cidr))
    error_message = "connector_cidr must be a valid CIDR block (a /28 is required by Serverless VPC Access)."
  }
}

variable "connector_min_instances" {
  description = "Minimum connector instances. These run 24/7 and are billed as such; 2 is the platform minimum."
  type        = number
  default     = 2
}

variable "connector_max_instances" {
  description = "Maximum connector instances."
  type        = number
  default     = 3
}

variable "connector_machine_type" {
  description = "Connector machine type. e2-micro is the cheapest and is ample for JDBC + HTTP egress."
  type        = string
  default     = "e2-micro"
}

variable "nat_ip_count" {
  description = "How many static egress IPs to reserve. More than 1 only helps if you exhaust NAT ports; each is an address you must allowlist downstream."
  type        = number
  default     = 1
}

variable "flow_log_sampling" {
  description = "VPC flow log sampling rate, 0.0-1.0. 1.0 logs every flow (loud, complete); 0.5 halves the cost."
  type        = number
  default     = 0.5
}
