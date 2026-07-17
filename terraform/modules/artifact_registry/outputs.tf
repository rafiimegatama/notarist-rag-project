output "repository_id" {
  description = "Artifact Registry repository ID."
  value       = google_artifact_registry_repository.this.repository_id
}

output "repository_name" {
  description = "Fully-qualified repository name."
  value       = google_artifact_registry_repository.this.name
}

output "registry_host" {
  description = "Docker registry hostname, e.g. asia-southeast2-docker.pkg.dev."
  value       = "${var.region}-docker.pkg.dev"
}

output "image_base" {
  description = "Base path for images: <region>-docker.pkg.dev/<project>/<repo>. Append /<image>:<tag>."
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.this.repository_id}"
}
