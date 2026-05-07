# Rota Portal Phone

Internal Kotlin Multiplatform phone client scaffold for Opsgenie-backed rota tooling.

Current modules:

- `shared`: KMP shared code for Opsgenie token validation, API calls, and token-store contracts.
- `rota_portal_android:app`: Android app, Compose UI, and Android secure token storage.

No dedicated iPhone app files are included yet. The shared module declares iOS device and simulator targets so an iOS app can consume the same shared Kotlin code later.

Security rules:

- No API keys, tokens, or credentials are stored in source files.
- Android user tokens are entered at runtime and stored only through `EncryptedSharedPreferences`.
- `gradle.properties` is ignored by Git. Use `gradle.properties.example` as the local template.

Local setup:

```bash
cp gradle.properties.example gradle.properties
```

Then edit `gradle.properties` locally:

```properties
OPS_API_BASE=https://api.opsgenie.com
TOKEN_DOCS_URL=https://internal.example.com/docs/opsgenie-api-token
OPSGENIE_SCHEDULE_NAME=GB-INFRA-Schedule
OPSGENIE_ROTATION_NAME=normal
```

If there is no internal token document yet, leave it empty:

```properties
TOKEN_DOCS_URL=
```

For the EU Opsgenie tenant, use:

```properties
OPS_API_BASE=https://api.eu.opsgenie.com
```

Android Studio:

- Open `/Users/yossi/opsgenie/rota_portal_phone`.
- Build the `rota_portal_android:app` module.
- The debug APK output is `rota_portal_android/app/build/outputs/apk/debug/app-debug.apk`.
