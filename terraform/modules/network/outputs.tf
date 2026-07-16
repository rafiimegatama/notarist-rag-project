output "connector_id" {
  description = "VPC Access connector ID for the Cloud Run service, or null when disabled."
  value       = var.enabled ? google_vpc_access_connector.this[0].id : null
}

output "connector_name" {
  description = "VPC Access connector name, or null when disabled."
  value       = var.enabled ? google_vpc_access_connector.this[0].name : null
}

output "egress_ips" {
  description = "The static egress IPs. ALLOWLIST THESE in Supabase (Network Restrictions) and Qdrant Cloud."
  value       = var.enabled ? google_compute_address.nat[*].address : []
}

output "network_id" {
  description = "VPC network ID, or null when disabled."
  value       = var.enabled ? google_compute_network.this[0].id : null
}
