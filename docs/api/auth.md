# Auth API

Base path: `/api/v1`. All bodies are JSON.

Authentication is a signed JWT (HS256). The login response returns it in two ways:

- `accessToken` in the body. The client may keep it in memory and send `Authorization: Bearer <token>`.
- An `httpOnly`, `Secure`, `SameSite=Strict` cookie named `cr_access_token` (path `/api`). It keeps the session across page reloads.

The backend accepts either. The `Authorization` header wins when both are present.

Tokens are valid for 8 hours (`JWT_TTL`). There is no refresh token in the MVP: after expiry the user logs in again.

## Roles

`RECEPTIONIST`, `PRACTITIONER`, `CLINIC_MANAGER`. A user has exactly one role, returned as a one-element `roles` array so the client can support several roles later without a contract change.

## `POST /auth/login`

Public.

Request:

```json
{ "email": "reception@clinic.test", "password": "password" }
```

`200 OK` (also sets the `cr_access_token` cookie):

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 28800,
  "user": {
    "id": "6f1c1c4e-3b0a-4a49-9a51-1f9f6a1d2b10",
    "email": "reception@clinic.test",
    "fullName": "Sarra Test",
    "clinicId": "0b6a4c1e-8d7f-4c2a-9e55-3a1d2f4b5c6d",
    "roles": ["RECEPTIONIST"]
  }
}
```

Errors:

| Status | `code` | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Missing or malformed email or password. `details` lists the fields. |
| 401 | `INVALID_CREDENTIALS` | Unknown email, wrong password or disabled account. The response does not say which. |

## `GET /auth/me`

Authenticated. Returns the current user, same shape as `user` above.

| Status | `code` | When |
| --- | --- | --- |
| 401 | `UNAUTHENTICATED` | No token, or the token is invalid or expired. |

## `POST /auth/logout`

Public. Clears the `cr_access_token` cookie and returns `204 No Content`. JWTs are stateless, so a token the client still holds stays valid until it expires.

## Error body

Every error from the API uses this shape:

```json
{ "code": "UNAUTHENTICATED", "message": "Authentication required", "details": null }
```

Every endpoint other than `POST /auth/login`, `POST /auth/logout` and `GET /actuator/health` returns `401 UNAUTHENTICATED` without a valid token.
