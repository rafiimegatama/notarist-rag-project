output "bucket_name" {
  description = "Name of the document bucket. This is the value for the app's GCS_BUCKET env var."
  value       = google_storage_bucket.documents.name
}

output "bucket_url" {
  description = "gs:// URL of the document bucket."
  value       = google_storage_bucket.documents.url
}

output "bucket_self_link" {
  description = "Self link of the document bucket."
  value       = google_storage_bucket.documents.self_link
}
