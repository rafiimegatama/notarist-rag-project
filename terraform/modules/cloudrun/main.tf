/**
 * Cloud Run — the Notarist backend service.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TWO THINGS HERE ARE LOAD-BEARING AND NON-OBVIOUS.
 *
 * 1. THE JWT KEYS ARE FILES, NOT ENV VARS.
 *
 *    JwtService reads its RSA keypair with Files.readString(Paths.get(path)) — it wants a PEM on
 *    disk, not a string in the environment. So the JWT secrets are mounted as a Secret Manager
 *    VOLUME at /etc/notarist/keys, and JWT_PRIVATE_KEY_PATH points into that mount. Passing the PEM
 *    as an env var would fail at startup, and "fix" it by chmod-ing a key into the image — which is
 *    how private keys end up in a container registry. They are mounted, never baked.
 *
 * 2. THE SCHEDULERS NEED CPU WHEN NO REQUEST IS IN FLIGHT.
 *
 *    The app runs three in-process @Scheduled jobs: the ingestion queue poller, the retry-policy
 *    sweep, and the token-deny-list cleanup. Cloud Run throttles an instance's CPU to ~zero between
 *    requests unless you tell it not to. With the defaults (scale-to-zero, cpu_idle = true) those
 *    jobs simply DO NOT RUN — the ingestion pipeline would appear to accept uploads and then never
 *    process them, with no error anywhere.
 *
 *    So: min_instance_count >= 1 and cpu_idle = false. That means the service does not scale to
 *    zero and you pay for one always-on instance. That is the real cost of running an in-process
 *    scheduler on a request-driven platform. The alternative — extract the jobs behind HTTP
 *    endpoints and drive them from Cloud Scheduler — is a better fit for Cloud Run, but it is a
 *    Java change and Java is out of scope here. See terraform/README "Blockers".
 * ─────────────────────────────────────────────────────────────────────────────
 */

locals {
  # Secrets exposed to the process as environment variables.
  env_secrets = {
    POSTGRES_PASSWORD   = var.secret_ids["postgres-password"]
    APP_ENCRYPTION_KEY  = var.secret_ids["app-encryption-key"]
    APP_ENCRYPTION_SALT = var.secret_ids["app-encryption-salt"]
    QDRANT_API_KEY      = var.secret_ids["qdrant-api-key"]
  }

  # The scheduled jobs only run if the instance has CPU outside of requests. Deriving this rather
  # than exposing it as a knob means the two settings cannot drift into a silently broken pipeline.
  needs_always_on_cpu = var.run_in_process_schedulers
  min_instances       = local.needs_always_on_cpu ? max(var.min_instances, 1) : var.min_instances
  cpu_idle            = local.needs_always_on_cpu ? false : true
}

