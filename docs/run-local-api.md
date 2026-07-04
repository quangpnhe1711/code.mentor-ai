# Run Local API

This guide starts PostgreSQL, runs the Spring Boot API, opens Swagger UI, and gets a local dev JWT for trying protected endpoints.

## Requirements

- Java 21
- Docker Desktop
- PowerShell

## 1. Start PostgreSQL

From `be/codementor.ai`:

```powershell
docker compose up -d postgres
```

The local database is:

```text
host: 127.0.0.1
port: 55432
database: codementor_ai
username: codementor
password: codementor
```

Flyway runs automatically when the API starts.

## 2. Run The API

From `be/codementor.ai`:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The API starts at:

```text
http://localhost:8080
```

## 3. Open Swagger UI

Open:

```text
http://localhost:8080/swagger-ui.html
```

Raw OpenAPI JSON is available at:

```text
http://localhost:8080/v3/api-docs
```

## 4. Get A Dev Token

Most APIs are protected by JWT, so you need an access token before calling them from Swagger or PowerShell.

In production, the token comes from the real GitHub OAuth flow. In local development, the `local` profile enables a helper endpoint:

```text
POST /api/auth/github/provision/dev
```

That endpoint creates or reuses a fake local GitHub user, creates that user's personal organization, stores a fake encrypted GitHub token, and returns a platform JWT.

Run this in a new PowerShell window:

```powershell
$body = @{
  githubUserId = "local-user-1"
  githubLogin = "localdev"
  displayName = "Local Developer"
  email = "localdev@example.test"
  accessToken = "local-gh-token"
} | ConvertTo-Json

$response = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/auth/github/provision/dev" `
  -ContentType "application/json" `
  -Body $body

$accessToken = $response.data.accessToken
$accessToken
```

What each field means:

- `githubUserId`: a stable fake GitHub user id. Reuse the same value to get the same local user again.
- `githubLogin`: fake GitHub username shown in local user/member responses.
- `displayName`: fake display name.
- `email`: fake email.
- `accessToken`: fake GitHub provider token. With the `local` profile this is enough for testing because repository access verification is permissive.

The last line prints a long JWT string. Keep that PowerShell window open because later examples use `$accessToken`.

To use the token in Swagger UI:

1. Open `http://localhost:8080/swagger-ui.html`.
2. Click `Authorize`.
3. Paste this format:

```text
Bearer <access token>
```

Replace `<access token>` with the token printed by PowerShell. Keep the word `Bearer` and one space before the token.

Example:

```text
Bearer eyJhbGciOiJIUzI1NiJ9...
```

Then click `Authorize`, close the modal, and try any protected endpoint such as:

```text
GET /api/organizations
```

You can also verify the token from PowerShell:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/organizations" `
  -Headers @{ Authorization = "Bearer $accessToken" }
```

If it works, you should see at least one organization: the personal organization created for `localdev`.

## 5. Useful First Calls

List organizations for the current user:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/organizations" `
  -Headers @{ Authorization = "Bearer $accessToken" }
```

Create a team organization:

```powershell
$orgBody = @{
  name = "Local Team"
  slug = "local-team"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/organizations" `
  -ContentType "application/json" `
  -Headers @{ Authorization = "Bearer $accessToken" } `
  -Body $orgBody
```

## Local Profile Notes

The `local` profile intentionally changes these settings for easier manual testing:

- `codementor.auth.dev-provisioning-enabled=true`
- `codementor.github.access-verifier=permissive`
- PostgreSQL points to `127.0.0.1:55432/codementor_ai`

Do not use the `local` profile in production.

## Stop PostgreSQL

```powershell
docker compose down
```

To remove the local database volume too:

```powershell
docker compose down -v
```
