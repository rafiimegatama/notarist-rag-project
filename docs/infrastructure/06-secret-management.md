# 06 — Secret Management

## Terraform creates containers. It never sets values.

Terraform state is a plaintext record of everything Terraform manages. Put a password in a
`google_secret_manager_secret_version.secret_data` and you have not protected it — you have copied it
into the state file, into every plan output, and into every CI log that echoes a plan.

`sensitive = true` hides a value from CLI output. **It does not remove it from state.**

So "never hardcode secrets" has to mean something stronger than "don't put them in the repo". It has
to mean *the value never enters the IaC pipeline at all*. Terraform creates the empty secret and
grants the runtime SA permission to read it. A human writes the value once, out of band.

## The secrets

Eight containers, `notarist-<env>-<name>`:

| Secret | Consumed as | Wired? |
|---|---|---|
| `postgres-password` | env var | yes |
| `app-encryption-key` | env var | yes |
| `app-encryption-salt` | env var | yes |
| `qdrant-api-key` | env var | yes |
| `jwt-private-key` | **file** at `/etc/notarist/keys` | yes |
| `jwt-public-key` | **file** | yes |
| `openrouter-api-key` | — | **container only** |
| `gemini-api-key` | — | **container only** |

The JWT keys are mounted as **files** because `JwtService` calls
`Files.readString(Paths.get(...))`. Passing a PEM as an env var fails at startup — and the tempting
"fix" is baking the key into the image, which is how private keys end up in a container registry.

`openrouter-api-key` and `gemini-api-key` were added this sprint as containers only. The `cloudrun`
module selects secrets **by name** (`var.secret_ids["postgres-password"]`) rather than iterating, so
these are created but not mounted. That is why they are safe to add: an unmounted secret with zero
versions cannot break a revision. They are also deliberately **absent from `verify-secrets`** in
both pipelines — requiring values for a feature nothing consumes would block deploys for no reason.

**Wiring them into the service is a backend + Terraform change, and it was out of scope here.** See
[09-next-steps.md](09-next-steps.md).

## What is NOT a secret

`POSTGRES_URL`, `POSTGRES_USER`, `QDRANT_URL`, `GCS_BUCKET`, `OLLAMA_BASE_URL` are plain env vars.

The infrastructure sprint specified all of these as Secret Manager entries. That was pushed back on,
and the reasoning is worth recording: a URL is not a credential. Routing one through Secret Manager
buys no confidentiality, and costs

- ~$0.06/secret/version/month plus access charges,
- an extra IAM dependency on the startup path,
- and a new failure mode — **a secret with zero versions stops the revision from starting**.

That is three costs for zero security benefit. Connection topology belongs in configuration.

GCS credentials are **nothing at all**: authentication is ADC via the runtime service account. There
is no key file anywhere.

## Populating values

`terraform output secret_populate_commands` prints this for the environment you are in.

```bash
P=notarist-prod-000000
E=notarist-prod

printf '%s' "$SUPABASE_PASSWORD"   | gcloud secrets versions add $E-postgres-password   --data-file=- --project=$P
printf '%s' "$APP_ENCRYPTION_KEY"  | gcloud secrets versions add $E-app-encryption-key  --data-file=- --project=$P
printf '%s' "$APP_ENCRYPTION_SALT" | gcloud secrets versions add $E-app-encryption-salt --data-file=- --project=$P
printf '%s' "$QDRANT_API_KEY"      | gcloud secrets versions add $E-qdrant-api-key      --data-file=- --project=$P

# JWT keypair — mounted as FILES.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out notarist-private.pem
openssl rsa -in notarist-private.pem -pubout -out notarist-public.pem
gcloud secrets versions add $E-jwt-private-key --data-file=notarist-private.pem --project=$P
gcloud secrets versions add $E-jwt-public-key  --data-file=notarist-public.pem  --project=$P
shred -u notarist-private.pem   # do not leave it on a laptop
```

`printf '%s'` rather than `echo`: `echo` appends a newline, and a trailing `\n` in a password is a
genuinely miserable bug to find.

## Ordering — the thing that will bite you

**Populate every mounted secret BEFORE the first `terraform apply` of the cloudrun module.**

A Cloud Run revision referencing a secret with zero versions will not start, and the error points at
the revision rather than at the empty secret. `verify-secrets` in both pipelines catches this and
names the empty secret.

## Rotation

Just another `versions add`. The app reads `:latest` and picks it up on the next revision. No
Terraform run, no state churn.

The runtime SA holds `roles/secretmanager.secretAccessor` — it can **read** values, not create,
update or delete them. A compromised container can use the DB password but cannot rotate it out from
under you.

`prevent_destroy = true` on every secret: deletion is unrecoverable and takes the app down.
Removing one requires an explicit removal from `var.secrets` *and* state surgery — a refactor cannot
drop it by accident.
