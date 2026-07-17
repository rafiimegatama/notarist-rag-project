# 08 — Cost Estimation

**Estimates, not quotes.** Derived from the sizing committed in `terraform.tfvars.example` and
published list prices. Read the method and check it rather than trusting the totals.

## Headline

**Roughly $390–$480/month across all three environments, at zero traffic.**

About **95% of that is Cloud Run instances that are idle but billed**, because the platform cannot
scale to zero. That is a design consequence, not waste — and it is fixable. See below.

## The reason: the platform cannot scale to zero

`run_in_process_schedulers = true` in **all three** environments. The `cloudrun` module derives
`min_instances >= 1` and `cpu_idle = false` from it, because the app drives ingestion from in-process
`@Scheduled` jobs and Cloud Run gives an idle instance no CPU.

So every environment — including dev — runs at least one instance 24/7, whether or not anyone uses
it. Dev is not cheap.

## Method

Cloud Run **CPU-always-allocated** rates (which is what `cpu_idle = false` selects):

- vCPU: **$0.000018** / vCPU-second
- Memory: **$0.0000020** / GiB-second
- Month: 730 h × 3600 = **2,628,000** seconds

> Counter-intuitive but correct: always-allocated is *cheaper per second* than
> allocated-during-requests ($0.000024 / $0.0000025) — you are billed for every second either way.
> The cost is the always-on requirement, not the rate.

## Per environment

| | dev | staging | prod |
|---|---|---|---|
| min instances | 1 (derived) | 1 (derived) | 2 (explicit) |
| vCPU × memory | 1 × 1 GiB | 1 × 2 GiB | 2 × 4 GiB |
| CPU / month | $47.30 | $47.30 | $189.22 |
| Memory / month | $5.26 | $10.51 | $42.05 |
| **Cloud Run** | **$52.56** | **$57.81** | **$231.26** |
| VPC egress | — (off) | ~$21 | ~$21 |
| **Subtotal** | **~$53** | **~$79** | **~$252** |

**Cloud Run + network ≈ $384/month.**

Prod's `min_instances = 2` is deliberate — a single instance restarting would pause ingestion
entirely — and it is the single most expensive line here, at ~$231/month before a request is served.

### VPC egress (staging + prod)

Enabled so Supabase and Qdrant can allowlist a static IP.

| | |
|---|---|
| Connector: 2 × e2-micro, 24/7 | ~$15.50 |
| Cloud NAT gateway (~$0.0014/VM-hour × 2) | ~$2.00 |
| Reserved static IP, in use | ~$3.20 |
| NAT data processing | $0.045/GB |

The connector's floor is 2 instances — that is a platform minimum, not a tuning choice.

## Everything else — under $15/month combined

| Service | Estimate | Notes |
|---|---|---|
| Cloud Storage | $2–10 | $0.023/GB/mo Standard. Tiering drops cold objects to NEARLINE (90d) / COLDLINE (365d) |
| Artifact Registry | ~$0.50 | $0.10/GB/mo over 0.5 GB free |
| Secret Manager | ~$1.44 | 8 secrets × 3 envs × $0.06/version/mo |
| Cloud Logging | $0 → | First 50 GB/mo free, then $0.50/GB |
| Cloud Monitoring | $0 | GCP metrics free |
| Cloud Scheduler | $0 | First 3 jobs free |
| Cloud Build | $0 → | 120 free build-minutes/day; `E2_HIGHCPU_8` is $0.016/min beyond |
| GitHub Actions | $0 | Free for public repos; private repos consume plan minutes |

Versioning on `documents` and `generated_documents` means storage grows with overwrites — the
lifecycle rule keeps 3 noncurrent versions and deletes the rest, so it is bounded, not unbounded.

## Caveats — read these before quoting the number

1. **Region premium.** Everything is `asia-southeast2` (Jakarta). Rates above are the standard
   published tier; Jakarta typically carries **roughly +10–20%**. Applied to the Cloud Run subtotal
   that is **+$38 to +$77/month**, which is where the $390–$480 range comes from.
2. **Zero traffic.** No request CPU beyond the warm instances, no egress, no signed-URL bandwidth,
   no logging past the free tier.
3. **Sustained-use / committed-use discounts are not modelled.** Cloud Run committed use could cut
   the always-on cost meaningfully for prod, since the baseline is guaranteed.
4. **Free tier is not modelled** (2M requests/month etc.).
5. **List prices change.** Verify with the pricing calculator before committing a budget.
6. **Supabase, Qdrant, OpenRouter and Gemini are not here at all** — they are not GCP resources and
   may well exceed the GCP bill.

## Where the money actually is

| Lever | Saving | Cost of pulling it |
|---|---|---|
| **Move schedulers to Cloud Scheduler HTTP triggers** | **~$110/mo** (dev+staging → zero) and lets prod drop toward 1 | A Java change. `modules/scheduler` (`var.jobs`) is already wired to drive it |
| Prod `min_instances` 2 → 1 | ~$116/mo | A restart pauses ingestion until the instance is back |
| Turn dev off outside work hours | ~$35/mo | Scheduling machinery, and dev stops being always-available |
| Disable staging VPC egress | ~$21/mo | Staging loses its static IP; only viable if nothing allowlists it |

The first row is the real one. It is [09-next-steps.md](09-next-steps.md) item 2, it is also the
thing blocking scale-to-zero, and it is a backend change rather than an infrastructure one — which
is why this sprint could not do it.
