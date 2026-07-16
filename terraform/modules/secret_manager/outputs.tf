output "secret_ids" {
  description = "Map of short name => full secret ID, e.g. { postgres-password = \"notarist-prod-postgres-password\" }."
  value       = { for k, s in google_secret_manager_secret.this : k => s.secret_id }
}

output "secret_names" {
  description = "Map of short name => fully-qualified secret resource name."
  value       = { for k, s in google_secret_manager_secret.this : k => s.name }
}

output "populate_commands" {
  description = "Copy-pasteable commands to write each secret's value out of band, before the first deploy."
  value = [
    for k, s in google_secret_manager_secret.this :
    "gcloud secrets versions add ${s.secret_id} --data-file=- --project=${var.project_id}   # ${var.secrets[k]}"
  ]
}
