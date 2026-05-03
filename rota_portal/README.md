# Rota Portal

Local Opsgenie rotation portal for `GB-INFRA-Schedule`.

Run:

```bash
python3 server.py
```

Open:

```bash
http://127.0.0.1:17001
```

Defaults:

- Port: `17001`
- Opsgenie API key file: `~/.config/ops_genie_api_key`
- Opsgenie API base: `https://api.opsgenie.com`
- Schedule: `GB-INFRA-Schedule`
- Rotation: `normal`

The UI supports individual full-shift and partial-shift overrides. It does not include a monthly bulk apply action.

Environment overrides:

```bash
ROTA_PORTAL_PORT=17001 python3 server.py
OPSGENIE_API_BASE=https://api.eu.opsgenie.com python3 server.py
OPSGENIE_API_KEY_FILE=/path/to/token python3 server.py
OPSGENIE_SCHEDULE_NAME=GB-INFRA-Schedule python3 server.py
OPSGENIE_ROTATION_NAME=normal python3 server.py
```
