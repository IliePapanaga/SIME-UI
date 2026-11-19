(function () {
  "use strict";

  const STORAGE_CREDS = "agenda_creds_v1";
  const STORAGE_SNAPSHOT = "agenda_snapshot_v1";
  const STORAGE_TOKEN = "agenda_token_v1";
  const STORAGE_THEME = "agenda_theme_v1";
  const STORAGE_HW_DONE = "agenda_hw_done_v1";

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
    navigator.serviceWorker.register("/sw.js").catch(() => {});
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
    theme: localStorage.getItem(STORAGE_THEME) || "amoled",
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
    pendingScrollCenter: false
  };
  if (state.creds?.username) state.loginUsername = state.creds.username;

  function applyTheme(mode) {
    state.theme = mode;
    localStorage.setItem(STORAGE_THEME, mode);
    document.documentElement.setAttribute("data-theme", mode);
    const meta = document.getElementById("metaThemeColor");
    if (meta) {
      meta.setAttribute("content", mode === "light" ? "#f3f4f6" : mode === "dark" ? "#090a0d" : "#000000");
    }
  }
  applyTheme(state.theme);

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

  function getEvaluationType(subject, allSemesterSubjects) {
    if ((subject.numericGrades && subject.numericGrades.length > 0) || subject.teza != null) {
      return "NUMERIC";
    }
    if (subject.qualifiers && subject.qualifiers.length > 0) {
      return "QUALIFIER";
    }
    const norm = normalizeSubject(subject.subject);
    if (DESCRIPTOR_ONLY_KEYWORDS.some((k) => norm.includes(k))) return "DESCRIPTOR_ONLY";
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

  function calculateSubjectAvg(numericGrades, teza) {
    if ((!numericGrades || numericGrades.length === 0) && teza == null) return null;
    if (!numericGrades || numericGrades.length === 0) return Number(teza);
    const mean = numericGrades.reduce((a, b) => a + b, 0) / numericGrades.length;
    return teza != null ? (mean + Number(teza)) / 2 : mean;
  }

  function calculateQualifierSummary(qualifiers, officialAvg) {
    const mapPts = { "Foarte bine": 4, Bine: 3, Suficient: 2, Insuficient: 1 };
    const pts = (qualifiers || []).map((q) => mapPts[canonicalQualifier(q)]).filter(Boolean);
    if (pts.length === 0) return officialAvg || null;
    const avg = Math.min(4, Math.max(1, Math.round(pts.reduce((a, b) => a + b, 0) / pts.length)));
    return ["Insuficient", "Suficient", "Bine", "Foarte bine"][avg - 1];
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
      return {
        ...sub,
        datedGrades: [...(sub.datedGrades || []), ...extraNums, ...extraQuals],
        numericGrades: combinedNums,
        qualifiers: combinedQuals,
        liveAverage: calculateSubjectAvg(combinedNums, sub.teza)
      };
    });
  }

  function getLiveOverallAverage(sem = state.selectedSemester) {
    const subjects = getEnrichedSemesterSubjects(sem);
    const avgs = subjects.map((s) => s.liveAverage).filter((v) => v != null);
    if (avgs.length === 0) return null;
    return avgs.reduce((a, b) => a + b, 0) / avgs.length;
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

  async function syncPortal({ silent = false } = {}) {
    if (!state.creds && !state.sessionToken) return;
    if (state.isSyncing) return;
    state.isSyncing = true;
    state.syncError = null;
    render();
    try {
      const data = await apiCall({
        action: "sync",
        username: state.creds?.username,
        password: state.creds?.password,
        userType: state.creds?.userType || 2,
        sessionToken: state.sessionToken
      });
      state.snapshot = data.snapshot;
      state.sessionToken = data.sessionToken;
      saveJson(STORAGE_SNAPSHOT, data.snapshot);
      saveJson(STORAGE_TOKEN, data.sessionToken);
    } catch (err) {
      if (!silent || !state.snapshot) {
        state.syncError = err.message || "Sincronizarea a eșuat.";
      }
    } finally {
      state.isSyncing = false;
      render();
    }
  }

  async function handleLoginSubmit(username, password, userType, stayLoggedIn) {
    if (!username.trim() || !password) {
      state.loginError = "Introdu numele de utilizator și parola.";
      render();
      return;
    }
    state.loginLoading = true;
    state.loginError = null;
    render();
    try {
      const data = await apiCall({
        action: "sync",
        username: username.trim(),
        password,
        userType
      });
      if (stayLoggedIn) {
        state.creds = { username: username.trim(), password, userType, stayLoggedIn: true };
        saveJson(STORAGE_CREDS, state.creds);
      } else {
        state.creds = null;
        saveJson(STORAGE_CREDS, null);
      }
      state.sessionToken = data.sessionToken;
      state.snapshot = data.snapshot;
      state.simMarks = {};
      state.simQualifiers = {};
      state.loginPassword = "";
      state.tab = "home";
      saveJson(STORAGE_TOKEN, data.sessionToken);
      saveJson(STORAGE_SNAPSHOT, data.snapshot);
    } catch (err) {
      state.loginError = err.message || "Autentificare eșuată.";
    } finally {
      state.loginLoading = false;
      render();
    }
  }

  async function openLessonDetail(lesson) {
    if (!lesson.detailUrl) return;
    state.lessonDetail = { lesson, loading: true, detail: null, error: null };
    render();
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
        sessionToken: state.sessionToken
      });
      if (data.sessionToken) {
        state.sessionToken = data.sessionToken;
        saveJson(STORAGE_TOKEN, data.sessionToken);
      }
      state.lessonDetail = { lesson, loading: false, detail: data.detail, error: null };
    } catch (err) {
      state.lessonDetail = { lesson, loading: false, detail: null, error: err.message };
    }
    render();
  }

  async function switchStudent(switchUrl) {
    state.showDossier = false;
    state.isSyncing = true;
    render();
    try {
      const data = await apiCall({
        action: "switchStudent",
        switchUrl,
        username: state.creds?.username,
        password: state.creds?.password,
        userType: state.creds?.userType || 2,
        sessionToken: state.sessionToken
      });
      state.snapshot = data.snapshot;
      state.sessionToken = data.sessionToken;
      state.simMarks = {};
      state.simQualifiers = {};
      saveJson(STORAGE_SNAPSHOT, data.snapshot);
      saveJson(STORAGE_TOKEN, data.sessionToken);
    } catch (err) {
      state.syncError = err.message;
    } finally {
      state.isSyncing = false;
      render();
    }
  }

  function jumpToScheduleDate(dateIso, subjectToHighlight = null, openDetail = false) {
    state.selectedDateIso = dateIso;
    state.highlightedSubject = subjectToHighlight;
    state.tab = "schedule";
    state.pendingScrollCenter = true;
    render();
    if (openDetail && subjectToHighlight && state.snapshot?.scheduleEvents) {
      const norm = normalizeSubject(subjectToHighlight);
      const candidates = state.snapshot.scheduleEvents.filter(
        (e) => e.dateIso === dateIso && normalizeSubject(e.subject) === norm
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
          <div class="glass-card highlighted" style="padding:16px;margin-bottom:16px;">
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
            <input id="inpUser" class="text-input" type="text" autocomplete="username" value="${esc(state.loginUsername)}" ${state.loginLoading ? "disabled" : ""} />
          </div>

          <div class="input-group">
            <label class="kicker" for="inpPass">Parolă</label>
            <div style="position:relative;">
              <input id="inpPass" class="text-input" type="${state.loginShowPassword ? "text" : "password"}" autocomplete="current-password" value="${esc(state.loginPassword)}" style="padding-right:64px;" ${state.loginLoading ? "disabled" : ""} />
              <button type="button" id="togglePass" style="position:absolute;right:10px;top:50%;transform:translateY(-50%);background:none;border:none;color:var(--text-secondary);font-size:12px;cursor:pointer;">
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

          ${state.loginError ? `<div class="rose" style="font-size:13px;">${esc(state.loginError)}</div>` : ""}

          <button type="submit" class="${saved ? "btn-outline" : "btn-primary"}" ${state.loginLoading ? "disabled" : ""}>
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
    const todayLessons = events.filter((e) => e.dateIso === today);

    let previewDate = today;
    let previewTitle = "Astăzi la școală";
    let previewLessons = todayLessons;

    if (previewLessons.length === 0) {
      const next = events.find((e) => e.dateIso > today);
      if (next) {
        previewDate = next.dateIso;
        const d = new Date(`${next.dateIso}T12:00:00`);
        previewTitle = `Următoarea zi (${RO_DAYS_FULL[d.getDay()]}, ${formatIsoShort(next.dateIso)})`;
        previewLessons = events.filter((e) => e.dateIso === next.dateIso);
      } else {
        const prev = [...events].reverse().find((e) => e.dateIso <= today);
        if (prev) {
          previewDate = prev.dateIso;
          previewTitle = `Ultima zi cu ore (${formatIsoShort(prev.dateIso)})`;
          previewLessons = events.filter((e) => e.dateIso === prev.dateIso);
        } else {
          previewTitle = "Orarul zilei";
        }
      }
    }

    const totalAbs = (s.cumulativeAbsences?.sem1Total || 0) + (s.cumulativeAbsences?.sem2Total || 0);
    const overall = getLiveOverallAverage(state.selectedSemester);
    const overallTxt = overall != null ? overall.toFixed(2) : "—";

    const lessonsHtml = previewLessons.length === 0
      ? `<div class="glass-card" style="padding:14px;font-size:13px;" ><span class="secondary">Nicio lecție programată. Deschide Orar pentru săptămâni.</span></div>`
      : `<div class="glass-card">${previewLessons.map((l) => {
          const meta = [l.room ? `Cab. ${l.room}` : "", l.teacher].filter(Boolean).join(" · ");
          const right = l.badgeText
            ? l.isAbsent
              ? `<span class="rose" style="font-size:11px;font-weight:600;letter-spacing:0.8px;">ABS</span>`
              : `<span style="font-size:17px;font-weight:600;">${esc(l.badgeText)}</span>`
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
          return `
            <div class="ledger-row" ${dIso ? `data-jump="${esc(dIso)}|${esc(ev.subject)}|1"` : ""}>
              <span class="muted" style="width:46px;font-size:12px;">${esc(formatDisplayShort(ev.date))}</span>
              <span style="flex:1;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(ev.subject)}</span>
              <span style="font-size:17px;font-weight:600;">${esc(ev.grade)}</span>
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
    const events = state.snapshot?.scheduleEvents || [];
    const todayIso = todayIsoLocal();
    const selIso = state.selectedDateIso || todayIso;
    const mondayIso = getMondayIso(selIso);
    const satIso = addDaysIso(mondayIso, 5);
    const hasSaturday = events.some((e) => e.dateIso === satIso);
    const numDays = hasSaturday ? 6 : 5;
    const weekDays = Array.from({ length: numDays }, (_, i) => addDaysIso(mondayIso, i));
    const endRange = hasSaturday ? satIso : addDaysIso(mondayIso, 4);
    const weekRange = `${formatIsoShort(mondayIso)} – ${formatIsoShort(endRange)}`;
    const weekHasAny = events.some((e) => e.dateIso >= weekDays[0] && e.dateIso <= weekDays[weekDays.length - 1]);

    const dayStripHtml = weekDays.map((dIso) => {
      const d = new Date(`${dIso}T12:00:00`);
      const active = dIso === selIso;
      const evs = events.filter((e) => e.dateIso === dIso);
      const hasMark = evs.some((e) => e.numericGrade != null);
      const hasAbs = evs.some((e) => e.isAbsent);
      return `
        <button class="day-pill ${active ? "active" : ""}" data-select-date="${esc(dIso)}">
          <span class="kicker" style="color:${active ? "var(--text-primary)" : "var(--text-muted)"};">${RO_DAYS_SHORT[d.getDay()]}</span>
          <span style="margin-top:2px;font-size:15px;font-weight:600;color:${active ? "var(--text-primary)" : "var(--text-secondary)"};">${d.getDate()}</span>
          <span class="dot-row">
            ${hasMark ? `<span class="dot" style="background:var(--text-secondary);"></span>` : ""}
            ${hasAbs ? `<span class="dot" style="background:var(--rose);"></span>` : ""}
          </span>
        </button>
      `;
    }).join("");

    const weekCardsHtml = !weekHasAny
      ? `<div class="glass-card" style="padding:28px 16px;text-align:center;"><span class="secondary" style="font-size:14px;">Nu există lecții înregistrate pentru săptămâna ${esc(weekRange)}.</span></div>`
      : `<div style="display:flex;flex-direction:column;gap:14px;">
          ${weekDays.map((dIso) => {
            const d = new Date(`${dIso}T12:00:00`);
            const isSel = dIso === selIso;
            const isToday = dIso === todayIso;
            const lessons = events.filter((e) => e.dateIso === dIso);
            const countLabel = lessons.length === 1 ? "1 oră" : `${lessons.length} ore`;
            const normHi = state.highlightedSubject ? normalizeSubject(state.highlightedSubject) : null;

            const rowsHtml = lessons.length === 0
              ? `<div class="muted" style="padding:14px;font-size:12px;">Nicio lecție înregistrată</div>`
              : lessons.map((l) => {
                  const rowHi = isSel && normHi && normalizeSubject(l.subject) === normHi;
                  const meta = [l.teacher, l.room ? `Cab. ${l.room}` : ""].filter(Boolean).join(" · ");
                  let rightHtml = "";
                  if (l.badgeText) {
                    if (l.isAbsent) {
                      rightHtml = `<span class="rose" style="font-size:10px;font-weight:600;letter-spacing:0.8px;">ABS</span>`;
                    } else if (l.numericGrade != null) {
                      rightHtml = `<div style="text-align:right;"><div class="kicker" style="font-size:8px;">NOTA</div><div style="font-size:16px;font-weight:600;">${esc(l.badgeText)}</div></div>`;
                    } else {
                      rightHtml = `<span style="font-size:12px;font-weight:600;">${esc(l.badgeText)}</span>`;
                    }
                  }
                  return `
                    <div class="ledger-row ${rowHi ? "lesson-highlight" : ""}" style="align-items:flex-start;" data-open-lesson="${esc(l.id)}">
                      <div class="lesson-time-col">
                        <span class="kicker" style="font-size:9px;">ORA ${l.periodNumber}</span>
                        <span style="font-size:12px;font-weight:500;">${esc(l.startTime)}</span>
                        <span class="muted" style="font-size:10px;">${esc(l.endTime)}</span>
                      </div>
                      <div class="v-hairline"></div>
                      <div style="flex:1;min-width:0;">
                        <div style="font-size:14px;font-weight:600;">${esc(l.subject)}</div>
                        ${meta ? `<div class="secondary" style="font-size:11px;margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${esc(meta)}</div>` : ""}
                        ${l.homeworkSnippet ? `<div class="muted" style="font-size:11px;line-height:1.35;margin-top:4px;">Temă: ${esc(l.homeworkSnippet)}</div>` : ""}
                      </div>
                      ${rightHtml}
                    </div>
                  `;
                }).join("");

            return `
              <div class="glass-card ${isSel ? "highlighted" : ""}" id="day-card-${esc(dIso)}">
                <div class="week-day-head" data-select-date="${esc(dIso)}">
                  <div style="display:flex;align-items:center;gap:8px;">
                    <span style="font-size:12px;font-weight:600;letter-spacing:1.1px;text-transform:uppercase;color:${isSel ? "var(--text-primary)" : "var(--text-secondary)"};">
                      ${RO_DAYS_FULL[d.getDay()].toUpperCase()} · ${formatIsoShort(dIso)}
                    </span>
                    ${isToday ? `<span class="badge-azi">AZI</span>` : ""}
                  </div>
                  <span class="muted" style="font-size:11px;">${countLabel}</span>
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
    const totalMarks = subjects.reduce((a, b) => a + (b.numericGrades?.length || 0), 0);
    const gradedCount = subjects.filter((s) => s.liveAverage != null).length;

    const cardsHtml = subjects.length === 0
      ? `<div class="glass-card" style="padding:16px;font-size:14px;"><span class="secondary">Nu sunt evaluări pentru Semestrul ${state.selectedSemester}.</span></div>`
      : `<div class="grades-grid">${subjects.map((sub) => {
          const evalType = getEvaluationType(sub, subjects);
          const hasSimSub = (state.simMarks[sub.subject]?.length > 0) || (state.simQualifiers[sub.subject]?.length > 0);
          const qualSummary = evalType === "QUALIFIER" ? calculateQualifierSummary(sub.qualifiers, sub.officialAverage) : null;

          let rightHeader = "";
          if (evalType === "QUALIFIER") {
            rightHeader = `<span class="${hasSimSub ? "bronze" : ""}" style="font-size:15px;font-weight:600;">${esc(qualSummary || "—")}</span>`;
          } else if (evalType === "DESCRIPTOR_ONLY") {
            rightHeader = `<span class="muted" style="font-size:12px;font-weight:500;">Descriptori</span>`;
          } else {
            const avgTxt = sub.liveAverage != null ? sub.liveAverage.toFixed(2) : "—";
            rightHeader = `<span class="${hasSimSub ? "bronze" : ""}" style="font-size:19px;font-weight:600;">${esc(avgTxt)}</span>`;
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
              const attr = dg.isSimulated
                ? `data-pop-sim="${esc(sub.subject)}|${dg.numericValue == null ? "q" : "n"}"`
                : (dg.dateIso ? `data-jump="${esc(dg.dateIso)}|${esc(sub.subject)}|1"` : "");
              return `
                <div class="mark-keycap ${clickable ? "clickable" : ""} ${dg.isSimulated ? "simulated" : ""}" ${attr}>
                  <span style="font-size:${dg.rawText.length > 3 ? 13 : 15}px;font-weight:600;">${esc(dg.rawText)}</span>
                  ${dLabel ? `<span class="${dg.isSimulated ? "bronze" : "muted"}" style="font-size:10px;font-weight:500;">${esc(dLabel)}</span>` : ""}
                </div>
              `;
            }).join("")}</div>`;
          } else {
            const emptyTxt = evalType === "DESCRIPTOR_ONLY"
              ? "Evaluare prin descriptori (fără note sau calificative)"
              : evalType === "QUALIFIER"
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
            ${overall != null ? overall.toFixed(2) : "—"}
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
                  <span class="${g.unmotCount > 0 ? "rose" : "secondary"}" style="font-size:11px;font-weight:500;">${esc(summary)}</span>
                </div>
                <div class="chips-flow" style="margin-top:10px;gap:6px;">
                  ${g.items.map((a) => `
                    <span class="abs-lesson-pill ${a.isMotivated ? "" : "unmotivated"}" ${dIso ? `data-jump="${esc(dIso)}|${esc(a.subject)}|0"` : ""}>
                      <span class="dot" style="background:${a.isMotivated ? "var(--text-muted)" : "var(--rose)"};"></span>
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
                            <span class="abs-lesson-pill ${a.isMotivated ? "" : "unmotivated"}" ${dIso ? `data-jump="${esc(dIso)}|${esc(it.subject)}|0"` : ""}>
                              <span class="dot" style="background:${a.isMotivated ? "var(--text-muted)" : "var(--rose)"};"></span>
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
    const teachers = (state.snapshot?.teachers || []).filter((t) => {
      const q = state.teacherQuery.trim().toLowerCase();
      return !q || t.subject.toLowerCase().includes(q) || t.teacherName.toLowerCase().includes(q);
    });

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
            <button id="closeDossier" class="icon-btn" style="font-size:18px;">✕</button>
          </div>

          ${opts.length > 1 ? `
            <div style="display:flex;flex-direction:column;gap:6px;">
              <div class="kicker">SCHIMBĂ ELEVUL</div>
              ${opts.map((o) => `
                <button class="seg-btn ${o.isSelected ? "active" : ""}" style="border:0.75px solid var(--border);" data-switch-student="${esc(o.switchUrl)}">${esc(o.name)}</button>
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
              <button class="seg-btn ${state.theme === "amoled" ? "active" : ""}" data-theme="amoled">Negru OLED</button>
              <button class="seg-btn ${state.theme === "dark" ? "active" : ""}" data-theme="dark">Întunecat</button>
              <button class="seg-btn ${state.theme === "light" ? "active" : ""}" data-theme="light">Luminos</button>
            </div>
          </div>

          ${installSection}

          <a href="https://github.com/IliePapanaga/SIME-UI/releases/latest/download/Agenda.apk" target="_blank" rel="noopener" style="text-decoration:none;">
            <button type="button" class="btn-outline">Descarcă aplicația Android (.apk)</button>
          </a>

          ${(state.snapshot?.teachers?.length || 0) > 0 ? `
            <div style="display:flex;flex-direction:column;gap:8px;">
              <div class="kicker">DISCIPLINE ȘI PROFESORI</div>
              <input id="inpTeacherSearch" class="text-input" placeholder="Caută disciplină sau profesor" value="${esc(state.teacherQuery)}" />
              <div class="glass-card" style="background:var(--bg);">
                ${teachers.map((t) => `
                  <div style="display:flex;justify-content:space-between;gap:10px;padding:9px 12px;border-bottom:0.5px solid var(--hairline);font-size:13px;">
                    <span style="font-weight:500;">${esc(t.subject)}</span>
                    <span class="secondary" style="text-align:right;">${esc(t.teacherName)}</span>
                  </div>
                `).join("")}
              </div>
            </div>
          ` : ""}

          <button id="btnLogoutKeep" class="btn-outline">Deconectare (păstrează login rapid)</button>
          <button id="btnLogoutForget" style="background:none;border:none;color:var(--rose);font-size:13px;font-weight:500;cursor:pointer;padding:4px;">Șterge datele și parola salvată</button>
        </div>
      </div>
    `;
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
      const tags = [
        d.evaluation ? `<span class="mark-keycap" style="padding:4px 10px;font-size:12px;font-weight:600;${d.evaluation.toLowerCase() === "a" ? "color:var(--rose);" : ""}">Evaluare: ${esc(d.evaluation)}</span>` : "",
        d.isSummative ? `<span class="mark-keycap secondary" style="padding:4px 10px;font-size:12px;">Sumativă</span>` : "",
        d.isTeza ? `<span class="mark-keycap secondary" style="padding:4px 10px;font-size:12px;">Teză</span>` : ""
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

  function render() {
    const root = document.getElementById("app");
    if (!isLoggedIn()) {
      root.innerHTML = renderLogin();
      bindEvents();
      return;
    }

    const p = state.snapshot.profile || {};
    const classSuffix = p.className ? `AGENDA · ${p.className}` : "AGENDA";
    const curTab = TABS.find((t) => t.id === state.tab) || TABS[0];
    const dossierLabel = p.className ? `${p.initials || "AE"} · ${p.className}` : (p.initials || "AE");

    let tabContent = "";
    if (state.tab === "home") tabContent = renderHome();
    else if (state.tab === "schedule") tabContent = renderSchedule();
    else if (state.tab === "grades") tabContent = renderGrades();
    else if (state.tab === "absences") tabContent = renderAbsences();
    else if (state.tab === "messages") tabContent = renderMessages();

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
            <button class="btn-outline" id="sidebarRefresh" ${state.isSyncing ? "disabled" : ""}>
              ${state.isSyncing ? "Se actualizează..." : "Actualizează acum"}
            </button>
            <button class="btn-outline" id="sidebarDossier">Dosar & Setări</button>
          </div>
        </aside>

        <div class="main-column">
          <header class="topbar">
            <div class="topbar-inner">
              <div>
                <div class="kicker">${esc(classSuffix)}</div>
                <div class="topbar-title">${curTab.label}</div>
              </div>
              <div class="topbar-actions">
                ${state.isSyncing
                  ? `<span class="spinner" title="Se actualizează"></span>`
                  : `<button class="icon-btn" id="btnRefresh" title="Actualizează">${ICONS.refresh}</button>`
                }
                <button class="dossier-pill" id="btnDossier">${esc(dossierLabel)}</button>
              </div>
            </div>
            ${state.syncError ? `
              <div class="sync-banner" id="dismissSyncErr">
                <span>${esc(state.syncError)}</span>
                <span class="secondary" style="font-weight:600;">Închide</span>
              </div>
            ` : ""}
          </header>

          <main class="content">${tabContent}</main>
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
      ${renderDossierModal()}
      ${renderLessonModal()}
    `;

    bindEvents();

    if (state.tab === "schedule" && state.pendingScrollCenter) {
      state.pendingScrollCenter = false;
      requestAnimationFrame(() => {
        const card = document.getElementById(`day-card-${state.selectedDateIso}`);
        if (card) {
          card.scrollIntoView({ behavior: "smooth", block: "center" });
        }
      });
    }
  }

  function bindEvents() {
    // Login form
    const loginForm = document.getElementById("loginForm");
    if (loginForm) {
      document.getElementById("inpUser")?.addEventListener("input", (e) => {
        state.loginUsername = e.target.value;
      });
      document.getElementById("inpPass")?.addEventListener("input", (e) => {
        state.loginPassword = e.target.value;
      });
      loginForm.addEventListener("submit", (e) => {
        e.preventDefault();
        handleLoginSubmit(state.loginUsername, state.loginPassword, state.loginUserType, document.getElementById("chkStay").checked);
      });
      document.querySelectorAll("[data-ut]").forEach((btn) => {
        btn.addEventListener("click", () => {
          state.loginUserType = Number(btn.getAttribute("data-ut"));
          render();
        });
      });
      const togglePass = document.getElementById("togglePass");
      if (togglePass) {
        togglePass.addEventListener("click", () => {
          state.loginShowPassword = !state.loginShowPassword;
          render();
        });
      }
      const btnOneTap = document.getElementById("btnOneTap");
      if (btnOneTap && state.creds) {
        btnOneTap.addEventListener("click", () => {
          handleLoginSubmit(state.creds.username, state.creds.password, state.creds.userType, true);
        });
      }
      return;
    }

    document.querySelectorAll("[data-tab]").forEach((el) => {
      el.addEventListener("click", () => {
        const target = el.getAttribute("data-tab");
        state.tab = target;
        if (target !== "schedule") state.highlightedSubject = null;
        else state.pendingScrollCenter = true;
        render();
      });
    });

    document.getElementById("btnRefresh")?.addEventListener("click", () => syncPortal({ silent: false }));
    document.getElementById("sidebarRefresh")?.addEventListener("click", () => syncPortal({ silent: false }));
    document.getElementById("btnDossier")?.addEventListener("click", () => { state.showDossier = true; render(); });
    document.getElementById("sidebarDossier")?.addEventListener("click", () => { state.showDossier = true; render(); });
    document.getElementById("dismissSyncErr")?.addEventListener("click", () => { state.syncError = null; render(); });

    document.querySelectorAll("[data-select-date]").forEach((el) => {
      el.addEventListener("click", () => {
        state.selectedDateIso = el.getAttribute("data-select-date");
        state.highlightedSubject = null;
        state.pendingScrollCenter = true;
        render();
      });
    });

    document.querySelectorAll("[data-jump]").forEach((el) => {
      el.addEventListener("click", (e) => {
        e.stopPropagation();
        const [dIso, subj, openFlag] = el.getAttribute("data-jump").split("|");
        jumpToScheduleDate(dIso, subj || null, openFlag === "1");
      });
    });

    document.querySelectorAll("[data-open-lesson]").forEach((el) => {
      el.addEventListener("click", () => {
        const id = el.getAttribute("data-open-lesson");
        const lesson = (state.snapshot?.scheduleEvents || []).find((e) => e.id === id);
        if (lesson) openLessonDetail(lesson);
      });
    });

    document.querySelectorAll("[data-toggle-hw]").forEach((el) => {
      el.addEventListener("click", () => {
        const id = el.getAttribute("data-toggle-hw");
        if (state.hwDone.has(id)) state.hwDone.delete(id);
        else state.hwDone.add(id);
        saveJson(STORAGE_HW_DONE, [...state.hwDone]);
        render();
      });
    });

    // Grades controls
    document.querySelectorAll("[data-sem]").forEach((el) => {
      el.addEventListener("click", () => {
        state.selectedSemester = Number(el.getAttribute("data-sem"));
        render();
      });
    });
    document.getElementById("toggleSimBtn")?.addEventListener("click", () => {
      state.isSimulatorMode = !state.isSimulatorMode;
      render();
    });
    document.querySelectorAll("[data-add-sim-n]").forEach((el) => {
      el.addEventListener("click", () => {
        const [sub, val] = el.getAttribute("data-add-sim-n").split("|");
        state.simMarks[sub] = [...(state.simMarks[sub] || []), Number(val)];
        render();
      });
    });
    document.querySelectorAll("[data-add-sim-q]").forEach((el) => {
      el.addEventListener("click", () => {
        const [sub, q] = el.getAttribute("data-add-sim-q").split("|");
        state.simQualifiers[sub] = [...(state.simQualifiers[sub] || []), q];
        render();
      });
    });
    document.querySelectorAll("[data-pop-sim]").forEach((el) => {
      el.addEventListener("click", () => {
        const [sub, kind] = el.getAttribute("data-pop-sim").split("|");
        if (kind === "q") state.simQualifiers[sub] = (state.simQualifiers[sub] || []).slice(0, -1);
        else state.simMarks[sub] = (state.simMarks[sub] || []).slice(0, -1);
        render();
      });
    });
    document.querySelectorAll("[data-reset-sim]").forEach((el) => {
      el.addEventListener("click", () => {
        const sub = el.getAttribute("data-reset-sim");
        if (!sub) {
          state.simMarks = {};
          state.simQualifiers = {};
        } else {
          delete state.simMarks[sub];
          delete state.simQualifiers[sub];
        }
        render();
      });
    });

    // Absences controls
    document.querySelectorAll("[data-abs-filter]").forEach((el) => {
      el.addEventListener("click", () => {
        state.absFilter = el.getAttribute("data-abs-filter");
        render();
      });
    });
    document.querySelectorAll("[data-abs-view]").forEach((el) => {
      el.addEventListener("click", () => {
        state.absViewBySubject = el.getAttribute("data-abs-view") === "subjects";
        render();
      });
    });
    document.getElementById("cycleAbsSort")?.addEventListener("click", () => {
      const order = ["newest", "oldest", "most"];
      state.absSort = order[(order.indexOf(state.absSort) + 1) % order.length];
      render();
    });
    document.querySelectorAll("[data-toggle-abs-sub]").forEach((el) => {
      el.addEventListener("click", () => {
        const sub = el.getAttribute("data-toggle-abs-sub");
        if (state.expandedSubjects.has(sub)) state.expandedSubjects.delete(sub);
        else state.expandedSubjects.add(sub);
        render();
      });
    });

    // Dossier Modal
    document.getElementById("closeDossier")?.addEventListener("click", () => { state.showDossier = false; render(); });
    document.getElementById("dossierBackdrop")?.addEventListener("click", (e) => {
      if (e.target.id === "dossierBackdrop") { state.showDossier = false; render(); }
    });
    document.querySelectorAll("[data-theme]").forEach((el) => {
      el.addEventListener("click", () => {
        applyTheme(el.getAttribute("data-theme"));
        render();
      });
    });
    document.querySelectorAll("[data-switch-student]").forEach((el) => {
      el.addEventListener("click", () => switchStudent(el.getAttribute("data-switch-student")));
    });
    document.getElementById("inpTeacherSearch")?.addEventListener("input", (e) => {
      state.teacherQuery = e.target.value;
      const pos = e.target.selectionStart;
      render();
      const inp = document.getElementById("inpTeacherSearch");
      if (inp) { inp.focus(); inp.setSelectionRange(pos, pos); }
    });
    document.getElementById("btnPwaInstall")?.addEventListener("click", async () => {
      if (!deferredInstallPrompt) return;
      deferredInstallPrompt.prompt();
      await deferredInstallPrompt.userChoice;
      deferredInstallPrompt = null;
      render();
    });
    document.getElementById("btnLogoutKeep")?.addEventListener("click", () => {
      state.showDossier = false;
      state.snapshot = null;
      state.sessionToken = null;
      saveJson(STORAGE_SNAPSHOT, null);
      saveJson(STORAGE_TOKEN, null);
      render();
    });
    document.getElementById("btnLogoutForget")?.addEventListener("click", () => {
      state.showDossier = false;
      state.snapshot = null;
      state.sessionToken = null;
      state.creds = null;
      saveJson(STORAGE_SNAPSHOT, null);
      saveJson(STORAGE_TOKEN, null);
      saveJson(STORAGE_CREDS, null);
      render();
    });

    // Lesson Detail Modal
    document.getElementById("closeLesson")?.addEventListener("click", () => { state.lessonDetail = null; render(); });
    document.getElementById("lessonBackdrop")?.addEventListener("click", (e) => {
      if (e.target.id === "lessonBackdrop") { state.lessonDetail = null; render(); }
    });
  }

  render();
  if (isLoggedIn()) {
    syncPortal({ silent: true });
  }
})();
