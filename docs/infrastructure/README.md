# Notarist — Infrastructure

Google Cloud platform documentation. Written 2026-07-15, verified against the live project.

```
GitHub ──► GitHub Actions ─┐
                           ├──► Artifact Registry ──► Cloud Run ──┬──► Supabase (PostgreSQL)
Cloud Build ───────────────┘                              │       ├──► Qdrant (vectors)
                                                          │       └──► Cloud Storage (4 buckets)
                                   Cloud Scheduler ───────┤
                                   Secret Manager ────────┤
                                   Logging + Monitoring ──┘
```

| Doc | |
|---|---|
| [01-current-state.md](01-current-state.md) | What exists today, what was verified, what wasn't |
| [02-terraform-architecture.md](02-terraform-architecture.md) | Layout, state, the byte-identical roots, dependency graph |
| [03-module-overview.md](03-module-overview.md) | All nine modules, and why two specified ones don't exist |
| [04-gcp-resources.md](04-gcp-resources.md) | What an apply creates; what is deliberately absent |
| [05-ci-cd.md](05-ci-cd.md) | Both pipelines, repo variables, the two deploy switches |
| [06-secret-management.md](06-secret-management.md) | Why Terraform never sets a value |
| [07-deployment-guide.md](07-deployment-guide.md) | Empty project → running service |
| [08-cost-estimation.md](08-cost-estimation.md) | ~$390–480/mo, and why ~95% is idle instances |
| [09-next-steps.md](09-next-steps.md) | What blocks what |

## Start here

**Nothing has been applied.** No buckets, no Cloud Run service, no service accounts. The Terraform
`validate`s cleanly on all four roots and `tflint` is clean, but **no `terraform plan` has ever
run** — the environment had no Application Default Credentials. `validate` catches schema and type
errors; it does not catch quota, org policy, IAM propagation, or a bucket name someone else owns.
The first apply is still a first apply. [01-current-state.md](01-current-state.md).

## The three things most likely to hurt

1. **The Supabase app role must not be superuser and must not hold `BYPASSRLS`.** Either one
   silently voids every cross-tenant isolation policy. Everything keeps working; every tenant just
   sees every other tenant. Nothing in GCP can detect it. ([07](07-deployment-guide.md))
2. **A secret with zero versions stops a revision from starting**, with an error that names the
   revision rather than the secret. Populate before the first apply. ([06](06-secret-management.md))
3. **The platform cannot scale to zero**, which is ~95% of the bill. It's a Java change, and the
   infrastructure side is already wired for it. ([08](08-cost-estimation.md), [09](09-next-steps.md))
