output "enabled_apis" {
  description = "APIs enabled by this module."
  value       = [for s in google_project_service.this : s.service]
}

# Other modules depend on this to force ordering: create resources only after APIs are on.
output "ready" {
  description = "Opaque handle to depend on so resources are created only after APIs are enabled."
  value       = join(",", [for s in google_project_service.this : s.id])
}
