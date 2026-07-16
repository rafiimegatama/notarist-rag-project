/**
 * Artifact Registry — the Docker repository Cloud Run pulls the Notarist image from.
 *
 * Cleanup policies keep the repo from growing without bound. They are in DRY-RUN mode by default:
 * a policy that silently deletes an image a Cloud Run revision still references would make that
 * revision unrollbackable. Run with dry_run = true, read the "would delete" logs, and only then
 * flip it off.
 */

resource "google_artifact_registry_repository" "this" {
  project       = var.project_id
  location      = var.region
  repository_id = var.repository_id
  description   = "Notarist backend container images (${var.environment})"
  format        = "DOCKER"

  docker_config {
    immutable_tags = var.immutable_tags
  }

  # Keep every image that is tagged with a release/deploy tag.
  cleanup_policies {
    id     = "keep-tagged-releases"
    action = "KEEP"
    condition {
      tag_state    = "TAGGED"
      tag_prefixes = ["v", "release-", "prod-"]
    }
  }

  # Always keep the most recent N versions, tagged or not, so rollback targets survive.
  cleanup_policies {
    id     = "keep-recent"
    action = "KEEP"
    most_recent_versions {
      keep_count = var.keep_recent_versions
    }
  }

  # Delete untagged images (intermediate build layers, superseded :latest pushes) after a grace
  # period long enough that an in-flight rollback still has something to roll back to.
  cleanup_policies {
    id     = "delete-old-untagged"
    action = "DELETE"
    condition {
      tag_state  = "UNTAGGED"
      older_than = var.untagged_retention
    }
  }

  cleanup_policy_dry_run = var.cleanup_dry_run

  labels = var.labels
}

# Cloud Run's runtime SA must be able to PULL the image it runs. Granting reader on the repository
# rather than project-wide artifactregistry.reader keeps the blast radius to this one repo.
resource "google_artifact_registry_repository_iam_member" "runtime_reader" {
  for_each = toset(var.reader_members)

  project    = var.project_id
  location   = google_artifact_registry_repository.this.location
  repository = google_artifact_registry_repository.this.name
  role       = "roles/artifactregistry.reader"
  member     = each.value
}

resource "google_artifact_registry_repository_iam_member" "writer" {
  for_each = toset(var.writer_members)

  project    = var.project_id
  location   = google_artifact_registry_repository.this.location
  repository = google_artifact_registry_repository.this.name
  role       = "roles/artifactregistry.writer"
  member     = each.value
}
