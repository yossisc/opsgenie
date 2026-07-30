function currentYearMonth() {
  const now = new Date();
  return { year: now.getFullYear(), month: now.getMonth() + 1 };
}

function syncToCurrentMonth() {
  const current = currentYearMonth();
  state.year = current.year;
  state.month = current.month;
}

const state = {
  year: 0,
  month: 0,
  periods: [],
  members: [],
  selectedPeriod: null,
  bulkMode: false,
  selectedPeriodIds: new Set(),
};

syncToCurrentMonth();

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

const MEMBER_COLORS = {
  Yossi: "member-yossi",
  Gabi: "member-gabi",
  Nadav: "member-nadav",
  Dovid: "member-dovid",
  Unassigned: "member-unassigned",
};

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

function formatLocalTime(ms) {
  const date = new Date(ms);
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
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
  state.selectedPeriod = null;
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
  setStatus("Loading on-call timeline...");
  const payload = await fetchJson(`/api/month?year=${state.year}&month=${state.month}`);
  state.periods = payload.periods || [];
  state.members = payload.teamMembers;
  state.selectedPeriodIds.clear();
  subtitle.textContent = `${payload.scheduleName} / ${payload.rotationName} (Final)`;
  monthPicker.value = `${state.year}-${pad(state.month)}`;
  renderMemberOptions(payload.defaultAssignableMembers);
  renderCalendar();
  updateBulkControls();

  if (payload.timelineError) {
    setStatus(`Failed to load Opsgenie Final timeline: ${payload.timelineError}`, true);
    return;
  }

  const assigned = state.periods.filter((period) => period.member !== "Unassigned").length;
  const unassigned = state.periods.length - assigned;
  const overrideCount = state.periods.filter((period) => period.type === "override").length;
  let note = `${monthLabel(state.year, state.month)}: ${state.periods.length} on-call periods from Opsgenie Final`;
  if (overrideCount) note += `, ${overrideCount} overrides`;
  if (unassigned) note += `, ${unassigned} unassigned`;
  if (payload.overridesError) note += `. Override list sync failed: ${payload.overridesError}`;
  setStatus(note, Boolean(payload.overridesError));
}

function updateBulkControls() {
  const count = state.selectedPeriodIds.size;
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

function dayBounds(dateKey) {
  const start = new Date(`${dateKey}T00:00:00`);
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  return { start: start.getTime(), end: end.getTime() };
}

function overlapsDay(item, dateKey) {
  const { start, end } = dayBounds(dateKey);
  return Date.parse(item.end) > start && Date.parse(item.start) < end;
}

function periodsForDate(dateKey) {
  return state.periods
    .filter((period) => overlapsDay(period, dateKey))
    .sort((a, b) => Date.parse(a.start) - Date.parse(b.start));
}

function clippedTimeLabel(item, dateKey) {
  const { start: dayStart, end: dayEnd } = dayBounds(dateKey);
  const start = Math.max(Date.parse(item.start), dayStart);
  const end = Math.min(Date.parse(item.end), dayEnd);
  const endLabel = end === dayEnd ? "24:00" : formatLocalTime(end);
  return `${formatLocalTime(start)}-${endLabel}`;
}

function typeLabel(type) {
  if (type === "override") return "Override";
  if (type === "historical") return "Historical";
  if (type === "default") return "Base rotation";
  return type || "On-call";
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

    for (const period of periodsForDate(dateKey)) {
      const selected = state.selectedPeriodIds.has(period.id);
      const colorClass = MEMBER_COLORS[period.member] || "member-other";
      const button = document.createElement("button");
      button.className = `shift oncall ${colorClass} type-${period.type}${selected ? " selected" : ""}`;
      const checkbox = state.bulkMode ? `<span class="select-box">${selected ? "x" : ""}</span>` : "";
      button.innerHTML = `${checkbox}<strong>${period.member}</strong><span>${clippedTimeLabel(period, dateKey)}</span>`;
      button.dataset.tip = [
        `${period.member} on-call`,
        `${formatTime(period.start)} to ${formatTime(period.end)}`,
        typeLabel(period.type),
        state.bulkMode ? "Click to select" : "Click to override",
      ].join("\n");
      button.addEventListener("click", () => {
        if (state.bulkMode) {
          togglePeriodSelection(period.id);
        } else {
          openSinglePeriodDialog(period);
        }
      });
      cell.appendChild(button);
    }
    calendar.appendChild(cell);
  }
}

