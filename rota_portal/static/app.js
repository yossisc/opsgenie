const state = {
  year: 2026,
  month: 5,
  entries: [],
  members: [],
  overrides: [],
  selectedEntry: null,
  bulkMode: false,
  selectedShiftIds: new Set(),
};

const calendar = document.querySelector("#calendar");
const monthPicker = document.querySelector("#monthPicker");
const statusEl = document.querySelector("#status");
const subtitle = document.querySelector("#subtitle");
const shiftDialog = document.querySelector("#shiftDialog");
const shiftSummary = document.querySelector("#shiftSummary");
const memberSelect = document.querySelector("#memberSelect");
const partialFields = document.querySelector("#partialFields");
const startInput = document.querySelector("#startInput");
const endInput = document.querySelector("#endInput");
const bulkModeInput = document.querySelector("#bulkMode");
const openBulkOverrideButton = document.querySelector("#openBulkOverride");

function pad(value) {
  return String(value).padStart(2, "0");
}

function isoToLocalInput(value) {
  const date = new Date(value);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function dateKeyFromDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function localInputToIsoZ(value) {
  const date = new Date(value);
  return date.toISOString().replace(/\.\d{3}Z$/, "Z");
}

function formatUtcTime(ms) {
  const date = new Date(ms);
  return `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
}

function monthLabel(year, month) {
  return new Intl.DateTimeFormat("en", { month: "long", year: "numeric" }).format(new Date(year, month - 1, 1));
}

function formatTime(value) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

function setStatus(message, error = false) {
  statusEl.textContent = message;
  statusEl.style.color = error ? "#b42318" : "";
}

function closeShiftDialog() {
  state.selectedEntry = null;
  shiftDialog.dataset.mode = "";
  if (shiftDialog.open) {
    shiftDialog.close();
  }
}

async function fetchJson(url, options) {
  const response = await fetch(url, options);
  const payload = await response.json();
  if (!response.ok || payload.ok === false) {
    throw new Error(payload.error || `HTTP ${response.status}`);
  }
  return payload;
}

async function loadMonth() {
  setStatus("Loading schedule...");
  const payload = await fetchJson(`/api/month?year=${state.year}&month=${state.month}`);
  state.entries = payload.entries;
  state.members = payload.teamMembers;
  state.overrides = payload.overrides || [];
  state.selectedShiftIds.clear();
  subtitle.textContent = `${payload.scheduleName} / ${payload.rotationName}`;
  monthPicker.value = `${state.year}-${pad(state.month)}`;
  renderMemberOptions(payload.defaultAssignableMembers);
  renderCalendar();
  const overrideNote = payload.overridesError ? ` Override sync failed: ${payload.overridesError}` : ` ${state.overrides.length} existing overrides loaded.`;
  setStatus(`${state.entries.length} shifts loaded for ${monthLabel(state.year, state.month)}.${overrideNote}`, Boolean(payload.overridesError));
}

function updateBulkControls() {
  const count = state.selectedShiftIds.size;
  openBulkOverrideButton.disabled = !state.bulkMode || count === 0;
  openBulkOverrideButton.textContent = count ? `Override Selected (${count})` : "Override Selected";
}

function renderMemberOptions(defaults) {
  const sorted = state.members
    .map((member) => member.name)
    .sort((a, b) => {
      const ai = defaults.indexOf(a);
      const bi = defaults.indexOf(b);
      if (ai !== -1 || bi !== -1) return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
      return a.localeCompare(b);
    });
  memberSelect.innerHTML = sorted.map((name) => `<option value="${name}">${name}</option>`).join("");
}

function overlapsDay(item, dateKey) {
  const dayStart = Date.parse(`${dateKey}T00:00:00Z`);
  const dayEnd = dayStart + 24 * 60 * 60 * 1000;
  return Date.parse(item.end) > dayStart && Date.parse(item.start) < dayEnd;
}

function coveredItemsForDate(dateKey) {
  const items = [];
  for (const entry of state.entries) {
    if (overlapsDay(entry, dateKey)) {
      items.push({ type: "shift", entry });
    }
  }
  for (const override of state.overrides) {
    const fullShiftOverride = !override.partial && state.entries.some((entry) => {
      return entry.start === override.start && entry.end === override.end;
    });
    if (fullShiftOverride) {
      continue;
    }
    if (overlapsDay(override, dateKey)) {
      items.push({ type: "override", override });
    }
  }
  return items.sort((a, b) => Date.parse((a.entry || a.override).start) - Date.parse((b.entry || b.override).start));
}

function clippedTimeLabel(item, dateKey) {
  const dayStart = Date.parse(`${dateKey}T00:00:00Z`);
  const dayEnd = dayStart + 24 * 60 * 60 * 1000;
  const start = Math.max(Date.parse(item.start), dayStart);
  const end = Math.min(Date.parse(item.end), dayEnd);
  const endLabel = end === dayEnd ? "24:00" : formatUtcTime(end);
  return `${formatUtcTime(start)}-${endLabel} UTC`;
}

function effectiveMember(entry) {
  const fullOverride = state.overrides.find((override) => {
    return !override.partial && override.start === entry.start && override.end === entry.end;
  });
  return fullOverride ? fullOverride.member : entry.apply_member;
}

function renderCalendar() {
  calendar.innerHTML = "";
  const first = new Date(state.year, state.month - 1, 1);
  const start = new Date(first);
  start.setDate(1 - first.getDay());
  const todayKey = dateKeyFromDate(new Date());

  for (let i = 0; i < 42; i += 1) {
    const day = new Date(start);
    day.setDate(start.getDate() + i);
    const dateKey = dateKeyFromDate(day);
    const cell = document.createElement("div");
    cell.className = `day${day.getMonth() === state.month - 1 ? "" : " outside"}${dateKey === todayKey ? " today" : ""}`;
    cell.innerHTML = `<div class="date-num">${day.getDate()}</div>`;

    for (const item of coveredItemsForDate(dateKey)) {
      const button = document.createElement("button");
      if (item.type === "override") {
        const override = item.override;
        button.className = `shift override ${override.partial ? "partial-override" : ""}`;
        button.innerHTML = `<strong>${override.member}</strong><span>${clippedTimeLabel(override, dateKey)}</span>`;
        button.dataset.tip = `Opsgenie override\n${formatTime(override.start)} to ${formatTime(override.end)}\n${override.partial ? "Partial override" : "Full shift override"}`;
      } else {
        const entry = item.entry;
        const selected = state.selectedShiftIds.has(entry.id);
        button.className = `shift ${entry.kind === "WEEKEND" ? "weekend" : "regular"}${selected ? " selected" : ""}`;
        const owner = effectiveMember(entry);
        const memberText = entry.member === owner ? entry.member : `${entry.member} -> ${owner}`;
        const checkbox = state.bulkMode ? `<span class="select-box">${selected ? "x" : ""}</span>` : "";
        button.innerHTML = `${checkbox}<strong>${memberText}</strong><span>${clippedTimeLabel(entry, dateKey)}</span>`;
        button.dataset.tip = state.bulkMode
          ? `${entry.kind}\n${formatTime(entry.start)} to ${formatTime(entry.end)}\nClick to select`
          : `${entry.kind}\n${formatTime(entry.start)} to ${formatTime(entry.end)}\nClick to override`;
        button.addEventListener("click", () => {
          if (state.bulkMode) {
            toggleShiftSelection(entry.id);
          } else {
            openSingleShiftDialog(entry);
          }
        });
      }
      cell.appendChild(button);
    }
    calendar.appendChild(cell);
  }
}

function selectedEntries() {
  return state.entries.filter((entry) => state.selectedShiftIds.has(entry.id));
}

function toggleShiftSelection(entryId) {
  if (state.selectedShiftIds.has(entryId)) {
    state.selectedShiftIds.delete(entryId);
  } else {
    state.selectedShiftIds.add(entryId);
  }
  renderCalendar();
  updateBulkControls();
}

function openSingleShiftDialog(entry) {
  state.selectedEntry = entry;
  shiftDialog.dataset.mode = "single";
  document.querySelector("input[name='mode'][value='full']").checked = true;
  partialFields.classList.remove("active");
  document.querySelectorAll("input[name='mode']").forEach((radio) => {
    radio.disabled = false;
  });
  memberSelect.value = entry.apply_member;
  startInput.value = isoToLocalInput(entry.start);
  endInput.value = isoToLocalInput(entry.end);
  shiftSummary.textContent = [
    `${entry.member} shift (${entry.kind})`,
    `${formatTime(entry.start)} to ${formatTime(entry.end)}`,
    `Current apply target: ${entry.apply_member}`,
  ].join("\n");
  shiftDialog.showModal();
}

function openBulkShiftDialog() {
  const entries = selectedEntries();
  if (!entries.length) return;
  state.selectedEntry = null;
  shiftDialog.dataset.mode = "bulk";
  document.querySelector("input[name='mode'][value='full']").checked = true;
  partialFields.classList.remove("active");
  document.querySelectorAll("input[name='mode']").forEach((radio) => {
    radio.disabled = radio.value !== "full";
  });
  memberSelect.value = entries[0].apply_member;
  startInput.value = "";
  endInput.value = "";
  shiftSummary.textContent = [
    `${entries.length} selected full shifts`,
    entries.slice(0, 5).map((entry) => `${entry.member}: ${formatTime(entry.start)} to ${formatTime(entry.end)}`).join("\n"),
    entries.length > 5 ? `...and ${entries.length - 5} more` : "",
  ].filter(Boolean).join("\n");
  shiftDialog.showModal();
}

function upsertOverride(override) {
  state.overrides = state.overrides.filter((existing) => {
    return existing.start !== override.start || existing.end !== override.end;
  });
  state.overrides.push(override);
}

async function submitOverride(event) {
  event.preventDefault();
  if (shiftDialog.dataset.mode === "bulk") {
    await submitBulkOverride();
    return;
  }
  const entry = state.selectedEntry;
  if (!entry) return;
  const mode = document.querySelector("input[name='mode']:checked").value;
  const member = memberSelect.value;
  const start = mode === "full" ? entry.start : localInputToIsoZ(startInput.value);
  const end = mode === "full" ? entry.end : localInputToIsoZ(endInput.value);
  setStatus(`Creating ${mode} override for ${member}...`);
  try {
    const payload = await fetchJson("/api/override", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        member,
        start,
        end,
        partial: mode === "partial",
      }),
    });
    upsertOverride(payload.override);
    renderCalendar();
    closeShiftDialog();
    setStatus(`Override ${payload.operation} in Opsgenie and refreshed: ${payload.alias}`);
  } catch (error) {
    setStatus(error.message, true);
  }
}

async function submitBulkOverride() {
  const entries = selectedEntries();
  if (!entries.length) return;
  const member = memberSelect.value;
  setStatus(`Overriding ${entries.length} selected shifts for ${member}...`);
  try {
    const payload = await fetchJson("/api/override-bulk", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        member,
        shifts: entries.map((entry) => ({
          id: entry.id,
          start: entry.start,
          end: entry.end,
        })),
      }),
    });
    const okResults = payload.results.filter((item) => item.ok);
    for (const result of okResults) {
      upsertOverride(result.override);
    }
    state.selectedShiftIds.clear();
    renderCalendar();
    updateBulkControls();
    closeShiftDialog();
    const failed = payload.results.length - okResults.length;
    setStatus(`Bulk override finished: ${okResults.length} succeeded, ${failed} failed.`, failed > 0);
  } catch (error) {
    setStatus(error.message, true);
  }
}

function shiftMonth(delta) {
  const date = new Date(state.year, state.month - 1 + delta, 1);
  state.year = date.getFullYear();
  state.month = date.getMonth() + 1;
  loadMonth().catch((error) => setStatus(error.message, true));
}

document.querySelector("#prevMonth").addEventListener("click", () => shiftMonth(-1));
document.querySelector("#nextMonth").addEventListener("click", () => shiftMonth(1));
document.querySelector("#refresh").addEventListener("click", () => loadMonth().catch((error) => setStatus(error.message, true)));
document.querySelector("#overrideForm").addEventListener("submit", submitOverride);
openBulkOverrideButton.addEventListener("click", openBulkShiftDialog);
bulkModeInput.addEventListener("change", () => {
  state.bulkMode = bulkModeInput.checked;
  if (!state.bulkMode) {
    state.selectedShiftIds.clear();
  }
  renderCalendar();
  updateBulkControls();
});
document.querySelectorAll("[data-close-override]").forEach((button) => {
  button.addEventListener("click", closeShiftDialog);
});
monthPicker.addEventListener("change", () => {
  const [year, month] = monthPicker.value.split("-").map(Number);
  state.year = year;
  state.month = month;
  loadMonth().catch((error) => setStatus(error.message, true));
});

document.querySelectorAll("input[name='mode']").forEach((radio) => {
  radio.addEventListener("change", () => {
    partialFields.classList.toggle("active", radio.value === "partial" && radio.checked);
  });
});

loadMonth().catch((error) => setStatus(error.message, true));
