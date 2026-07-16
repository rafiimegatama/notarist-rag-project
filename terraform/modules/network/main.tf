/**
 * VPC egress with a STATIC outbound IP.
 *
 * Cloud Run's default egress comes from a large, shared, changing Google IP range. That is fine
 * until a downstream wants to allowlist you — and both of ours plausibly will:
 *
 *   - Supabase: "Network Restrictions" allowlists source CIDRs to the Postgres port.
 *   - Qdrant Cloud: IP allowlisting on the cluster endpoint.
 *
 * You cannot allowlist Cloud Run's default egress. So this module builds the standard path to a
 * stable source IP: a Serverless VPC Access connector puts the service's egress inside a subnet,
 * a Cloud Router + Cloud NAT gives that subnet a reserved static external IP, and the downstream
 * allowlists that one address.
 *
 * This module is OPTIONAL (enabled = false skips everything). Without it Cloud Run still reaches
 * Supabase and Qdrant fine over the public internet — you simply cannot pin the source IP. Turn it
 * on when a downstream demands an allowlist, or when policy requires egress to be attributable.
 *
 * Cost note: a connector runs min_instances VMs 24/7. It is not free, and on a dev environment that
 * nothing allowlists it is pure burn — hence enabled = false by default outside prod.
 */

resource "google_compute_network" "this" {
  count = var.enabled ? 1 : 0

  project                 = var.project_id
  name                    = "${var.name_prefix}-vpc"
  auto_create_subnetworks = false
  description             = "Egress VPC for Notarist Cloud Run (${var.environment})"
}

resource "google_compute_subnetwork" "connector" {
  count = var.enabled ? 1 : 0

  project       = var.project_id
  name          = "${var.name_prefix}-connector-subnet"
  region        = var.region
  network       = google_compute_network.this[0].id
  ip_cidr_range = var.connector_cidr

  # See every flow leaving the service. For a system handling confidential legal documents, being
  # able to answer "what did it talk to, and when?" is worth the log volume.
  log_config {
    aggregation_interval = "INTERVAL_5_SEC"
    flow_sampling        = var.flow_log_sampling
    metadata             = "INCLUDE_ALL_METADATA"
  }
}

resource "google_vpc_access_connector" "this" {
  count = var.enabled ? 1 : 0

  project = var.project_id
  name    = "${var.name_prefix}-conn"
  region  = var.region

  subnet {
    name = google_compute_subnetwork.connector[0].name
  }

  min_instances = var.connector_min_instances
  max_instances = var.connector_max_instances
  machine_type  = var.connector_machine_type
}

# ---------------------------------------------------------------------------
# Static egress IP: reserve it, then make Cloud NAT use ONLY it.
# Letting NAT auto-allocate would hand out addresses that change, which defeats the entire purpose.
# ---------------------------------------------------------------------------
resource "google_compute_address" "nat" {
  count = var.enabled ? var.nat_ip_count : 0

  project      = var.project_id
  name         = "${var.name_prefix}-nat-ip-${count.index}"
  region       = var.region
  address_type = "EXTERNAL"
  description  = "Static egress IP for Notarist Cloud Run — allowlist this in Supabase and Qdrant."
}

resource "google_compute_router" "this" {
  count = var.enabled ? 1 : 0

  project = var.project_id
  name    = "${var.name_prefix}-router"
  region  = var.region
  network = google_compute_network.this[0].id
}

resource "google_compute_router_nat" "this" {
  count = var.enabled ? 1 : 0

  project = var.project_id
  name    = "${var.name_prefix}-nat"
  region  = var.region
  router  = google_compute_router.this[0].name

  nat_ip_allocate_option = "MANUAL_ONLY"
  nat_ips                = google_compute_address.nat[*].self_link

  source_subnetwork_ip_ranges_to_nat = "LIST_OF_SUBNETWORKS"

  subnetwork {
    name                    = google_compute_subnetwork.connector[0].id
    source_ip_ranges_to_nat = ["ALL_IP_RANGES"]
  }

  log_config {
    enable = true
    filter = "ERRORS_ONLY"
  }
}