function selectedPeriods() {
  return state.periods.filter((period) => state.selectedPeriodIds.has(period.id));
}

function togglePeriodSelection(periodId) {
  if (state.selectedPeriodIds.has(periodId)) {
    state.selectedPeriodIds.delete(periodId);
  } else {
    state.selectedPeriodIds.add(periodId);
  }
  renderCalendar();
  updateBulkControls();
}

function openSinglePeriodDialog(period) {
  state.selectedPeriod = period;
  shiftDialog.dataset.mode = "single";
  document.querySelector("input[name='mode'][value='full']").checked = true;
  partialFields.classList.remove("active");
  document.querySelectorAll("input[name='mode']").forEach((radio) => {
    radio.disabled = false;
  });
  const defaultMember = period.member !== "Unassigned" ? period.member : (state.members[0]?.name || "Yossi");
  if ([...memberSelect.options].some((option) => option.value === defaultMember)) {
    memberSelect.value = defaultMember;
  }
  startInput.value = isoToLocalInput(period.start);
  endInput.value = isoToLocalInput(period.end);
  shiftSummary.textContent = [
    `${period.member} on-call (${typeLabel(period.type)})`,
    `${formatTime(period.start)} to ${formatTime(period.end)}`,
  ].join("\n");
  shiftDialog.showModal();
}

function openBulkShiftDialog() {
  const periods = selectedPeriods();
  if (!periods.length) return;
  state.selectedPeriod = null;
  shiftDialog.dataset.mode = "bulk";
  document.querySelector("input[name='mode'][value='full']").checked = true;
  partialFields.classList.remove("active");
  document.querySelectorAll("input[name='mode']").forEach((radio) => {
    radio.disabled = radio.value !== "full";
  });
  const firstMember = periods.find((period) => period.member !== "Unassigned")?.member;
  if (firstMember && [...memberSelect.options].some((option) => option.value === firstMember)) {
    memberSelect.value = firstMember;
  }
  startInput.value = "";
  endInput.value = "";
  shiftSummary.textContent = [
    `${periods.length} selected on-call periods`,
    periods.slice(0, 5).map((period) => `${period.member}: ${formatTime(period.start)} to ${formatTime(period.end)}`).join("\n"),
    periods.length > 5 ? `...and ${periods.length - 5} more` : "",
  ].filter(Boolean).join("\n");
  shiftDialog.showModal();
}

async function submitOverride(event) {
  event.preventDefault();
  if (shiftDialog.dataset.mode === "bulk") {
    await submitBulkOverride();
    return;
  }
  const period = state.selectedPeriod;
  if (!period) return;
  const mode = document.querySelector("input[name='mode']:checked").value;
  const member = memberSelect.value;
  const start = mode === "full" ? period.start : localInputToIsoZ(startInput.value);
  const end = mode === "full" ? period.end : localInputToIsoZ(endInput.value);
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
    closeShiftDialog();
    await loadMonth();
    setStatus(`Override ${payload.operation} in Opsgenie: ${payload.alias}`);
  } catch (error) {
    setStatus(error.message, true);
  }
}

async function submitBulkOverride() {
  const periods = selectedPeriods();
  if (!periods.length) return;
  const member = memberSelect.value;
  setStatus(`Overriding ${periods.length} selected periods for ${member}...`);
  try {
    const payload = await fetchJson("/api/override-bulk", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        member,
        shifts: periods.map((period) => ({
          id: period.id,
          start: period.start,
          end: period.end,
        })),
      }),
    });
    const okResults = payload.results.filter((item) => item.ok);
    const failed = payload.results.length - okResults.length;
    closeShiftDialog();
    await loadMonth();
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
    state.selectedPeriodIds.clear();
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

window.addEventListener("pageshow", (event) => {
  if (!event.persisted) {
    return;
  }
  syncToCurrentMonth();
  loadMonth().catch((error) => setStatus(error.message, true));
});