resource "google_cloud_run_v2_service" "this" {
  project  = var.project_id
  name     = var.service_name
  location = var.region

  ingress = var.ingress

  # Cloud Run itself does not need a deletion guard in dev, but in prod an accidental destroy takes
  # the product offline; the environment sets this.
  deletion_protection = var.deletion_protection

  template {
    service_account = var.service_account_email

    # Requests queue behind a full instance rather than cold-starting a new one for every burst.
    max_instance_request_concurrency = var.concurrency
    timeout                          = var.request_timeout

    scaling {
      min_instance_count = local.min_instances
      max_instance_count = var.max_instances
    }

    dynamic "vpc_access" {
      for_each = var.vpc_connector_id == null ? [] : [1]
      content {
        connector = var.vpc_connector_id
        # Only private/allowlisted traffic needs to leave via the connector. Sending ALL_TRAFFIC
        # through it would push GCS and Secret Manager calls through NAT too — slower, and billed.
        egress = var.vpc_egress
      }
    }

    containers {
      image = var.image

      # Cloud Run injects PORT; the container's server.port honours it.
      ports {
        name           = "http1"
        container_port = var.container_port
      }

      resources {
        limits = {
          cpu    = var.cpu
          memory = var.memory
        }
        cpu_idle          = local.cpu_idle
        startup_cpu_boost = true
      }

      # ---- Plain configuration (non-secret) ----
      dynamic "env" {
        for_each = var.env_vars
        content {
          name  = env.key
          value = env.value
        }
      }

      # ---- Secrets injected as env vars, resolved at instance start ----
      # "latest" means a rotated secret is picked up by the next revision without a Terraform run.
      dynamic "env" {
        for_each = local.env_secrets
        content {
          name = env.key
          value_source {
            secret_key_ref {
              secret  = env.value
              version = "latest"
            }
          }
        }
      }

      # ---- JWT keypair, mounted as files (see header) ----
      # Two mounts, not one: a Cloud Run secret volume projects exactly ONE secret, and JwtService
      # loads a private AND a public PEM from separate paths.
      volume_mounts {
        name       = "jwt-private-key"
        mount_path = "${var.jwt_key_mount_path}/private"
      }

      volume_mounts {
        name       = "jwt-public-key"
        mount_path = "${var.jwt_key_mount_path}/public"
      }

      # Give the JVM time to boot before Cloud Run calls the instance unhealthy. A Spring Boot app
      # with JPA + Flyway is not up in a second, and an impatient startup probe produces a crash
      # loop that looks like an application bug.
      startup_probe {
        http_get {
          path = "/actuator/health"
          port = var.container_port
        }
        initial_delay_seconds = var.startup_initial_delay
        period_seconds        = 10
        timeout_seconds       = 5
        failure_threshold     = var.startup_failure_threshold
      }

      liveness_probe {
        http_get {
          path = "/actuator/health"
          port = var.container_port
        }
        initial_delay_seconds = 0
        period_seconds        = 30
        timeout_seconds       = 5
        failure_threshold     = 3
      }
    }

    volumes {
      name = "jwt-private-key"
      secret {
        secret       = var.secret_ids["jwt-private-key"]
        default_mode = 256 # 0400 — read-only, owner only
        items {
          version = "latest"
          path    = "notarist-private.pem"
          mode    = 256
        }
      }
    }

    volumes {
      name = "jwt-public-key"
      secret {
        secret       = var.secret_ids["jwt-public-key"]
        default_mode = 292 # 0444 — the public half is not a secret; it is mounted only because
        items {            # JwtService insists on reading it from a path like the private one.
          version = "latest"
          path    = "notarist-public.pem"
          mode    = 292
        }
      }
    }

    labels = var.labels
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }

  labels = var.labels

  lifecycle {
    ignore_changes = [
      # Cloud Build deploys new images outside Terraform. Without this, the next `terraform apply`
      # would roll the service back to whatever image the tfvars pinned — silently reverting a
      # deploy. Terraform owns the service's SHAPE; the pipeline owns which image is running.
      template[0].containers[0].image,
      client,
      client_version,
    ]
  }
}

# ---------------------------------------------------------------------------
# Who may invoke the service.
#
# allUsers = a public endpoint. That is correct for a customer-facing API whose auth is its own JWT
# layer (this app's), and catastrophic for anything that assumed the network was the boundary. It is
# an explicit, per-environment decision, never a default.
# ---------------------------------------------------------------------------
resource "google_cloud_run_v2_service_iam_member" "invokers" {
  for_each = toset(var.invoker_members)

  project  = var.project_id
  location = google_cloud_run_v2_service.this.location
  name     = google_cloud_run_v2_service.this.name
  role     = "roles/run.invoker"
  member   = each.value
}

resource "google_cloud_run_domain_mapping" "this" {
  count = var.custom_domain == null ? 0 : 1

  project  = var.project_id
  location = var.region
  name     = var.custom_domain

  metadata {
    namespace = var.project_id
  }

  spec {
    route_name = google_cloud_run_v2_service.this.name
  }
}
