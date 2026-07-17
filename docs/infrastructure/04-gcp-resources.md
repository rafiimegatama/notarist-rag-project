# 04 — GCP Resources

What a full `terraform apply` would create, per environment. **Nothing here exists yet** — see
[01-current-state.md](01-current-state.md).

## Created

| Resource | Type | Notes |
|---|---|---|
| `notarist-<env>-api` | `google_cloud_run_v2_service` | The Spring Boot container |
| `notarist` | `google_artifact_registry_repository` | Docker, regional |
| `<env>-documents` | `google_storage_bucket` | Legal originals. WORM in prod |
| `<env>-ocr-output` | `google_storage_bucket` | Derived artefacts |
| `<env>-generated-documents` | `google_storage_bucket` | Template output |
| `<env>-application-assets` | `google_storage_bucket` | Templates. Runtime read-only |
| `notarist-<env>-run` | `google_service_account` | Runtime identity |
| `notarist-<env>-deploy` | `google_service_account` | Cloud Build |
| `notarist-<env>-invoker` | `google_service_account` | Cloud Scheduler |
| `notarist-<env>-gha` | `google_service_account` | GitHub Actions (opt-in) |
| `notarist-<env>-gh-pool` / `-gh-provider` | Workload Identity pool + provider | Opt-in |
| 8 × secret | `google_secret_manager_secret` | Containers only, no values |
| Uptime check, alert policies, log sink, dashboard | monitoring | Only when alert emails set |
| Heartbeat job | `google_cloud_scheduler_job` | |
| VPC, subnet, connector, router, NAT, static IP | network | **Only** when `enable_vpc_egress` |

Plus the state bucket from `bootstrap/`, which is per-project and created once.

## Explicitly NOT created

| | Why |
|---|---|
| **Cloud SQL** | Supabase is the production PostgreSQL. No Cloud SQL anywhere in the tree. |
| **GKE / Compute Engine / Memorystore** | Everything runs on Cloud Run. The only VMs are the VPC connector's, which are managed by the connector, not by us. |
| **Supabase, Qdrant** | Not GCP resources. Inputs, not outputs. |
| **RAG sidecars** (OCR, NER, reranker, embedding, Ollama) | Not in the target architecture and not deployed here. Ingestion stalls at OCR until they exist and `sidecar_urls` points at them. |
| **Secret values** | See [06-secret-management.md](06-secret-management.md). |
| **DNS zones** | `custom_domain` creates a domain mapping; the zone itself is external. |

## Regions

Everything is `asia-southeast2` (Jakarta) by default, and buckets default to `ASIA-SOUTHEAST2`.
Deliberate: notarial data residency is plausibly a legal constraint for an Indonesian notary
platform. Jakarta also carries a price premium over `us-*` — see
[08-cost-estimation.md](08-cost-estimation.md).

`secret_replica_locations` allows pinning Secret Manager replication to named regions when residency
is a hard requirement, instead of the default automatic (global) replication.

## Network posture

`enable_vpc_egress = false` in dev, `true` in staging and prod.

Off, Cloud Run egresses from a shared Google pool with **no stable source IP**. On, egress is routed
through a VPC connector and Cloud NAT pinned to a **reserved static IP** — which is what you give
Supabase or Qdrant to allowlist (`terraform output egress_ips`).

Letting NAT auto-allocate would hand out addresses that change, defeating the point. The module
reserves the address and forces NAT to use only it.

Dev has it off because nothing allowlists dev and a connector runs VMs 24/7 whether or not traffic
flows.

## Deletion protection

Several independent guards, each against a different accident:

| Guard | Where | Stops |
|---|---|---|
| `prevent_destroy` | every secret | a refactor silently dropping a secret |
| `force_destroy = false` | buckets outside dev | `terraform destroy` taking notarial originals with it |
| `retention_policy` (WORM) | prod documents | deletion *by anyone*, including project owners, once locked |
| `deletion_protection` | Cloud Run | accidental service deletion |
| `disable_on_destroy = false` | every API | one env's destroy breaking every other env |

**`retention_locked` is irreversible.** Once locked, the retention period cannot be shortened or
removed by anyone, ever. Leave it `false` until the duration is definitely right. The prod tfvars
example ships it unlocked for exactly this reason.
