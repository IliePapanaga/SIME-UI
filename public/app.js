(function () {
  "use strict";

  const STORAGE_CREDS = "agenda_creds_v1";
  const STORAGE_SNAPSHOT = "agenda_snapshot_v1";
  const STORAGE_TOKEN = "agenda_token_v1";
  const STORAGE_THEME = "agenda_theme_v2";
  const STORAGE_HW_DONE = "agenda_hw_done_v1";
  const STORAGE_SCHED_LAYOUT = "agenda_sched_layout_v1";
  const STORAGE_DETAILS = "agenda_details_v1";
  const AUTO_SYNC_COOLDOWN_MS = 5 * 60 * 1000;

  const QUALIFIER_KEYWORDS = [
    "educatie fizica",
    "educatie muzicala",
    "educatie plastica",
    "educatie tehnologica"
  ];
  const DESCRIPTOR_ONLY_KEYWORDS = [
    "dezvoltare personala",
    "educatie pentru societate"
  ];
  const RO_MONTHS = {
    ianuarie: 1, februarie: 2, martie: 3, aprilie: 4, mai: 5, iunie: 6,
    iulie: 7, august: 8, septembrie: 9, octombrie: 10, noiembrie: 11, decembrie: 12
  };
  const RO_DAYS_FULL = ["Duminică", "Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă"];
  const RO_DAYS_SHORT = ["DUM", "LUN", "MAR", "MIE", "JOI", "VIN", "SÂM"];

  let deferredInstallPrompt = null;
  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    deferredInstallPrompt = e;
    render();
  });

  if ("serviceWorker" in navigator) {
    const hadSwController = Boolean(navigator.serviceWorker.controller);
    if (hadSwController && typeof navigator.serviceWorker.addEventListener === "function") {
      let refreshingSw = false;
      navigator.serviceWorker.addEventListener("controllerchange", () => {
        if (!refreshingSw) {
          refreshingSw = true;
          window.location.reload();
        }
      });
    }
    navigator.serviceWorker.register("/sw.js").then((reg) => {
      if (reg && typeof reg.update === "function") reg.update().catch(() => {});
    }).catch(() => {});
  }

  function loadJson(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch {
      return fallback;
    }
  }

  function saveJson(key, val) {
    try {
      if (val == null) localStorage.removeItem(key);
      else localStorage.setItem(key, JSON.stringify(val));
    } catch {}
  }

  function todayIsoLocal() {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, "0");
    const day = String(d.getDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
  }

  const state = {
    theme: localStorage.getItem(STORAGE_THEME) || "system",
    creds: loadJson(STORAGE_CREDS, null),
    sessionToken: loadJson(STORAGE_TOKEN, null),
    snapshot: loadJson(STORAGE_SNAPSHOT, null),
    hwDone: new Set(loadJson(STORAGE_HW_DONE, [])),
    tab: "home",
    selectedDateIso: todayIsoLocal(),
    highlightedSubject: null,
    selectedSemester: 1,
    isSimulatorMode: false,
    simMarks: {},
    simQualifiers: {},
    absViewBySubject: false,
    absFilter: "all", // all | unmotivated | motivated
    absSort: "newest", // newest | oldest | most
    expandedSubjects: new Set(),
    showDossier: false,
    teacherQuery: "",
    lessonDetail: null, // { lesson, loading, detail, error }
    isSyncing: false,
    syncError: null,
    loginLoading: false,
    loginError: null,
    loginUserType: 2,
    loginUsername: "",
    loginPassword: "",
    loginShowPassword: false,
    pendingScrollCenter: false,
    scheduleLayout: localStorage.getItem(STORAGE_SCHED_LAYOUT) || "grid",
    lessonDetailsCache: loadJson(STORAGE_DETAILS, {})
  };
  if (state.creds?.username) state.loginUsername = state.creds.username;

  let eventsByDate = new Map();
  let eventsById = new Map();
  let syncEpoch = 0;

  function rebuildSnapshotIndex(snap) {
    eventsByDate = new Map();
    eventsById = new Map();
    const list = snap?.scheduleEvents || [];
    for (let i = 0; i < list.length; i++) {
      const ev = list[i];
      if (ev.id) eventsById.set(ev.id, ev);
      if (!eventsByDate.has(ev.dateIso)) eventsByDate.set(ev.dateIso, []);
      eventsByDate.get(ev.dateIso).push(ev);
    }
  }

  function getLessonsForDate(dateIso) {
    return eventsByDate.get(dateIso) || [];
  }

  if (state.snapshot) rebuildSnapshotIndex(state.snapshot);

  function resolveThemeMode(mode) {
    if (mode === "system" || !mode) {
      const prefersLight = window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches;
      return prefersLight ? "light" : "amoled";
    }
    return mode;
  }

  function applyTheme(mode) {
    state.theme = mode || "system";
    try { localStorage.setItem(STORAGE_THEME, state.theme); } catch {}
    const resolved = resolveThemeMode(state.theme);
    document.documentElement.setAttribute("data-theme", resolved);
    const meta = document.getElementById("metaThemeColor");
    if (meta) {
      meta.setAttribute("content", resolved === "light" ? "#f3f4f6" : resolved === "dark" ? "#090a0d" : "#000000");
    }
  }
  applyTheme(state.theme);

  if (window.matchMedia) {
    const colorSchemeQuery = window.matchMedia("(prefers-color-scheme: light)");
    const onSystemThemeChange = () => {
      if (state.theme === "system") applyTheme("system");
    };
    if (colorSchemeQuery.addEventListener) {
      colorSchemeQuery.addEventListener("change", onSystemThemeChange);
    } else if (colorSchemeQuery.addListener) {
      colorSchemeQuery.addListener(onSystemThemeChange);
    }
  }

  function esc(str) {
    return String(str ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function normalizeSubject(s) {
    return String(s || "")
      .trim()
      .replace(/ş/g, "ș")
      .replace(/Ş/g, "Ș")
      .replace(/ţ/g, "ț")
      .replace(/Ţ/g, "Ț")
      .toLowerCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/\s+/g, " ")
      .trim();
  }

  function parseDisplayDateToIso(disp) {
    const m = String(disp || "").trim().match(/^(\d{2})\.(\d{2})\.(\d{4})$/);
    return m ? `${m[3]}-${m[2]}-${m[1]}` : null;
  }

  function parseRomanianDateToIso(txt) {
    const iso = parseDisplayDateToIso(txt);
    if (iso) return iso;
    const parts = String(txt || "").trim().toLowerCase().split(/\s+/);
    if (parts.length < 3) return null;
    const day = parseInt(parts[0], 10);
    const month = RO_MONTHS[parts[1]];
    const year = parseInt(parts[2], 10);
    if (!day || !month || !year) return null;
    return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  }

  function formatIsoShort(iso) {
    const m = String(iso || "").match(/^(\d{4})-(\d{2})-(\d{2})$/);
    return m ? `${m[3]}.${m[2]}` : iso;
  }

  function formatDisplayShort(disp) {
    const parts = String(disp || "").trim().split(".");
    return parts.length >= 2 ? `${parts[0]}.${parts[1]}` : disp;
  }

  function addDaysIso(iso, deltaDays) {
    const d = new Date(`${iso}T12:00:00`);
    d.setDate(d.getDate() + deltaDays);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }

  function getMondayIso(iso) {
    const d = new Date(`${iso}T12:00:00`);
    const day = d.getDay(); // 0=Sun..6=Sat
    const diff = day === 0 ? -6 : 1 - day;
    d.setDate(d.getDate() + diff);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  }

  function canonicalQualifier(raw) {
    const norm = normalizeSubject(raw);
    if (norm === "fb" || norm.includes("foarte bine")) return "Foarte bine";
    if (norm === "b" || norm === "bine") return "Bine";
    if (norm === "s" || norm === "suficient") return "Suficient";
    if (norm === "i" || norm.includes("insuficient") || norm.includes("nesuficient")) return "Insuficient";
    return null;
  }

  function isDescriptorOnlySubject(subjectName) {
    const norm = normalizeSubject(subjectName);
    return DESCRIPTOR_ONLY_KEYWORDS.some((k) => norm.includes(k));
  }

  function getEvaluationType(subject, allSemesterSubjects) {
    if (isDescriptorOnlySubject(subject.subject)) {
      return "DESCRIPTOR_ONLY";
    }
    if ((subject.numericGrades && subject.numericGrades.length > 0) || subject.teza != null) {
      return "NUMERIC";
    }
    if (subject.qualifiers && subject.qualifiers.length > 0) {
      return "QUALIFIER";
    }
    const norm = normalizeSubject(subject.subject);
    if (QUALIFIER_KEYWORDS.some((k) => norm.includes(k))) return "QUALIFIER";
    if (
      allSemesterSubjects &&
      allSemesterSubjects.some((s) => s.qualifiers && s.qualifiers.length > 0) &&
      allSemesterSubjects.every((s) => !s.numericGrades || s.numericGrades.length === 0)
    ) {
      return "QUALIFIER";
    }
    return "NUMERIC";
  }

  function truncateTo2(num) {
    if (num == null || Number.isNaN(Number(num))) return null;
    const n = Number(num);
    return Math.trunc(n * 100 + (n >= 0 ? 1e-9 : -1e-9)) / 100;
  }

  function formatAvg(num) {
    const t = truncateTo2(num);
    return t != null ? t.toFixed(2) : "—";
  }

  function calculateSubjectAvg(numericGrades, teza) {
    if ((!numericGrades || numericGrades.length === 0) && teza == null) return null;
    if (!numericGrades || numericGrades.length === 0) return truncateTo2(Number(teza));
    const mean = numericGrades.reduce((a, b) => a + b, 0) / numericGrades.length;
    const raw = teza != null ? (mean + Number(teza)) / 2 : mean;
    return truncateTo2(raw);
  }

  function calculateQualifierSummary(qualifiers, officialAvg) {
    const mapPts = { "Foarte bine": 4, Bine: 3, Suficient: 2, Insuficient: 1 };
    const pts = (qualifiers || []).map((q) => mapPts[canonicalQualifier(q)]).filter(Boolean);
    if (pts.length === 0) return officialAvg || null;
    const avg = Math.min(4, Math.max(1, Math.round(pts.reduce((a, b) => a + b, 0) / pts.length)));
    return ["Insuficient", "Suficient", "Bine", "Foarte bine"][avg - 1];
  }

  function getNumericTier(num) {
    if (num == null || Number.isNaN(num)) return "";
    if (num >= 9) return "high";
    if (num >= 7) return "mid";
    return "low";
  }

  function getMarkKeycapTier(dg) {
    if (dg.numericValue != null) {
      return `tier-${getNumericTier(Number(dg.numericValue))}`;
    }
    if (dg.rawText) return "tier-qual";
    return "";
  }

  function renderEvalBadge(text, { isAbsent = false, numericGrade = null, isQualifier = false, isDescriptor = false, small = false } = {}) {
    const sm = small ? " sm" : "";
    if (isAbsent) {
      return `<span class="eval-badge abs${sm}">ABS</span>`;
    }
    if (isDescriptor) {
      return `<span class="eval-badge descriptor${sm}">${esc(text)}</span>`;
    }
    const rawStr = String(text ?? "").trim();
    if (isQualifier || canonicalQualifier(rawStr) != null) {
      return `<span class="eval-badge qualifier${sm}">${esc(rawStr)}</span>`;
    }
    const parsed = numericGrade != null ? Number(numericGrade) : (/^\d+([.,]\d+)?$/.test(rawStr) ? parseFloat(rawStr.replace(",", ".")) : null);
    if (parsed != null && !Number.isNaN(parsed)) {
      const tier = getNumericTier(parsed);
      return `<span class="eval-badge grade-${tier}${sm}">${esc(rawStr)}</span>`;
    }
    return `<span class="eval-badge descriptor${sm}">${esc(rawStr)}</span>`;
  }

  function getEnrichedSemesterSubjects(sem) {
    const raw = (sem === 1 ? state.snapshot?.sem1Grades : state.snapshot?.sem2Grades) || [];
    return raw.map((sub) => {
      const simNums = state.simMarks[sub.subject] || [];
      const simQuals = state.simQualifiers[sub.subject] || [];
      const extraNums = simNums.map((v, i) => ({
        id: `sim_${i}`, numericValue: v, rawText: String(v), dateDisplay: "Sim.", isSimulated: true
      }));
      const extraQuals = simQuals.map((q, i) => ({
        id: `simq_${i}`, numericValue: null, rawText: q, dateDisplay: "Sim.", isSimulated: true
      }));
      const combinedNums = [...(sub.numericGrades || []), ...simNums];
      const combinedQuals = [...(sub.qualifiers || []), ...simQuals];
      const descriptorOnly = isDescriptorOnlySubject(sub.subject);
      return {
        ...sub,
        datedGrades: [...(sub.datedGrades || []), ...extraNums, ...extraQuals],
        numericGrades: combinedNums,
        qualifiers: combinedQuals,
        liveAverage: descriptorOnly ? null : calculateSubjectAvg(combinedNums, sub.teza)
      };
    });
  }

  function getLiveOverallAverage(sem = state.selectedSemester) {
    const subjects = getEnrichedSemesterSubjects(sem);
    const avgs = subjects.map((s) => s.liveAverage).filter((v) => v != null);
    if (avgs.length === 0) return null;
    return truncateTo2(avgs.reduce((a, b) => a + b, 0) / avgs.length);
  }

  function hasAnySimulated() {
    return Object.values(state.simMarks).some((l) => l.length > 0) ||
      Object.values(state.simQualifiers).some((l) => l.length > 0);
  }

  async function apiCall(payload) {
    const resp = await fetch("/api/sime", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    const data = await resp.json().catch(() => ({}));
    if (!resp.ok) {
      throw new Error(data.error || `Eroare server (${resp.status})`);
    }
    return data;
  }

  function clearLessonDetailsCache() {
    state.lessonDetailsCache = {};
    saveJson(STORAGE_DETAILS, null);
  }

  function lessonDetailCacheKey(lesson) {
    if (!lesson) return "";
    const studentScope = state.snapshot?.profile?.permanentCode || state.snapshot?.profile?.fullName || "";
    return `${studentScope}_${lesson.dateIso || ""}_${lesson.periodNumber || ""}_${normalizeSubject(lesson.subject || "")}`;
  }

  function applySyncedSnapshot(data, { resetSims = false, clearDetails = false } = {}) {
    if (clearDetails) clearLessonDetailsCache();
    if (data.snapshot && !data.snapshot.updatedAtMillis) {
      data.snapshot.updatedAtMillis = Date.now();
    }
    state.snapshot = data.snapshot;
    state.sessionToken = data.sessionToken;
    rebuildSnapshotIndex(data.snapshot);
    if (resetSims) {
      state.simMarks = {};
      state.simQualifiers = {};
    }
    saveJson(STORAGE_SNAPSHOT, data.snapshot);
    saveJson(STORAGE_TOKEN, data.sessionToken);
  }

  function getActiveStudentId() {
    const selected = (state.snapshot?.studentOptions || []).find((o) => o.isSelected);
    return selected?.id || state.sessionToken?.selectedStudentId || "";
  }

  function formatStudentName(raw) {
    return String(raw || "")
      .trim()
      .toLowerCase()
      .split(/\s+/)
      .filter(Boolean)
      .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
      .join(" ");
  }

  function formatStudentChipLabel(opt, allOpts) {
    const formatted = formatStudentName(opt?.name || "");
    if (!allOpts || allOpts.length <= 1) return formatted;
    const wordsList = allOpts.map((o) => formatStudentName(o?.name || "").split(" ").filter(Boolean));
    const firstWord = wordsList[0]?.[0];
    const allShareSurname = firstWord && wordsList.every((w) => w.length >= 2 && w[0] === firstWord);
    if (allShareSurname) {
      return formatted.split(" ").slice(1).join(" ") || formatted;
    }
    return formatted;
  }

  function renderStudentSwitcherBar() {
    const opts = state.snapshot?.studentOptions || [];
    if (opts.length <= 1) return "";
    return `
      <div class="student-bar">
        <div class="student-bar-inner">
          ${opts.map((o) => `
            <button type="button" class="student-chip ${o.isSelected ? "active" : ""}" data-switch-student="${esc(o.switchUrl)}" data-student-id="${esc(o.id)}" ${state.isSyncing ? "disabled" : ""}>
              ${esc(formatStudentChipLabel(o, opts))}
            </button>
          `).join("")}
        </div>
      </div>
    `;
  }

  async function syncPortal({ silent = false } = {}) {
    if (!isLoggedIn()) return;
    if (state.isSyncing) return;
    const epoch = syncEpoch;
    state.isSyncing = true;
    state.syncError = null;
    render({ part: "chrome" });
    try {
      const data = await apiCall({
        action: "sync",
        username: state.creds?.username,
        password: state.creds?.password,
        userType: state.creds?.userType || 2,
        sessionToken: state.sessionToken,
        selectedStudentId: getActiveStudentId()
      });
      if (epoch !== syncEpoch) return;
      applySyncedSnapshot(data, { clearDetails: !silent });
    } catch (err) {
      if (epoch !== syncEpoch) return;
      if (!silent || !state.snapshot) {
        state.syncError = err.message || "Sincronizarea a eșuat.";
      }
    } finally {
      if (epoch === syncEpoch) {
        state.isSyncing = false;
        if (isLoggedIn()) {
          if (state.showDossier || state.lessonDetail) {
            render({ part: "chrome" });
            render({ part: "tab" });
          } else {
            render({ part: "all" });
          }
        }
      }
    }
  }

  async function handleLoginSubmit(username, password, userType, stayLoggedIn) {
    const domUser = document.getElementById("inpUser")?.value;
    const domPass = document.getElementById("inpPass")?.value;
    const finalUser = String(username || domUser || "").trim();
    const finalPass = String(password || domPass || "");
    if (!finalUser || !finalPass) {
      state.loginError = "Introdu numele de utilizator și parola.";
      render({ part: "all" });
      return;
    }
    syncEpoch++;
    const epoch = syncEpoch;
    state.loginLoading = true;
    state.loginError = null;
    render({ part: "all" });
    try {
      const data = await apiCall({
        action: "sync",
        username: finalUser,
        password: finalPass,
        userType
      });
      if (epoch !== syncEpoch) return;
      if (stayLoggedIn) {
        state.creds = { username: finalUser, password: finalPass, userType, stayLoggedIn: true };
        saveJson(STORAGE_CREDS, state.creds);
      } else {
        state.creds = null;
        saveJson(STORAGE_CREDS, null);
      }
      applySyncedSnapshot(data, { resetSims: true, clearDetails: true });
      state.loginPassword = "";
      state.tab = "home";
    } catch (err) {
      if (epoch !== syncEpoch) return;
      state.loginError = err.message || "Autentificare eșuată.";
    } finally {
      if (epoch === syncEpoch) {
        state.loginLoading = false;
        render({ part: "all" });
      }
    }
  }

  async function openLessonDetail(lesson) {
    if (!lesson.detailUrl) return;
    const cacheKey = lessonDetailCacheKey(lesson);
    const cachedDetail = cacheKey && state.lessonDetailsCache ? state.lessonDetailsCache[cacheKey] : null;
    if (cachedDetail) {
      state.lessonDetail = { lesson, loading: false, detail: cachedDetail, error: null };
      render({ part: "modals" });
      return;
    }
    const epoch = syncEpoch;
    state.lessonDetail = { lesson, loading: true, detail: null, error: null };
    render({ part: "modals" });
    try {
      const data = await apiCall({
        action: "lessonDetail",
        detailUrl: lesson.detailUrl,
        dateIso: lesson.dateIso,
        periodNumber: lesson.periodNumber,
        subject: lesson.subject,
        username: state.creds?.username,
        password: state.creds?.password,
        userType: state.creds?.userType || 2,
        sessionToken: state.sessionToken,
        selectedStudentId: getActiveStudentId()
      });
      if (epoch !== syncEpoch) return;
      if (data.sessionToken) {
        state.sessionToken = data.sessionToken;
        saveJson(STORAGE_TOKEN, data.sessionToken);
      }
      if (cacheKey && data.detail) {
        state.lessonDetailsCache = { ...(state.lessonDetailsCache || {}), [cacheKey]: data.detail };
        saveJson(STORAGE_DETAILS, state.lessonDetailsCache);
      }
      state.lessonDetail = { lesson, loading: false, detail: data.detail, error: null };
    } catch (err) {
      if (epoch !== syncEpoch) return;
      state.lessonDetail = { lesson, loading: false, detail: null, error: err.message };
    }
    render({ part: "modals" });
  }

  async function switchStudent(switchUrl, studentId = "") {
    const targetId = studentId || "";
    const activeId = getActiveStudentId();
    if (targetId && activeId && targetId === activeId) {
      if (state.showDossier) {
        state.showDossier = false;
        render({ part: "modals" });
      }
      return;
    }
    syncEpoch++;
    const epoch = syncEpoch;
    state.showDossier = false;
    state.lessonDetail = null;
    state.highlightedSubject = null;
    state.syncError = null;
    state.isSyncing = true;
    render({ part: "all" });
    try {
      const data = await apiCall({
        action: "switchStudent",
        switchUrl,
        studentId: targetId,
        username: state.creds?.username,
        password: state.creds?.password,
        userType: state.creds?.userType || 2,
        sessionToken: state.sessionToken
      });
      if (epoch !== syncEpoch) return;
      state.expandedSubjects = new Set();
      applySyncedSnapshot(data, { resetSims: true, clearDetails: true });
    } catch (err) {
      if (epoch !== syncEpoch) return;
      state.syncError = err.message;
    } finally {
      if (epoch === syncEpoch) {
        state.isSyncing = false;
        render({ part: "all" });
      }
    }
  }

  function jumpToScheduleDate(dateIso, subjectToHighlight = null, openDetail = false) {
    state.selectedDateIso = dateIso;
    state.highlightedSubject = subjectToHighlight;
    state.tab = "schedule";
    state.pendingScrollCenter = true;
    render({ part: "tab" });
    if (openDetail && subjectToHighlight) {
      const norm = normalizeSubject(subjectToHighlight);
      const candidates = getLessonsForDate(dateIso).filter(
        (e) => normalizeSubject(e.subject) === norm
      );
      const best = candidates.find((e) => e.numericGrade != null) || candidates[0];
      if (best) openLessonDetail(best);
    }
  }

  function isLoggedIn() {
    return Boolean(state.snapshot && (state.creds || state.sessionToken));
  }

  // Icons SVG helper
  const ICONS = {
    home: `<svg viewBox="0 0 24 24"><path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/></svg>`,
    schedule: `<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/></svg>`,
    grades: `<svg viewBox="0 0 24 24"><path d="M22 10 12 5 2 10l10 5 10-5z"/><path d="M6 12v5c3 3 9 3 12 0v-5"/></svg>`,
    absences: `<svg viewBox="0 0 24 24"><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18M10 14l4 4M14 14l-4 4"/></svg>`,
    messages: `<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3 7 9 6 9-6"/></svg>`,
    refresh: `<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" fill="none" stroke-width="1.8"><path d="M21 12a9 9 0 1 1-2.64-6.36"/><path d="M21 3v6h-6"/></svg>`,
    left: `<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" fill="none" stroke-width="2"><path d="m15 18-6-6 6-6"/></svg>`,
    right: `<svg viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" fill="none" stroke-width="2"><path d="m9 18 6-6-6-6"/></svg>`
  };

  const TABS = [
    { id: "home", label: "Acasă", icon: ICONS.home },
    { id: "schedule", label: "Orar", icon: ICONS.schedule },
    { id: "grades", label: "Reușita", icon: ICONS.grades },
    { id: "absences", label: "Absențe", icon: ICONS.absences },
    { id: "messages", label: "Mesaje", icon: ICONS.messages }
  ];

  function renderLogin() {
    const saved = state.creds;
    return `
      <div class="login-wrap">
        <div class="kicker">SIME · MOLDOVA</div>
        <h1 style="margin:4px 0 6px;font-size:30px;font-weight:600;letter-spacing:-0.5px;">Agenda</h1>
        <p class="secondary" style="margin:0 0 22px;font-size:14px;">Conectează-te la catalogul tău școlar de pe orice dispozitiv</p>

        ${saved ? `
          <div id="savedCardBox" class="glass-card highlighted" style="padding:16px;margin-bottom:16px;">
            <div class="kicker">CONT SALVAT LOCAL</div>
            <div style="font-size:16px;font-weight:600;margin:5px 0 14px;">${esc(saved.username)} · ${saved.userType === 1 ? "Părinte" : "Elev"}</div>
            <button id="btnOneTap" class="btn-primary" ${state.loginLoading ? "disabled" : ""}>
              ${state.loginLoading ? "Se conectează..." : "Intră cu o singură apăsare"}
            </button>
          </div>
        ` : ""}

        <form id="loginForm" class="glass-card" style="padding:16px;display:flex;flex-direction:column;gap:14px;">
          <div class="kicker">${saved ? "SAU ALT CONT" : "AUTENTIFICARE"}</div>
          <div class="seg-trough">
            <button type="button" class="seg-btn ${state.loginUserType === 2 ? "active" : ""}" data-ut="2">Elev</button>
            <button type="button" class="seg-btn ${state.loginUserType === 1 ? "active" : ""}" data-ut="1">Părinte</button>
          </div>

          <div class="input-group">
            <label class="kicker" for="inpUser">Nume utilizator</label>
            <input id="inpUser" class="text-input" type="text" autocomplete="username" autocapitalize="none" autocorrect="off" spellcheck="false" placeholder="Introdu numele de utilizator" value="${esc(state.loginUsername)}" />
          </div>

          <div class="input-group">
            <label class="kicker" for="inpPass">Parolă</label>
            <div style="position:relative;">
              <input id="inpPass" class="text-input" type="${state.loginShowPassword ? "text" : "password"}" autocomplete="current-password" placeholder="Introdu parola" value="${esc(state.loginPassword)}" style="padding-right:70px;" />
              <button type="button" id="togglePass" style="position:absolute;right:10px;top:50%;transform:translateY(-50%);z-index:3;background:none;border:none;color:var(--text-secondary);font-size:12px;cursor:pointer;padding:6px 4px;">
                ${state.loginShowPassword ? "Ascunde" : "Arată"}
              </button>
            </div>
          </div>

          <label style="display:flex;align-items:center;justify-content:space-between;gap:10px;cursor:pointer;">
            <div>
              <div style="font-size:13px;font-weight:500;">Rămâi conectat automat</div>
              <div class="muted" style="font-size:11px;">Datele sunt salvate local în browser</div>
            </div>
            <input id="chkStay" type="checkbox" checked style="width:18px;height:18px;accent-color:var(--text-primary);" />
          </label>

          <div id="loginErrorSlot">${state.loginError ? `<div class="rose" style="font-size:13px;">${esc(state.loginError)}</div>` : ""}</div>

          <button id="btnLoginSubmit" type="submit" class="${saved ? "btn-outline" : "btn-primary"}" ${state.loginLoading ? "disabled" : ""}>
            ${state.loginLoading ? "Se autentifică..." : saved ? "Autentificare cu aceste date" : "Autentificare"}
          </button>
        </form>

        <p class="muted" style="margin-top:16px;font-size:12px;line-height:1.5;">
          Datele vin direct din agenda.sime.md și rămân stocate local pe dispozitivul tău.
        </p>
      </div>
    `;
  }

  function renderHome() {
    const s = state.snapshot;
    const events = s.scheduleEvents || [];
    const today = todayIsoLocal();
    const todayLessons = getLessonsForDate(today);

    let previewDate = today;
    let previewTitle = "Astăzi la școală";
    let previewLessons = todayLessons;

    if (previewLessons.length === 0) {
      const next = events.find((e) => e.dateIso > today);
      if (next) {
        previewDate = next.dateIso;
        const d = new Date(`${next.dateIso}T12:00:00`);
        previewTitle = `Următoarea zi (${RO_DAYS_FULL[d.getDay()]}, ${formatIsoShort(next.dateIso)})`;
        previewLessons = getLessonsForDate(next.dateIso);
      } else {
        const prev = [...events].reverse().find((e) => e.dateIso <= today);
        if (prev) {
          previewDate = prev.dateIso;
          previewTitle = `Ultima zi cu ore (${formatIsoShort(prev.dateIso)})`;
          previewLessons = getLessonsForDate(prev.dateIso);
        } else {
          previewTitle = "Orarul zilei";
        }
      }
    }

    const totalAbs = (s.cumulativeAbsences?.sem1Total || 0) + (s.cumulativeAbsences?.sem2Total || 0);
    const overall = getLiveOverallAverage(state.selectedSemester);
    const overallTxt = formatAvg(overall);

    const lessonsHtml = previewLessons.length === 0
      ? `<div class="glass-card" style="padding:14px;font-size:13px;" ><span class="secondary">Nicio lecție programată. Deschide Orar pentru săptămâni.</span></div>`
      : `<div class="glass-card">${previewLessons.map((l) => {
          const meta = [l.room ? `Cab. ${l.room}` : "", l.teacher].filter(Boolean).join(" · ");
          const isDescSub = isDescriptorOnlySubject(l.subject);
          const right = l.badgeText
            ? renderEvalBadge(l.badgeText, {
                isAbsent: l.isAbsent,
                numericGrade: isDescSub ? null : l.numericGrade,
                isQualifier: !isDescSub && l.numericGrade == null && !l.isAbsent && canonicalQualifier(l.badgeText) != null,
                isDescriptor: !l.isAbsent && (isDescSub || (l.numericGrade == null && canonicalQualifier(l.badgeText) == null)),
                small: true
              })
            : "";
          return `
            <div class="ledger-row" data-open-lesson="${esc(l.id)}">
              <span class="muted" style="width:24px;font-size:12px;">${String(l.periodNumber).padStart(2, "0")}</span>
              <span class="secondary" style="width:44px;font-size:12px;">${esc(l.startTime)}</span>
              <div style="flex:1;min-width:0;">
                <div style="font-size:14px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(l.subject)}</div>
                ${meta ? `<div class="muted" style="font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(meta)}</div>` : ""}
              </div>
              ${right}
            </div>
          `;
        }).join("")}</div>`;

    const hwItems = s.homeworkItems || [];
    const hwHtml = hwItems.length === 0
      ? `<div class="glass-card" style="padding:14px;font-size:13px;"><span class="secondary">Nu ai teme afișate pentru zilele următoare.</span></div>`
      : `<div class="glass-card">${hwItems.map((hw) => {
          const done = state.hwDone.has(hw.id);
          return `
            <div class="ledger-row" style="align-items:flex-start;" data-toggle-hw="${esc(hw.id)}">
              <input type="checkbox" ${done ? "checked" : ""} style="margin-top:2px;width:16px;height:16px;accent-color:var(--text-primary);pointer-events:none;" />
              <div style="flex:1;min-width:0;">
                <div style="display:flex;justify-content:space-between;gap:8px;">
                  <span class="${done ? "hw-done" : ""}" style="font-size:14px;font-weight:500;">${esc(hw.subject)}</span>
                  <span class="muted" style="font-size:12px;flex-shrink:0;">${esc(formatDisplayShort(hw.date))}</span>
                </div>
                <div class="${done ? "hw-done" : "secondary"}" style="font-size:13px;line-height:1.4;margin-top:3px;">${esc(hw.text)}</div>
              </div>
            </div>
          `;
        }).join("")}</div>`;

    const evals = s.recentEvaluations || [];
    const evalsHtml = evals.length === 0
      ? `<div class="glass-card" style="padding:14px;font-size:13px;"><span class="secondary">Nu există note recente.</span></div>`
      : `<div class="glass-card">${evals.map((ev) => {
          const dIso = parseDisplayDateToIso(ev.date);
          const isDescSub = isDescriptorOnlySubject(ev.subject);
          const rawGrade = String(ev.grade || "").trim();
          const isNum = !isDescSub && /^\d+([.,]\d+)?$/.test(rawGrade);
          const numVal = isNum ? parseFloat(rawGrade.replace(",", ".")) : null;
          const isQual = !isDescSub && canonicalQualifier(rawGrade) != null;
          const badge = renderEvalBadge(rawGrade, {
            numericGrade: numVal,
            isQualifier: isQual,
            isDescriptor: isDescSub || (!isNum && !isQual)
          });
          return `
            <div class="ledger-row" ${dIso ? `data-jump="${esc(dIso)}|${esc(ev.subject)}|1"` : ""}>
              <span class="muted" style="width:46px;font-size:12px;">${esc(formatDisplayShort(ev.date))}</span>
              <span style="flex:1;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(ev.subject)}</span>
              ${badge}
              ${dIso ? `<span class="muted" style="font-size:15px;">›</span>` : ""}
            </div>
          `;
        }).join("")}</div>`;

    return `
      <div class="glass-card exec-strip">
        <div class="exec-cell" data-tab="grades">
          <div class="kicker">MEDIA</div>
          <div class="exec-value">${esc(overallTxt)}</div>
        </div>
        <div class="exec-cell" data-tab="absences">
          <div class="kicker">PREZENȚĂ</div>
          <div class="exec-value">${esc(s.presencePercent)}%</div>
        </div>
        <div class="exec-cell" data-tab="absences">
          <div class="kicker">ABSENȚE</div>
          <div class="exec-value">${totalAbs}</div>
        </div>
      </div>

      ${s.hasReplacements && s.replacementsText ? `
        <div class="glass-card sim-active" style="padding:14px;">
          <div class="kicker bronze">LECȚII ÎNLOCUITE</div>
          <div style="margin-top:6px;font-size:13px;line-height:1.45;">${esc(s.replacementsText)}</div>
        </div>
      ` : ""}

      <div class="grid-two">
        <div>
          <div class="section-head">
            <span class="kicker">${esc(previewTitle)}</span>
            <button class="section-action" data-jump="${esc(previewDate)}||0">Orar complet ›</button>
          </div>
          ${lessonsHtml}
        </div>

        <div style="display:flex;flex-direction:column;gap:16px;">
          <div>
            <div class="section-head">
              <span class="kicker">Sarcini de învățare</span>
            </div>
            ${hwHtml}
          </div>

          <div>
            <div class="section-head">
              <span class="kicker">Note recente</span>
              <button class="section-action" data-tab="grades">Toate notele ›</button>
            </div>
            ${evalsHtml}
          </div>
        </div>
      </div>
    `;
  }

  function renderSchedule() {
    const todayIso = todayIsoLocal();
    const selIso = state.selectedDateIso || todayIso;
    const mondayIso = getMondayIso(selIso);
    const satIso = addDaysIso(mondayIso, 5);
    const hasSaturday = getLessonsForDate(satIso).length > 0;
    const numDays = hasSaturday ? 6 : 5;
    const weekDays = Array.from({ length: numDays }, (_, i) => addDaysIso(mondayIso, i));
    const endRange = hasSaturday ? satIso : addDaysIso(mondayIso, 4);
    const weekRange = `${formatIsoShort(mondayIso)} – ${formatIsoShort(endRange)}`;
    const dayLessonsMap = weekDays.map((dIso) => getLessonsForDate(dIso));
    const weekHasAny = dayLessonsMap.some((list) => list.length > 0);
    const isListMode = state.scheduleLayout === "list";

    const dayStripHtml = weekDays.map((dIso, idx) => {
      const d = new Date(`${dIso}T12:00:00`);
      const active = dIso === selIso;
      const evs = dayLessonsMap[idx];
      const hasMark = evs.some((e) => e.numericGrade != null || (e.badgeText && !e.isAbsent));
      const hasAbs = evs.some((e) => e.isAbsent);
      return `
        <button class="day-pill ${active ? "active" : ""}" data-select-date="${esc(dIso)}">
          <span class="kicker" style="color:${active ? "var(--text-primary)" : "var(--text-muted)"};">${RO_DAYS_SHORT[d.getDay()]}</span>
          <span style="margin-top:2px;font-size:15px;font-weight:600;color:${active ? "var(--text-primary)" : "var(--text-secondary)"};">${d.getDate()}</span>
          <span class="dot-row">
            ${hasMark ? `<span class="dot" style="width:5px;height:5px;border-radius:3px;background:var(--bronze);"></span>` : ""}
            ${hasAbs ? `<span class="dot" style="width:5px;height:5px;border-radius:3px;background:var(--badge-abs-text);"></span>` : ""}
          </span>
        </button>
      `;
    }).join("");

    const boardClass = isListMode
      ? "week-board list-mode"
      : `week-board compact-cols cols-${numDays}`;

    const weekCardsHtml = !weekHasAny
      ? `<div class="glass-card" style="padding:28px 16px;text-align:center;"><span class="secondary" style="font-size:14px;">Nu există lecții înregistrate pentru săptămâna ${esc(weekRange)}.</span></div>`
      : `<div class="${boardClass}">
          ${weekDays.map((dIso, idx) => {
            const d = new Date(`${dIso}T12:00:00`);
            const isSel = dIso === selIso;
            const isToday = dIso === todayIso;
            const lessons = dayLessonsMap[idx];
            const countLabel = lessons.length === 1 ? "1 oră" : `${lessons.length} ore`;
            const normHi = state.highlightedSubject ? normalizeSubject(state.highlightedSubject) : null;

            const rowsHtml = lessons.length === 0
              ? `<div class="muted" style="padding:14px;font-size:12px;">Nicio lecție înregistrată</div>`
              : lessons.map((l) => {
                  const rowHi = isSel && normHi && normalizeSubject(l.subject) === normHi;
                  const meta = [l.teacher, l.room ? `Cab. ${l.room}` : ""].filter(Boolean).join(" · ");
                  const isDescSub = isDescriptorOnlySubject(l.subject);
                  const rightHtml = l.badgeText
                    ? renderEvalBadge(l.badgeText, {
                        isAbsent: l.isAbsent,
                        numericGrade: isDescSub ? null : l.numericGrade,
                        isQualifier: !isDescSub && l.numericGrade == null && !l.isAbsent && canonicalQualifier(l.badgeText) != null,
                        isDescriptor: !l.isAbsent && (isDescSub || (l.numericGrade == null && canonicalQualifier(l.badgeText) == null)),
                        small: true
                      })
                    : "";
                  return `
                    <div class="ledger-row ${rowHi ? "lesson-highlight" : ""}" style="align-items:flex-start;" data-open-lesson="${esc(l.id)}">
                      <div class="lesson-time-col">
                        <span class="kicker" style="font-size:9px;">ORA ${l.periodNumber}</span>
                        <span style="font-size:12px;font-weight:500;">${esc(l.startTime)}</span>
                        <span class="muted" style="font-size:10px;">${esc(l.endTime)}</span>
                      </div>
                      <div class="v-hairline"></div>
                      <div style="flex:1;min-width:0;">
                        <div style="font-size:13.5px;font-weight:600;line-height:1.25;">${esc(l.subject)}</div>
                        ${meta ? `<div class="secondary" style="font-size:11px;margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(meta)}</div>` : ""}
                        ${l.homeworkSnippet ? `<div class="muted" style="font-size:11px;line-height:1.35;margin-top:4px;overflow-wrap:anywhere;">Temă: ${esc(l.homeworkSnippet)}</div>` : ""}
                      </div>
                      ${rightHtml}
                    </div>
                  `;
                }).join("");

            return `
              <div class="glass-card ${isSel || isToday ? "highlighted" : ""}" id="day-card-${esc(dIso)}">
                <div class="week-day-head" data-select-date="${esc(dIso)}">
                  <div style="display:flex;align-items:center;gap:6px;min-width:0;">
                    <span class="week-day-title" style="font-size:11.5px;font-weight:600;letter-spacing:0.9px;text-transform:uppercase;color:${isSel || isToday ? "var(--text-primary)" : "var(--text-secondary)"};">
                      ${RO_DAYS_FULL[d.getDay()].toUpperCase()} · ${formatIsoShort(dIso)}
                    </span>
                    ${isToday ? `<span class="badge-azi">AZI</span>` : ""}
                  </div>
                  <span class="muted" style="font-size:11px;flex-shrink:0;">${countLabel}</span>
                </div>
                ${rowsHtml}
              </div>
            `;
          }).join("")}
        </div>`;

    return `
      <div class="week-header">
        <button class="icon-btn" data-select-date="${esc(addDaysIso(selIso, -7))}" title="Săptămâna anterioară">${ICONS.left}</button>
        <div class="week-center">
          <span style="font-size:14px;font-weight:600;">${esc(weekRange)}</span>
          ${selIso !== todayIso ? `<button class="azi-pill" data-select-date="${esc(todayIso)}">Azi</button>` : ""}
          <div class="schedule-layout-toggle" role="group" aria-label="Mod afișare orar">
            <button class="schedule-layout-btn ${!isListMode ? "active" : ""}" data-sched-layout="grid">Grilă</button>
            <button class="schedule-layout-btn ${isListMode ? "active" : ""}" data-sched-layout="list">Listă</button>
          </div>
        </div>
        <button class="icon-btn" data-select-date="${esc(addDaysIso(selIso, 7))}" title="Săptămâna următoare">${ICONS.right}</button>
      </div>
      <div class="day-strip">${dayStripHtml}</div>
      ${weekCardsHtml}
    `;
  }

  function renderGrades() {
    const subjects = getEnrichedSemesterSubjects(state.selectedSemester);
    const overall = getLiveOverallAverage(state.selectedSemester);
    const anySim = hasAnySimulated();
    const totalMarks = subjects.reduce(
      (a, b) => a + (isDescriptorOnlySubject(b.subject) ? 0 : (b.numericGrades?.length || 0)),
      0
    );
    const gradedCount = subjects.filter((s) => s.liveAverage != null).length;

    const cardsHtml = subjects.length === 0
      ? `<div class="glass-card" style="padding:16px;font-size:14px;"><span class="secondary">Nu sunt evaluări pentru Semestrul ${state.selectedSemester}.</span></div>`
      : `<div class="grades-grid">${subjects.map((sub) => {
          const evalType = getEvaluationType(sub, subjects);
          const hasSimSub = (state.simMarks[sub.subject]?.length > 0) || (state.simQualifiers[sub.subject]?.length > 0);
          const qualSummary = evalType === "QUALIFIER" ? calculateQualifierSummary(sub.qualifiers, sub.officialAverage) : null;

          let rightHeader = "";
          if (evalType === "QUALIFIER") {
            rightHeader = qualSummary
              ? renderEvalBadge(qualSummary, { isQualifier: true })
              : `<span class="muted" style="font-size:15px;font-weight:600;">—</span>`;
          } else if (evalType === "DESCRIPTOR_ONLY") {
            rightHeader = renderEvalBadge("Descriptori", { isDescriptor: true });
          } else if (sub.liveAverage != null) {
            rightHeader = renderEvalBadge(formatAvg(sub.liveAverage), { numericGrade: sub.liveAverage });
          } else {
            rightHeader = `<span class="muted" style="font-size:18px;font-weight:600;">—</span>`;
          }

          const meta = [
            sub.tezaRaw ? `Teză: ${sub.tezaRaw}` : "",
            sub.officialAverage ? `Medie oficială: ${sub.officialAverage}` : ""
          ].filter(Boolean).join(" · ");

          let bodyHtml = "";
          if (sub.datedGrades && sub.datedGrades.length > 0) {
            bodyHtml = `<div class="hairline" style="margin:10px 0;"></div><div class="chips-flow">${sub.datedGrades.map((dg) => {
              const clickable = dg.isSimulated || Boolean(dg.dateIso);
              const dLabel = dg.isSimulated ? "Sim." : (dg.dateDisplay ? formatDisplayShort(dg.dateDisplay) : "");
              const tierCls = evalType === "DESCRIPTOR_ONLY"
                ? "tier-desc"
                : (dg.isSimulated ? "simulated" : getMarkKeycapTier(dg));
              const attr = dg.isSimulated
                ? `data-pop-sim="${esc(sub.subject)}|${dg.numericValue == null ? "q" : "n"}"`
                : (dg.dateIso ? `data-jump="${esc(dg.dateIso)}|${esc(sub.subject)}|1"` : "");
              return `
                <div class="mark-keycap ${tierCls} ${clickable ? "clickable" : ""}" ${attr}>
                  <span class="mark-val" style="${dg.rawText.length > 3 ? "font-size:13px;" : ""}">${esc(dg.rawText)}</span>
                  ${dLabel ? `<span style="font-size:10px;font-weight:500;opacity:0.8;">${esc(dLabel)}</span>` : ""}
                </div>
              `;
            }).join("")}</div>`;
          } else if (evalType === "DESCRIPTOR_ONLY") {
            bodyHtml = `<div class="descriptor-banner">Evaluare prin descriptori (fără note sau calificative)</div>`;
          } else {
            const emptyTxt = evalType === "QUALIFIER"
              ? "Niciun calificativ înregistrat"
              : "Nicio notă înregistrată";
            bodyHtml = `<div class="muted" style="margin-top:8px;font-size:12px;">${emptyTxt}</div>`;
          }

          let simBarHtml = "";
          if (state.isSimulatorMode && evalType !== "DESCRIPTOR_ONLY") {
            const buttons = evalType === "QUALIFIER"
              ? ["Foarte bine", "Bine", "Suficient"].map((q) =>
                  `<button class="sim-btn" data-add-sim-q="${esc(sub.subject)}|${esc(q)}">+${q}</button>`
                ).join("")
              : [10, 9, 8, 7, 6, 5].map((m) =>
                  `<button class="sim-btn" data-add-sim-n="${esc(sub.subject)}|${m}">+${m}</button>`
                ).join("");
            simBarHtml = `
              <div class="sim-bar">
                <div style="display:flex;gap:6px;flex-wrap:wrap;">${buttons}</div>
                ${hasSimSub ? `<button style="background:none;border:none;color:var(--rose);font-size:12px;cursor:pointer;" data-reset-sim="${esc(sub.subject)}">Șterge</button>` : ""}
              </div>
            `;
          }

          return `
            <div class="glass-card ${hasSimSub ? "sim-active" : ""}" style="padding:14px;">
              <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;">
                <div style="min-width:0;">
                  <div style="font-size:15px;font-weight:600;">${esc(sub.subject)}</div>
                  ${meta ? `<div class="muted" style="font-size:11px;margin-top:2px;">${esc(meta)}</div>` : ""}
                </div>
                ${rightHeader}
              </div>
              ${bodyHtml}
              ${simBarHtml}
            </div>
          `;
        }).join("")}</div>`;

    return `
      <div class="seg-trough">
        <button class="seg-btn ${state.selectedSemester === 1 ? "active" : ""}" data-sem="1">Semestrul I</button>
        <button class="seg-btn ${state.selectedSemester === 2 ? "active" : ""}" data-sem="2">Semestrul II</button>
      </div>

      <div class="glass-card ${anySim ? "sim-active" : ""}" style="padding:16px;">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;">
          <div>
            <div class="kicker">${anySim ? "MEDIA GENERALĂ · SIMULARE" : "MEDIA GENERALĂ · LIVE"}</div>
            <div class="secondary" style="font-size:12px;margin-top:4px;">${totalMarks} evaluări · ${gradedCount} discipline cu medie</div>
          </div>
          <div class="${anySim ? "bronze" : ""}" style="font-size:32px;font-weight:300;">
            ${formatAvg(overall)}
          </div>
        </div>
        <div class="hairline" style="margin:12px 0 10px;"></div>
        <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;flex-wrap:wrap;">
          <span class="muted" style="font-size:12px;">
            ${state.isSimulatorMode ? "Simulator: adaugă note sau calificative" : "Apasă pe o notă pentru a deschide lecția"}
          </span>
          <div style="display:flex;align-items:center;gap:8px;">
            ${anySim ? `<button style="background:none;border:none;color:var(--rose);font-size:12px;cursor:pointer;" data-reset-sim="">Resetează</button>` : ""}
            <button id="toggleSimBtn" class="azi-pill" style="${state.isSimulatorMode ? "color:var(--bronze);border-color:var(--bronze);" : ""}">
              ${state.isSimulatorMode ? "Închide simulator" : "Simulare medie"}
            </button>
          </div>
        </div>
      </div>

      ${cardsHtml}
    `;
  }

  function renderAbsences() {
    const s = state.snapshot;
    const cum = s.cumulativeAbsences || {};
    const total = (cum.sem1Total || 0) + (cum.sem2Total || 0);
    const mot = (cum.sem1Motivated || 0) + (cum.sem2Motivated || 0);
    const unmot = (cum.sem1Unmotivated || 0) + (cum.sem2Unmotivated || 0);
    const pres = s.presencePercent || 0;

    const absFrac = Math.max(0, 100 - pres);
    const motW = total > 0 ? (absFrac * (mot / total)) : 0;
    const unmotW = total > 0 ? (absFrac * (unmot / total)) : 0;

    const sortLabels = { newest: "↕ Noi", oldest: "↕ Vechi", most: "↕ Multe/zi" };

    let listHtml = "";
    if (!state.absViewBySubject) {
      const filtered = (s.dailyAbsences || []).filter((a) => {
        if (state.absFilter === "unmotivated") return !a.isMotivated;
        if (state.absFilter === "motivated") return a.isMotivated;
        return true;
      });
      const groupsMap = new Map();
      for (const a of filtered) {
        if (!groupsMap.has(a.date)) groupsMap.set(a.date, []);
        groupsMap.get(a.date).push(a);
      }
      const groups = [...groupsMap.entries()].map(([dateDisplay, items]) => {
        items.sort((x, y) => (parseInt(x.periodNumber, 10) || 99) - (parseInt(y.periodNumber, 10) || 99));
        return {
          dateDisplay,
          epoch: items[0]?.dateEpochDay || 0,
          items,
          motCount: items.filter((i) => i.isMotivated).length,
          unmotCount: items.filter((i) => !i.isMotivated).length
        };
      });
      if (state.absSort === "newest") groups.sort((a, b) => b.epoch - a.epoch);
      else if (state.absSort === "oldest") groups.sort((a, b) => a.epoch - b.epoch);
      else groups.sort((a, b) => b.items.length - a.items.length || b.epoch - a.epoch);

      listHtml = groups.length === 0
        ? `<div class="glass-card" style="padding:16px;"><span class="secondary">Nu există absențe pentru filtrul selectat.</span></div>`
        : `<div class="grid-two">${groups.map((g) => {
            const dIso = parseDisplayDateToIso(g.dateDisplay);
            const dayName = dIso ? RO_DAYS_FULL[new Date(`${dIso}T12:00:00`).getDay()] : "";
            const header = dayName ? `${dayName}, ${g.dateDisplay}` : g.dateDisplay;
            const summary = [
              g.unmotCount > 0 ? `${g.unmotCount} nemot.` : "",
              g.motCount > 0 ? `${g.motCount} mot.` : ""
            ].filter(Boolean).join(" · ");
            return `
              <div class="glass-card" style="padding:14px;">
                <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;${dIso ? "cursor:pointer;" : ""}" ${dIso ? `data-jump="${esc(dIso)}||0"` : ""}>
                  <span style="font-size:14px;font-weight:600;">${esc(header)}</span>
                  ${summary ? `<span class="eval-badge sm ${g.unmotCount > 0 ? "abs" : ""}">${esc(summary)}</span>` : ""}
                </div>
                <div class="chips-flow" style="margin-top:10px;gap:6px;">
                  ${g.items.map((a) => `
                    <span class="abs-lesson-pill ${a.isMotivated ? "motivated" : "unmotivated"}" ${dIso ? `data-jump="${esc(dIso)}|${esc(a.subject)}|0"` : ""}>
                      <span class="dot" style="width:5px;height:5px;border-radius:3px;background:${a.isMotivated ? "var(--text-secondary)" : "var(--badge-abs-text)"};"></span>
                      Ora ${esc(a.periodNumber)} · ${esc(a.subject)}
                    </span>
                  `).join("")}
                </div>
              </div>
            `;
          }).join("")}</div>`;
    } else {
      const subjs = (s.subjectAbsences || [])
        .map((it) => ({
          ...it,
          mot: (it.sem1Motivated || 0) + (it.sem2Motivated || 0),
          unmot: (it.sem1Unmotivated || 0) + (it.sem2Unmotivated || 0),
          tot: (it.sem1Total || 0) + (it.sem2Total || 0)
        }))
        .filter((it) => {
          if (state.absFilter === "unmotivated") return it.unmot > 0;
          if (state.absFilter === "motivated") return it.mot > 0;
          return it.tot > 0;
        })
        .sort((a, b) => {
          if (state.absFilter === "unmotivated") return b.unmot - a.unmot;
          if (state.absFilter === "motivated") return b.mot - a.mot;
          return b.tot - a.tot;
        });

      listHtml = subjs.length === 0
        ? `<div class="glass-card" style="padding:16px;"><span class="secondary">Nu există absențe pe discipline pentru filtrul selectat.</span></div>`
        : `<div class="grid-two">${subjs.map((it) => {
            const isExp = state.expandedSubjects.has(it.subject);
            const normSub = normalizeSubject(it.subject);
            const details = isExp
              ? (s.dailyAbsences || [])
                  .filter((a) => normalizeSubject(a.subject) === normSub && (
                    state.absFilter === "all" ||
                    (state.absFilter === "unmotivated" && !a.isMotivated) ||
                    (state.absFilter === "motivated" && a.isMotivated)
                  ))
                  .sort((a, b) => (b.dateEpochDay || 0) - (a.dateEpochDay || 0))
              : [];

            return `
              <div class="glass-card" style="padding:14px;cursor:pointer;" data-toggle-abs-sub="${esc(it.subject)}">
                <div style="display:flex;justify-content:space-between;align-items:center;">
                  <span style="font-size:14px;font-weight:600;">${esc(it.subject)}</span>
                  <span style="font-size:13px;font-weight:500;">${it.tot} total ${isExp ? "⌃" : "⌄"}</span>
                </div>
                <div style="font-size:11px;margin-top:6px;">
                  <span class="${it.unmot > 0 ? "rose" : "muted"}" style="font-weight:500;">${it.unmot} nemotivate</span>
                  <span class="muted"> · </span>
                  <span class="secondary">${it.mot} motivate</span>
                </div>
                <div class="progress-track" style="height:3px;margin-top:8px;">
                  ${it.unmot > 0 ? `<div style="width:${(it.unmot / it.tot) * 100}%;background:var(--rose);"></div>` : ""}
                  ${it.mot > 0 ? `<div style="width:${(it.mot / it.tot) * 100}%;background:rgba(160,163,173,0.5);"></div>` : ""}
                </div>
                ${isExp ? `
                  <div class="hairline" style="margin:10px 0;"></div>
                  <div class="chips-flow" style="gap:6px;">
                    ${details.length === 0
                      ? `<span class="muted" style="font-size:12px;">Nu există detalii zilnice sincronizate.</span>`
                      : details.map((a) => {
                          const dIso = parseDisplayDateToIso(a.date);
                          return `
                            <span class="abs-lesson-pill ${a.isMotivated ? "motivated" : "unmotivated"}" ${dIso ? `data-jump="${esc(dIso)}|${esc(it.subject)}|0"` : ""}>
                              <span class="dot" style="width:5px;height:5px;border-radius:3px;background:${a.isMotivated ? "var(--text-secondary)" : "var(--badge-abs-text)"};"></span>
                              ${esc(formatDisplayShort(a.date))} · Ora ${esc(a.periodNumber)}
                            </span>
                          `;
                        }).join("")
                    }
                  </div>
                ` : ""}
              </div>
            `;
          }).join("")}</div>`;
    }

    return `
      <div class="glass-card" style="padding:14px;">
        <div style="display:flex;justify-content:space-between;align-items:flex-end;">
          <div>
            <div class="kicker">PREZENȚĂ SEMESTRIALĂ</div>
            <div style="font-size:28px;font-weight:300;margin-top:2px;">${pres}%</div>
          </div>
          <div style="text-align:right;">
            <div style="font-size:14px;font-weight:600;">${total} absențe</div>
            <div class="muted" style="font-size:11px;margin-top:2px;">${unmot} nemot. · ${mot} mot.</div>
          </div>
        </div>
        <div class="progress-track" style="margin-top:10px;">
          <div style="width:${pres}%;background:rgba(245,245,247,0.72);"></div>
          <div style="width:${motW}%;background:rgba(160,163,173,0.42);"></div>
          <div style="width:${unmotW}%;background:var(--rose);"></div>
        </div>
        <div class="abs-filter-row">
          <button class="abs-filter-chip ${state.absFilter === "all" ? "active" : ""}" style="${state.absFilter === "all" ? "color:var(--text-primary);" : ""}" data-abs-filter="all">Toate · ${total}</button>
          <button class="abs-filter-chip ${state.absFilter === "unmotivated" ? "active" : ""}" style="${state.absFilter === "unmotivated" && unmot > 0 ? "color:var(--rose);" : ""}" data-abs-filter="unmotivated">Nemot. · ${unmot}</button>
          <button class="abs-filter-chip ${state.absFilter === "motivated" ? "active" : ""}" style="${state.absFilter === "motivated" ? "color:var(--text-primary);" : ""}" data-abs-filter="motivated">Motivate · ${mot}</button>
        </div>
      </div>

      <div style="display:flex;align-items:center;gap:8px;">
        <div class="seg-trough" style="flex:1;">
          <button class="seg-btn ${!state.absViewBySubject ? "active" : ""}" data-abs-view="days">Pe zile</button>
          <button class="seg-btn ${state.absViewBySubject ? "active" : ""}" data-abs-view="subjects">Pe discipline</button>
        </div>
        ${!state.absViewBySubject ? `<button id="cycleAbsSort" class="sort-cycle-btn">${sortLabels[state.absSort]}</button>` : ""}
      </div>

      ${listHtml}
    `;
  }

  function renderMessages() {
    const msgs = state.snapshot?.messages || [];
    if (msgs.length === 0) {
      return `<div class="glass-card" style="padding:16px;"><span class="secondary">Nu ai niciun mesaj de la profesori.</span></div>`;
    }
    return `<div class="grid-two">${msgs.map((m) => {
      const dIso = parseRomanianDateToIso(m.date);
      const kicker = [m.subject?.toUpperCase(), m.date?.toUpperCase()].filter(Boolean).join(" · ");
      return `
        <div class="glass-card" style="padding:14px;${dIso ? "cursor:pointer;" : ""}" ${dIso ? `data-jump="${esc(dIso)}|${esc(m.subject)}|1"` : ""}>
          ${kicker ? `<div class="kicker" style="margin-bottom:4px;">${esc(kicker)}</div>` : ""}
          <div style="font-size:15px;font-weight:600;">${esc(m.professor)}</div>
          <div class="secondary" style="font-size:14px;line-height:1.45;margin-top:6px;">${esc(m.text)}</div>
          ${dIso ? `
            <div class="hairline" style="margin:10px 0 8px;"></div>
            <div style="font-size:12px;font-weight:500;">Deschide lecția (${formatIsoShort(dIso)}) ›</div>
          ` : ""}
        </div>
      `;
    }).join("")}</div>`;
  }

  function renderDossierModal() {
    if (!state.showDossier) return "";
    const p = state.snapshot?.profile || {};
    const opts = state.snapshot?.studentOptions || [];

    const isIos = /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream;
    const isStandalone = window.matchMedia("(display-mode: standalone)").matches || window.navigator.standalone === true;

    let installSection = "";
    if (!isStandalone) {
      if (deferredInstallPrompt) {
        installSection = `<button id="btnPwaInstall" class="btn-outline">Instalează ca aplicație pe dispozitiv</button>`;
      } else if (isIos) {
        installSection = `
          <div class="glass-card" style="padding:12px;background:var(--surface-elevated);">
            <div class="kicker">INSTALARE PE IPHONE / IPAD</div>
            <div class="secondary" style="font-size:12px;line-height:1.45;margin-top:4px;">
              În Safari, apasă butonul <b>Share</b> (pătratul cu săgeată sus), apoi alege <b>Add to Home Screen</b> („Adaugă pe ecranul principal”).
            </div>
          </div>
        `;
      }
    }

    const row = (lbl, val) => val
      ? `<div style="display:flex;justify-content:space-between;gap:12px;padding:4px 0;font-size:13px;"><span class="muted">${lbl}</span><span style="font-weight:500;text-align:right;">${esc(val)}</span></div>`
      : "";

    return `
      <div class="modal-backdrop" id="dossierBackdrop">
        <div class="modal-sheet">
          <div class="sheet-handle"></div>
          <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:12px;">
            <div>
              <div style="font-size:19px;font-weight:600;">${esc(p.fullName || "Elev")}</div>
              ${p.schoolName ? `<div class="secondary" style="font-size:13px;margin-top:2px;">${esc(p.schoolName)}</div>` : ""}
            </div>
            <button type="button" id="closeDossier" class="icon-btn" style="font-size:18px;">✕</button>
          </div>

          ${opts.length > 1 ? `
            <div style="display:flex;flex-direction:column;gap:6px;">
              <div class="kicker">SCHIMBĂ ELEVUL</div>
              ${opts.map((o) => `
                <button type="button" class="seg-btn ${o.isSelected ? "active" : ""}" style="border:0.75px solid var(--border);" data-switch-student="${esc(o.switchUrl)}" data-student-id="${esc(o.id)}">${esc(formatStudentName(o.name))}</button>
              `).join("")}
            </div>
          ` : ""}

          <div class="glass-card" style="padding:12px;background:var(--bg);">
            ${row("Clasa", p.className)}
            ${row("Diriginte", p.homeroomTeacher)}
            ${row("Cod permanent", p.permanentCode)}
            ${row("Data nașterii", p.birthDate)}
            ${row("Adresa școlii", p.schoolAddress)}
            ${row("Telefon", p.schoolPhone)}
          </div>

          <div>
            <div class="kicker" style="margin-bottom:8px;">TEMĂ VIZUALĂ</div>
            <div class="seg-trough">
              <button type="button" class="seg-btn ${state.theme === "system" ? "active" : ""}" data-theme="system">Sistem</button>
              <button type="button" class="seg-btn ${state.theme === "amoled" ? "active" : ""}" data-theme="amoled">OLED</button>
              <button type="button" class="seg-btn ${state.theme === "dark" ? "active" : ""}" data-theme="dark">Întunecat</button>
              <button type="button" class="seg-btn ${state.theme === "light" ? "active" : ""}" data-theme="light">Luminos</button>
            </div>
          </div>

          ${installSection}

          <a href="https://github.com/IliePapanaga/SIME-UI/releases/latest/download/Agenda.apk" target="_blank" rel="noopener" style="text-decoration:none;">
            <button type="button" class="btn-outline">Descarcă aplicația Android (.apk)</button>
          </a>

          ${(state.snapshot?.teachers?.length || 0) > 0 ? `
            <div style="display:flex;flex-direction:column;gap:8px;">
              <div class="kicker">DISCIPLINE ȘI PROFESORI</div>
              <div id="teacherList" class="glass-card" style="background:var(--bg);">
                ${renderTeacherRows()}
              </div>
            </div>
          ` : ""}

          <button type="button" id="btnLogoutKeep" class="btn-outline">Deconectare (păstrează login rapid)</button>
          <button type="button" id="btnLogoutForget" style="background:none;border:none;color:var(--rose);font-size:13px;font-weight:500;cursor:pointer;padding:4px;">Șterge datele și parola salvată</button>
        </div>
      </div>
    `;
  }

  function renderTeacherRows() {
    const teachers = state.snapshot?.teachers || [];
    if (teachers.length === 0) {
      return `<div class="muted" style="padding:10px 12px;font-size:13px;">Niciun rezultat</div>`;
    }
    return teachers.map((t) => `
      <div style="display:flex;justify-content:space-between;gap:10px;padding:9px 12px;border-bottom:0.5px solid var(--hairline);font-size:13px;">
        <span style="font-weight:500;">${esc(t.subject)}</span>
        <span class="secondary" style="text-align:right;">${esc(t.teacherName)}</span>
      </div>
    `).join("");
  }

  function renderLessonModal() {
    const ld = state.lessonDetail;
    if (!ld) return "";
    const l = ld.lesson;
    let body = "";
    if (ld.loading) {
      body = `<div style="display:flex;align-items:center;justify-content:center;gap:10px;padding:24px 0;"><span class="spinner"></span><span class="secondary" style="font-size:13px;">Se încarcă detaliile lecției...</span></div>`;
    } else if (ld.error) {
      body = `<div class="rose" style="font-size:13px;">${esc(ld.error)}</div>`;
    } else if (ld.detail) {
      const d = ld.detail;
      const evalStr = String(d.evaluation || "").trim();
      const isAbsEval = evalStr.toLowerCase() === "a";
      const isNumEval = /^\d+([.,]\d+)?$/.test(evalStr);
      const evalBadge = evalStr
        ? renderEvalBadge(isAbsEval ? "Evaluare: ABS" : `Evaluare: ${evalStr}`, {
            isAbsent: isAbsEval,
            numericGrade: isNumEval ? parseFloat(evalStr.replace(",", ".")) : null,
            isQualifier: canonicalQualifier(evalStr) != null,
            isDescriptor: !isAbsEval && !isNumEval && canonicalQualifier(evalStr) == null
          })
        : "";
      const tags = [
        evalBadge,
        d.isSummative ? `<span class="eval-badge qualifier sm">Sumativă</span>` : "",
        d.isTeza ? `<span class="eval-badge descriptor sm">Teză</span>` : ""
      ].filter(Boolean).join("");

      body = `
        ${tags ? `<div class="chips-flow">${tags}</div>` : ""}
        <div class="glass-card" style="padding:13px;background:var(--surface-elevated);">
          <div class="kicker">SUBIECTUL LECȚIEI</div>
          <div style="font-size:14px;line-height:1.45;margin-top:5px;">${esc(d.topic || "Necompletat de profesor.")}</div>
        </div>
        <div class="glass-card" style="padding:13px;background:var(--surface-elevated);">
          <div class="kicker">ACTIVITĂȚI DE ÎNVĂȚARE (TEMĂ)</div>
          <div style="font-size:14px;line-height:1.45;margin-top:5px;">${esc(d.homework || "Fără temă pentru acasă.")}</div>
        </div>
        ${d.teacherMessage ? `
          <div class="glass-card" style="padding:13px;background:var(--surface-elevated);">
            <div class="kicker">MESAJUL PROFESORULUI</div>
            <div style="font-size:14px;line-height:1.45;margin-top:5px;">${esc(d.teacherMessage)}</div>
          </div>
        ` : ""}
        ${d.meetingLink ? `
          <a href="${esc(d.meetingLink)}" target="_blank" rel="noopener" style="text-decoration:none;">
            <button type="button" class="btn-outline">Deschide lecția online ↗</button>
          </a>
        ` : ""}
      `;
    }

    return `
      <div class="modal-backdrop" id="lessonBackdrop">
        <div class="modal-sheet">
          <div class="sheet-handle"></div>
          <div style="display:flex;justify-content:space-between;align-items:flex-start;gap:12px;">
            <div>
              <div style="font-size:19px;font-weight:600;">${esc(l.subject)}</div>
              <div class="secondary" style="font-size:13px;margin-top:3px;">Ora ${l.periodNumber} · ${esc(l.startTime)}–${esc(l.endTime)} · ${esc(l.dateDisplay)}</div>
            </div>
            <button id="closeLesson" class="icon-btn" style="font-size:18px;">✕</button>
          </div>
          ${body}
        </div>
      </div>
    `;
  }

  let shellMounted = false;
  let lastShellProfileKey = null;
  let lastRenderedTab = null;

  function updateChrome(p, curTab) {
    const titleEl = document.getElementById("topbarTitle");
    if (titleEl && curTab) titleEl.textContent = curTab.label;

    document.querySelectorAll("[data-tab]").forEach((el) => {
      el.classList.toggle("active", el.getAttribute("data-tab") === state.tab);
    });

    const syncSlot = document.getElementById("syncTopSlot");
    if (syncSlot) {
      const nextSync = state.isSyncing
        ? `<span class="spinner" title="Se actualizează"></span>`
        : `<button class="icon-btn" id="btnRefresh" title="Actualizează">${ICONS.refresh}</button>`;
      if (syncSlot.getAttribute("data-busy") !== String(state.isSyncing)) {
        syncSlot.setAttribute("data-busy", String(state.isSyncing));
        syncSlot.innerHTML = nextSync;
      }
    }

    const sideRefresh = document.getElementById("sidebarRefresh");
    if (sideRefresh) {
      sideRefresh.disabled = state.isSyncing;
      sideRefresh.textContent = state.isSyncing ? "Se actualizează..." : "Actualizează acum";
    }

    document.querySelectorAll(".student-chip").forEach((chip) => {
      chip.disabled = state.isSyncing;
    });

    const errSlot = document.getElementById("syncErrSlot");
    if (errSlot) {
      const errHtml = state.syncError
        ? `<div class="sync-banner" id="dismissSyncErr"><span>${esc(state.syncError)}</span><span class="secondary" style="font-weight:600;">Închide</span></div>`
        : "";
      if (errSlot.getAttribute("data-err") !== String(state.syncError || "")) {
        errSlot.setAttribute("data-err", String(state.syncError || ""));
        errSlot.innerHTML = errHtml;
      }
    }
  }

  let loginMounted = false;

  function updateLoginDomInPlace() {
    document.querySelectorAll("[data-ut]").forEach((btn) => {
      const ut = Number(btn.getAttribute("data-ut"));
      btn.classList.toggle("active", ut === state.loginUserType);
    });
    const inpPass = document.getElementById("inpPass");
    if (inpPass) {
      const nextType = state.loginShowPassword ? "text" : "password";
      if (inpPass.type !== nextType) inpPass.type = nextType;
    }
    const togglePass = document.getElementById("togglePass");
    if (togglePass) {
      togglePass.textContent = state.loginShowPassword ? "Ascunde" : "Arată";
    }
    const errSlot = document.getElementById("loginErrorSlot");
    if (errSlot) {
      errSlot.innerHTML = state.loginError
        ? `<div class="rose" style="font-size:13px;">${esc(state.loginError)}</div>`
        : "";
    }
    const btnSubmit = document.getElementById("btnLoginSubmit");
    if (btnSubmit) {
      btnSubmit.disabled = state.loginLoading;
      btnSubmit.textContent = state.loginLoading
        ? "Se autentifică..."
        : (state.creds ? "Autentificare cu aceste date" : "Autentificare");
    }
    const btnOneTap = document.getElementById("btnOneTap");
    if (btnOneTap) {
      btnOneTap.disabled = state.loginLoading;
      btnOneTap.textContent = state.loginLoading ? "Se conectează..." : "Intră cu o singură apăsare";
    }
  }

  function render({ part = "all" } = {}) {
    const root = document.getElementById("app");
    if (!root) return;

    if (!isLoggedIn()) {
      shellMounted = false;
      lastShellProfileKey = null;
      lastRenderedTab = null;
      const formExists = loginMounted && Boolean(document.getElementById("loginForm"));
      const hasSavedBox = Boolean(document.getElementById("savedCardBox"));
      if (!formExists || hasSavedBox !== Boolean(state.creds)) {
        root.innerHTML = renderLogin();
        loginMounted = true;
      } else {
        updateLoginDomInPlace();
      }
      return;
    }
    loginMounted = false;

    const p = state.snapshot.profile || {};
    const classSuffix = p.className ? `AGENDA · ${p.className}` : "AGENDA";
    const curTab = TABS.find((t) => t.id === state.tab) || TABS[0];
    const dossierLabel = p.className ? `${p.initials || "AE"} · ${p.className}` : (p.initials || "AE");
    const profileKey = `${p.fullName || ""}|${p.className || ""}|${p.shortName || ""}|${getActiveStudentId()}|${(state.snapshot?.studentOptions || []).length}`;

    if (!shellMounted || lastShellProfileKey !== profileKey) {
      root.innerHTML = `
        <div class="shell">
          <aside class="sidebar">
            <div class="sidebar-brand">
              <div class="kicker">SIME · MOLDOVA</div>
              <div style="font-size:20px;font-weight:600;margin-top:2px;">Agenda</div>
              <div class="muted" style="font-size:12px;margin-top:2px;">${esc(p.shortName || p.fullName || "")}</div>
            </div>
            <nav class="sidebar-nav">
              ${TABS.map((t) => `
                <button class="sidebar-btn ${state.tab === t.id ? "active" : ""}" data-tab="${t.id}">
                  ${t.icon}
                  <span>${t.label}</span>
                </button>
              `).join("")}
            </nav>
            <div style="display:flex;flex-direction:column;gap:8px;">
              <button class="btn-outline" id="sidebarRefresh"></button>
              <button class="btn-outline" id="sidebarDossier">Dosar & Setări</button>
            </div>
          </aside>

          <div class="main-column">
            <header class="topbar">
              <div class="topbar-inner">
                <div>
                  <div class="kicker">${esc(classSuffix)}</div>
                  <div class="topbar-title" id="topbarTitle">${esc(curTab.label)}</div>
                </div>
                <div class="topbar-actions">
                  <span id="syncTopSlot" style="display:inline-flex;align-items:center;"></span>
                  <button class="dossier-pill" id="btnDossier">${esc(dossierLabel)}</button>
                </div>
              </div>
              <div id="studentBarSlot">${renderStudentSwitcherBar()}</div>
              <div id="syncErrSlot"></div>
            </header>

            <main class="content" id="tabContent"></main>
          </div>

          <nav class="bottom-nav">
            ${TABS.map((t) => `
              <button class="nav-btn ${state.tab === t.id ? "active" : ""}" data-tab="${t.id}">
                ${t.icon}
                <span>${t.label}</span>
              </button>
            `).join("")}
          </nav>
        </div>
        <div id="modalsRoot"></div>
      `;
      shellMounted = true;
      lastShellProfileKey = profileKey;
      part = "all";
    }

    updateChrome(p, curTab);

    const tabChanged = lastRenderedTab !== null && lastRenderedTab !== state.tab;

    if (part === "all" || part === "tab") {
      const container = document.getElementById("tabContent");
      if (container) {
        let html = "";
        if (state.tab === "home") html = renderHome();
        else if (state.tab === "schedule") html = renderSchedule();
        else if (state.tab === "grades") html = renderGrades();
        else if (state.tab === "absences") html = renderAbsences();
        else if (state.tab === "messages") html = renderMessages();
        container.innerHTML = html;
      }
      lastRenderedTab = state.tab;
      if (tabChanged && typeof window !== "undefined" && window.scrollTo) {
        window.scrollTo(0, 0);
      }
    }

    if (part === "all" || part === "modals") {
      const modalsRoot = document.getElementById("modalsRoot");
      if (modalsRoot) {
        modalsRoot.innerHTML = `${renderDossierModal()}${renderLessonModal()}`;
      }
    }

    if (state.tab === "schedule" && state.pendingScrollCenter) {
      state.pendingScrollCenter = false;
      const shouldCenter = (typeof window !== "undefined" && window.innerWidth < 1180) || state.scheduleLayout === "list";
      if (shouldCenter && typeof requestAnimationFrame === "function") {
        requestAnimationFrame(() => {
          const card = document.getElementById(`day-card-${state.selectedDateIso}`);
          if (card) {
            card.scrollIntoView({ behavior: "smooth", block: "center" });
          }
        });
      }
    }
  }

  function performLogout({ forgetCreds = false } = {}) {
    syncEpoch++;
    state.isSyncing = false;
    state.loginLoading = false;
    state.showDossier = false;
    state.lessonDetail = null;
    state.snapshot = null;
    state.sessionToken = null;
    state.syncError = null;
    state.loginError = null;
    state.loginUsername = "";
    state.loginPassword = "";
    shellMounted = false;
    lastShellProfileKey = null;
    lastRenderedTab = null;
    loginMounted = false;
    eventsByDate = new Map();
    eventsById = new Map();
    clearLessonDetailsCache();
    saveJson(STORAGE_SNAPSHOT, null);
    saveJson(STORAGE_TOKEN, null);
    if (forgetCreds) {
      state.creds = null;
      saveJson(STORAGE_CREDS, null);
    }
    render({ part: "all" });
    if (typeof window !== "undefined" && window.scrollTo) {
      window.scrollTo(0, 0);
    }
  }

  function setupDelegatedEvents() {
    const root = document.getElementById("app");
    if (!root || root._agendaBound) return;
    root._agendaBound = true;

    root.addEventListener("input", (e) => {
      const t = e.target;
      if (!t) return;
      if (t.id === "inpUser") {
        state.loginUsername = t.value;
      } else if (t.id === "inpPass") {
        state.loginPassword = t.value;
      }
    });

    root.addEventListener("submit", (e) => {
      if (e.target && e.target.id === "loginForm") {
        e.preventDefault();
        const chk = document.getElementById("chkStay");
        const domUser = document.getElementById("inpUser")?.value ?? state.loginUsername;
        const domPass = document.getElementById("inpPass")?.value ?? state.loginPassword;
        state.loginUsername = domUser;
        state.loginPassword = domPass;
        handleLoginSubmit(domUser, domPass, state.loginUserType, chk ? chk.checked : true);
      }
    });

    root.addEventListener("click", async (e) => {
      const rawTarget = e.target;
      const target = rawTarget && typeof rawTarget.closest === "function"
        ? rawTarget
        : rawTarget?.parentElement;
      if (!target || typeof target.closest !== "function") return;

      if (target.closest("#btnLogoutKeep")) {
        e.preventDefault?.();
        e.stopPropagation?.();
        performLogout({ forgetCreds: false });
        return;
      }
      if (target.closest("#btnLogoutForget")) {
        e.preventDefault?.();
        e.stopPropagation?.();
        performLogout({ forgetCreds: true });
        return;
      }

      if (target.id === "dossierBackdrop" || target.closest("#closeDossier")) {
        state.showDossier = false;
        render({ part: "modals" });
        return;
      }
      if (target.id === "lessonBackdrop" || target.closest("#closeLesson")) {
        state.lessonDetail = null;
        render({ part: "modals" });
        return;
      }

      // Login actions
      if (target.closest("#togglePass")) {
        state.loginShowPassword = !state.loginShowPassword;
        render({ part: "all" });
        return;
      }
      if (target.closest("#btnOneTap") && state.creds) {
        handleLoginSubmit(state.creds.username, state.creds.password, state.creds.userType || 2, true);
        return;
      }
      const utBtn = target.closest("[data-ut]");
      if (utBtn) {
        state.loginUserType = Number(utBtn.getAttribute("data-ut"));
        render({ part: "all" });
        return;
      }

      // Navigation & Chrome
      const tabBtn = target.closest("[data-tab]");
      if (tabBtn) {
        const nextTab = tabBtn.getAttribute("data-tab");
        if (nextTab === state.tab && nextTab === "schedule") {
          state.selectedDateIso = todayIsoLocal();
          state.pendingScrollCenter = true;
          render({ part: "tab" });
          return;
        }
        state.tab = nextTab;
        if (nextTab !== "schedule") state.highlightedSubject = null;
        else state.pendingScrollCenter = true;
        render({ part: "tab" });
        return;
      }
      if (target.closest("#btnRefresh") || target.closest("#sidebarRefresh")) {
        syncPortal({ silent: false });
        return;
      }
      if (target.closest("#btnDossier") || target.closest("#sidebarDossier")) {
        state.showDossier = true;
        render({ part: "modals" });
        return;
      }
      if (target.closest("#dismissSyncErr")) {
        state.syncError = null;
        render({ part: "chrome" });
        return;
      }

      // Schedule layout & navigation
      const layoutBtn = target.closest("[data-sched-layout]");
      if (layoutBtn) {
        state.scheduleLayout = layoutBtn.getAttribute("data-sched-layout");
        try { localStorage.setItem(STORAGE_SCHED_LAYOUT, state.scheduleLayout); } catch {}
        state.pendingScrollCenter = state.scheduleLayout === "list";
        render({ part: "tab" });
        return;
      }
      const dateBtn = target.closest("[data-select-date]");
      if (dateBtn) {
        state.selectedDateIso = dateBtn.getAttribute("data-select-date");
        state.highlightedSubject = null;
        state.pendingScrollCenter = true;
        render({ part: "tab" });
        return;
      }
      const jumpBtn = target.closest("[data-jump]");
      if (jumpBtn) {
        e.stopPropagation();
        const [dIso, subj, openFlag] = jumpBtn.getAttribute("data-jump").split("|");
        jumpToScheduleDate(dIso, subj || null, openFlag === "1");
        return;
      }
      const lessonRow = target.closest("[data-open-lesson]");
      if (lessonRow) {
        const lesson = eventsById.get(lessonRow.getAttribute("data-open-lesson"));
        if (lesson) openLessonDetail(lesson);
        return;
      }
      const hwRow = target.closest("[data-toggle-hw]");
      if (hwRow) {
        const id = hwRow.getAttribute("data-toggle-hw");
        if (state.hwDone.has(id)) state.hwDone.delete(id);
        else state.hwDone.add(id);
        saveJson(STORAGE_HW_DONE, [...state.hwDone]);
        render({ part: "tab" });
        return;
      }

      // Grades controls
      const semBtn = target.closest("[data-sem]");
      if (semBtn) {
        state.selectedSemester = Number(semBtn.getAttribute("data-sem"));
        render({ part: "tab" });
        return;
      }
      if (target.closest("#toggleSimBtn")) {
        state.isSimulatorMode = !state.isSimulatorMode;
        render({ part: "tab" });
        return;
      }
      const simNumBtn = target.closest("[data-add-sim-n]");
      if (simNumBtn) {
        const [sub, val] = simNumBtn.getAttribute("data-add-sim-n").split("|");
        state.simMarks[sub] = [...(state.simMarks[sub] || []), Number(val)];
        render({ part: "tab" });
        return;
      }
      const simQualBtn = target.closest("[data-add-sim-q]");
      if (simQualBtn) {
        const [sub, q] = simQualBtn.getAttribute("data-add-sim-q").split("|");
        state.simQualifiers[sub] = [...(state.simQualifiers[sub] || []), q];
        render({ part: "tab" });
        return;
      }
      const popSimBtn = target.closest("[data-pop-sim]");
      if (popSimBtn) {
        const [sub, kind] = popSimBtn.getAttribute("data-pop-sim").split("|");
        if (kind === "q") state.simQualifiers[sub] = (state.simQualifiers[sub] || []).slice(0, -1);
        else state.simMarks[sub] = (state.simMarks[sub] || []).slice(0, -1);
        render({ part: "tab" });
        return;
      }
      const resetSimBtn = target.closest("[data-reset-sim]");
      if (resetSimBtn) {
        const sub = resetSimBtn.getAttribute("data-reset-sim");
        if (!sub) {
          state.simMarks = {};
          state.simQualifiers = {};
        } else {
          delete state.simMarks[sub];
          delete state.simQualifiers[sub];
        }
        render({ part: "tab" });
        return;
      }

      // Absences controls
      const absFilterBtn = target.closest("[data-abs-filter]");
      if (absFilterBtn) {
        state.absFilter = absFilterBtn.getAttribute("data-abs-filter");
        render({ part: "tab" });
        return;
      }
      const absViewBtn = target.closest("[data-abs-view]");
      if (absViewBtn) {
        state.absViewBySubject = absViewBtn.getAttribute("data-abs-view") === "subjects";
        render({ part: "tab" });
        return;
      }
      if (target.closest("#cycleAbsSort")) {
        const order = ["newest", "oldest", "most"];
        state.absSort = order[(order.indexOf(state.absSort) + 1) % order.length];
        render({ part: "tab" });
        return;
      }
      const absSubCard = target.closest("[data-toggle-abs-sub]");
      if (absSubCard) {
        const sub = absSubCard.getAttribute("data-toggle-abs-sub");
        if (state.expandedSubjects.has(sub)) state.expandedSubjects.delete(sub);
        else state.expandedSubjects.add(sub);
        render({ part: "tab" });
        return;
      }

      // Dossier modal actions
      const themeBtn = target.closest("[data-theme]");
      if (themeBtn) {
        applyTheme(themeBtn.getAttribute("data-theme"));
        render({ part: "modals" });
        return;
      }
      const switchBtn = target.closest("[data-switch-student]");
      if (switchBtn) {
        switchStudent(
          switchBtn.getAttribute("data-switch-student"),
          switchBtn.getAttribute("data-student-id") || ""
        );
        return;
      }
      if (target.closest("#btnPwaInstall") && deferredInstallPrompt) {
        deferredInstallPrompt.prompt();
        await deferredInstallPrompt.userChoice;
        deferredInstallPrompt = null;
        render({ part: "modals" });
        return;
      }
    });
  }

  setupDelegatedEvents();
  render({ part: "all" });
  if (isLoggedIn()) {
    const lastSync = Number(state.snapshot?.updatedAtMillis || 0);
    if (!lastSync || Date.now() - lastSync >= AUTO_SYNC_COOLDOWN_MS) {
      syncPortal({ silent: true });
    }
  }
})();
