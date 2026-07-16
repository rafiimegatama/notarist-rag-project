# 02 — Terraform Architecture

## Layout

```
terraform/
  bootstrap/              Run ONCE per project. Creates the state bucket. Local state.
  modules/                Nine reusable modules. No provider block, no backend.
  environments/
    dev/ staging/ prod/   Thin roots. main.tf is byte-identical across all three.
```

## The three roots are byte-identical

`dev/main.tf`, `staging/main.tf` and `prod/main.tf` have the same checksum. So do `variables.tf` and
`outputs.tf`. Only `backend.tf` and `terraform.tfvars` differ.

This is the most important structural decision in the tree, and it predates this sprint. The *shape*
of the infrastructure must not drift between environments, so "what is different about prod?" is
answered by diffing two small tfvars files rather than two large HCL trees. It rules out the classic
failure where staging passes and prod breaks because prod's root quietly grew an extra argument.

**If you change one root, copy it to the other two.** There is no automation enforcing this; it is
a convention held up by a checksum you can check yourself:

```bash
cd terraform/environments
md5sum dev/main.tf staging/main.tf prod/main.tf | awk '{print $1}' | sort -u | wc -l   # must print 1
```

The obvious alternative — one root plus workspaces — was not taken, and shouldn't be. Workspaces
share a single backend and a single set of credentials, so a `terraform destroy` in the wrong
workspace reaches prod. Separate roots with separate state buckets in separate projects make that
mistake structurally impossible rather than merely discouraged.

## State

Each environment has its own GCS bucket, configured in `backend.tf`:

- **Versioned**, so a corrupted or truncated state can be rolled back.
- **Locked** — GCS backend takes a lock automatically; two concurrent applies cannot interleave.
- **Not** shared between environments.

`bootstrap/` is the exception: it creates the state bucket, so it cannot store its state in it. It
uses local state. That local state file matters little — bootstrap creates one bucket and enables
APIs, and re-running it against existing resources is a no-op — but see
[07-deployment-guide.md](07-deployment-guide.md).

**State is not a secret store, and it is not encrypted at rest by Terraform.** Everything Terraform
manages is recorded in it in plaintext. That single fact drives the entire secret design in
[06-secret-management.md](06-secret-management.md).

## Module conventions

Every module:

- Declares its own `versions.tf` (`required_version >= 1.5`, `google ~> 6.0`). Added this sprint —
  a module that does not pin its provider inherits whatever the calling root has, so the same module
  can behave differently depending on who calls it.
- Declares **no** `provider` block and **no** `backend`. Providers are configured once, in the root,
  and inherited. A module that configures its own provider cannot be used twice in one root — which
  is exactly what `storage` now does, four times over.
- Takes `project_id` explicitly rather than relying on provider default.
- Opens with a comment explaining *why*, not *what*. `resource "google_storage_bucket"` already says
  what it does; the comment explains why `public_access_prevention` is enforced.

## Dependency graph

Extracted from `terraform graph` against the prod root, not from memory:

```
project_services ──► service_account ──► artifact_registry ──┐
        │                    │                                │
        │                    ├──► storage ────────────────────┤
        │                    ├──► storage_ocr_output          │
        │                    ├──► storage_generated_documents │
        │                    ├──► storage_application_assets  │
        │                    └──► secret_manager ─────────────┤
        │                                                     │
        └──► network ────────────────────────────────────────►├──► cloudrun ──┬──► monitoring
                                                                              └──► scheduler
```

Read it as: **APIs before anything** (a resource cannot be created against a disabled API);
**identities before bindings** (a binding needs its member to exist); **secrets before the service**
(a revision referencing an unresolvable secret will not start); **the service before the things that
target it** (scheduler and monitoring need its URL).

Terraform infers almost all of this from references. `depends_on = [module.project_services]` is
stated explicitly because API enablement is not referenced by anything and would otherwise race.

Note what the graph does *not* show: `cloudrun` depends on `storage` but **not** on
`storage_ocr_output`, `storage_generated_documents` or `storage_application_assets`. Only the
`documents` bucket is passed to the service (as `GCS_BUCKET`). The other three exist but nothing
consumes them yet — see [09-next-steps.md](09-next-steps.md).

## What is not here

- **Supabase and Qdrant.** Not GCP resources. Their URLs and credentials are *inputs*, not outputs.
- **Cloud SQL.** Deliberately not provisioned — Supabase is the production PostgreSQL.
- **GKE, Compute Engine, Memorystore.** Everything runs on Cloud Run.
- **The RAG sidecars** (OCR, NER, reranker, embedding, Ollama). Not deployed by this Terraform.
