# Jasper Report Templates

This repository contains Jasper Report templates (`.jrxml`) under the `templates/` folder and automatically deploys them to object storage via GitHub Actions.

## CI/CD GitHub Actions

Workflows:
- `.github/workflows/test.yml`
- `.github/workflows/deploy.yml`

Flow:
- `test.yml`: validate + compile test + package artifact
- `deploy.yml`: deploy to selected storage provider (`r2`, `s3`, or `gcs`) after test workflow succeeds
   - `r2` and `s3` use one shared S3-compatible deploy path
   - `gcs` uses a dedicated GCS deploy path

Compile checker tool: `tools/jasper-compile-check` (Maven + JasperReports)

Trigger:
- Test workflow: `push` to `main`/`master` (only when there are changes in `templates/**`) + `workflow_dispatch`
- Deploy workflow: `workflow_run` after successful `Jasper Report Test` on `main`/`master` + `workflow_dispatch`

## Provider Selection

Set repository variable:
- `STORAGE_PROVIDER` = `r2` | `s3` | `gcs` (default: `r2`)
- `STORAGE_PREFIX` = object path prefix (default: `jasper-report-templates`)
- `STORAGE_BUCKET` = target bucket name (generic for all providers)
- `STORAGE_REGION` = region for AWS S3 (default: `us-east-1`)

## GitHub Configuration

**Important**: All secrets and variables must be configured in the **`live` environment** (Settings → Environments → live → Environment secrets/variables), as the deploy workflow uses environment-scoped credentials.

### Cloudflare R2 configuration

Use when `STORAGE_PROVIDER=r2`.

Secrets:
- `ACCESS_KEY_ID`
- `SECRET_ACCESS_KEY`
- `ACCOUNT_ID`

Variables:
- `STORAGE_BUCKET` = target R2 bucket name

### AWS S3 configuration

Use when `STORAGE_PROVIDER=s3`.

Secrets:
- `ACCESS_KEY_ID`
- `SECRET_ACCESS_KEY`

Variables:
- `STORAGE_BUCKET` = target S3 bucket name
- `STORAGE_REGION` = AWS region (default: `us-east-1`)

### Google Cloud Storage configuration

Use when `STORAGE_PROVIDER=gcs`.

Secrets:
- `SERVICE_ACCOUNT_KEY` = service account JSON key

Variables:
- `STORAGE_BUCKET` = target GCS bucket name

## Deployment Output

The workflow will:
1. Validate that `.jrxml` files exist.
2. Compile-test all `.jrxml` files (fails if any template is invalid).
3. Upload workflow artifact.
4. Upload to selected object storage provider:
   - `<STORAGE_PREFIX>/jasper-report-templates.tar.gz`
   - `<STORAGE_PREFIX>/templates/...`

> Note: `*:Zone.Identifier` files are excluded during packaging/upload.
