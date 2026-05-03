from __future__ import annotations

import os
from pathlib import Path


PORT = int(os.environ.get("ROTA_PORTAL_PORT", "17001"))
OPS_API_BASE = os.environ.get("OPSGENIE_API_BASE", "https://api.opsgenie.com").rstrip("/")
OPS_API_KEY_FILE = Path(os.environ.get("OPSGENIE_API_KEY_FILE", "~/.config/ops_genie_api_key")).expanduser()
SCHEDULE_NAME = os.environ.get("OPSGENIE_SCHEDULE_NAME", "GB-INFRA-Schedule")
ROTATION_NAME = os.environ.get("OPSGENIE_ROTATION_NAME", "normal")

TEAM_MEMBERS = {
    "Yossi": "yossi.schwartz@glassboxdigital.com",
    "Dovid": "dovid.friedman@glassboxdigital.com",
    "Yaron": "yaron@glassboxdigital.com",
    "Gour": "gour.hadad@glassboxdigital.com",
    "Nadav": "nadav.kosovsky@glassboxdigital.com",
    "Gabi": "gavriel.matatov@glassboxdigital.com",
}

EXTRA_MEMBER_SUBSTITUTIONS = {
    "Extra1": "Gabi",
    "Extra2": "Gour",
}

DEFAULT_ASSIGNABLE_MEMBERS = ["Yossi", "Gabi", "Gour", "Nadav", "Dovid"]
