# API key permissions

The app only performs a read-only GET, but the console's current permission check is stricter than the app's actual needs.

## Current console behavior

- Personal keys with only **`usage:read`** are rejected by the console today.
- Personal keys with **read-only** permissions are also rejected.
- Use the **Go API key** from the Go page, or a personal key with **`all`**.

## Endpoint

```
GET https://opencode.ai/console/api/go/status
Authorization: Bearer oc_sk_...
```

## What the app does with the key

- Stores it encrypted with the Android Keystore.
- Sends it only to `opencode.ai`.
- Performs a read-only GET.
- Never writes to your account.
- Never logs or displays the key.

## Notes

The console endpoint is internal and unofficial. Its permission model may change without notice. If you want the narrowest key that works today, the Go API key on the Go page is the intended option.
