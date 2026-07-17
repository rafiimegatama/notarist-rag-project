/**
 * Cloud Storage — the document bucket.
 *
 * This holds notarial legal documents: akta, sertifikat, fidusia, and the OCR/chunk artefacts
 * derived from them. Under the project's own data classification that is CONFIDENTIAL to
 * STRICTLY_CONFIDENTIAL, so the defaults here are deliberately strict:
 *
 *   - public_access_prevention = "enforced": the bucket CANNOT be made public, even by a future
 *     careless IAM change. This is not the default on GCS and it is the single most valuable line
 *     in this file.
 *   - uniform_bucket_level_access: no per-object ACLs. Access is IAM only, so "who can read this
 *     document?" has exactly one answer to audit instead of one per object.
 *   - versioning: an overwrite or a bad OCR pass cannot destroy the original.
 *   - retention_policy (optional): a WORM lock for the 7-year retention the audit domain requires.
 *
 * The app writes here through the Cloud Run runtime SA (ADC) and hands clients V4 SIGNED URLs for
 * direct upload — which is why CORS is configured: without it the browser preflight to the signed
 * URL fails and uploads break in a way that looks like a bucket permission error.
 */

resource "google_storage_bucket" "documents" {
  project  = var.project_id
  name     = var.bucket_name
  location = var.location

  storage_class               = var.storage_class
  uniform_bucket_level_access = true
  public_access_prevention    = "enforced"

  # Refuse to delete a non-empty bucket outside dev. Losing notarial originals to a `terraform
  # destroy` is not a recoverable mistake.
  force_destroy = var.force_destroy

  versioning {
    enabled = var.versioning_enabled
  }

  dynamic "encryption" {
    for_each = var.kms_key_name == null ? [] : [1]
    content {
      default_kms_key_name = var.kms_key_name
    }
  }

  # WORM retention. Once LOCKED this cannot be shortened or removed — by anyone, including project
  # owners. Leave retention_locked = false until the duration is definitely right.
  dynamic "retention_policy" {
    for_each = var.retention_seconds == null ? [] : [1]
    content {
      retention_period = var.retention_seconds
      is_locked        = var.retention_locked
    }
  }

  # Age out noncurrent versions so versioning does not grow storage cost without bound.
  lifecycle_rule {
    condition {
      num_newer_versions = var.noncurrent_versions_to_keep
      with_state         = "ARCHIVED"
    }
    action {
      type = "Delete"
    }
  }

  # Tier cold objects down. Documents are read heavily right after ingestion, then rarely.
  dynamic "lifecycle_rule" {
    for_each = var.enable_storage_tiering ? [1] : []
    content {
      condition {
        age        = var.nearline_after_days
        with_state = "LIVE"
      }
      action {
        type          = "SetStorageClass"
        storage_class = "NEARLINE"
      }
    }
  }

  dynamic "lifecycle_rule" {
    for_each = var.enable_storage_tiering ? [1] : []
    content {
      condition {
        age        = var.coldline_after_days
        with_state = "LIVE"
      }
      action {
        type          = "SetStorageClass"
        storage_class = "COLDLINE"
      }
    }
  }

  # Abort dangling resumable uploads so half-finished multipart writes stop accruing cost.
  lifecycle_rule {
    condition {
      days_since_noncurrent_time = 1
      with_state                 = "ARCHIVED"
      matches_prefix             = []
    }
    action {
      type = "Delete"
    }
  }

  dynamic "cors" {
    for_each = length(var.cors_origins) > 0 ? [1] : []
    content {
      origin          = var.cors_origins
      method          = ["GET", "HEAD", "PUT", "POST"]
      response_header = ["Content-Type", "Content-MD5", "x-goog-resumable", "ETag"]
      max_age_seconds = 3600
    }
  }

  dynamic "logging" {
    for_each = var.access_log_bucket == null ? [] : [1]
    content {
      log_bucket        = var.access_log_bucket
      log_object_prefix = "gcs-access/${var.bucket_name}/"
    }
  }

  labels = var.labels
}

# The app needs to read, write and delete objects — but NOT administer the bucket (change its IAM,
# its retention policy, or delete it). roles/storage.objectAdmin is exactly that boundary;
# roles/storage.admin would hand the runtime the power to disable its own guardrails.
resource "google_storage_bucket_iam_member" "object_admins" {
  for_each = toset(var.object_admin_members)

  bucket = google_storage_bucket.documents.name
  role   = "roles/storage.objectAdmin"
  member = each.value
}

resource "google_storage_bucket_iam_member" "object_viewers" {
  for_each = toset(var.object_viewer_members)

  bucket = google_storage_bucket.documents.name
  role   = "roles/storage.objectViewer"
  member = each.value
}
