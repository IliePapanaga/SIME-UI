const SIME_BASE = "https://agenda.sime.md";
const PUBLIC_URL = `${SIME_BASE}/ords/r/agenda/main/public`;
const BROWSER_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";

function decodeHtml(str) {
  if (!str) return "";
  return String(str)
    .replace(/&#x([0-9a-fA-F]+);/g, (_, hex) => String.fromCodePoint(parseInt(hex, 16)))
    .replace(/&#(\d+);/g, (_, dec) => String.fromCodePoint(parseInt(dec, 10)))
    .replace(/&nbsp;/gi, " ")
    .replace(/&amp;/gi, "&")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/gi, "'")
    .replace(/&apos;/gi, "'");
}

function stripTags(html) {
  if (!html) return "";
  return decodeHtml(
    String(html)
      .replace(/<br\s*\/?>/gi, "\n")
      .replace(/<\/(p|div|li|tr|h1|h2|h3|h4)>/gi, "\n")
      .replace(/<[^>]+>/g, " ")
  )
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .replace(/[ \t]{2,}/g, " ")
    .trim();
}

function stripInlineTags(html) {
  if (!html) return "";
  return decodeHtml(String(html).replace(/<[^>]+>/g, " "))
    .replace(/\s+/g, " ")
    .trim();
}

function parseSetCookieHeaders(headers) {
  if (typeof headers.getSetCookie === "function") {
    return headers.getSetCookie();
  }
  const raw = headers.get("set-cookie");
  if (!raw) return [];
  return raw.split(/,(?=[^;]+?=)/);
}

function collectCookies(jar, headers) {
  for (const sc of parseSetCookieHeaders(headers)) {
    const firstPart = sc.split(";")[0];
    const eq = firstPart.indexOf("=");
    if (eq <= 0) continue;
    const name = firstPart.slice(0, eq).trim();
    const val = firstPart.slice(eq + 1).trim();
    if (!val || val === '""' || val.toLowerCase() === "deleted") {
      jar.delete(name);
    } else {
      jar.set(name, val);
    }
  }
}

function cookieHeaderFromJar(jar) {
  return [...jar.entries()].map(([k, v]) => `${k}=${v}`).join("; ");
}

function jarFromCookieString(str) {
  const jar = new Map();
  if (!str) return jar;
  for (const pair of str.split(";")) {
    const eq = pair.indexOf("=");
    if (eq <= 0) continue;
    jar.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
  }
  return jar;
}

async function fetchWithCookies(jar, url, options = {}) {
  let currentUrl = url.startsWith("http") ? url : `${SIME_BASE}${url}`;
  let method = options.method || "GET";
  let body = options.body;
  let headers = { ...(options.headers || {}) };

  for (let hop = 0; hop < 6; hop++) {
    const cookieStr = cookieHeaderFromJar(jar);
    const reqHeaders = {
      "User-Agent": BROWSER_UA,
      ...headers
    };
    if (cookieStr) reqHeaders["Cookie"] = cookieStr;

    const resp = await fetch(currentUrl, {
      method,
      headers: reqHeaders,
      body: method === "GET" || method === "HEAD" ? undefined : body,
      redirect: "manual"
    });
    collectCookies(jar, resp.headers);

    if (resp.status >= 300 && resp.status < 400) {
      const loc = resp.headers.get("location");
      if (!loc) return { resp, finalUrl: currentUrl, text: await resp.text() };
      currentUrl = new URL(loc, currentUrl).toString();
      if (resp.status === 303 || (resp.status !== 307 && resp.status !== 308 && method === "POST")) {
        method = "GET";
        body = undefined;
        delete headers["Content-Type"];
      }
      continue;
    }

    const text = await resp.text();
    return { resp, finalUrl: currentUrl, text };
  }
  throw new Error("Prea multe redirecționări din partea agenda.sime.md.");
}

function attrMatch(html, regex) {
  const m = html.match(regex);
  return m ? decodeHtml(m[1]).trim() : "";
}

function hiddenInputValue(html, nameOrId) {
  const escaped = nameOrId.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const r1 = new RegExp(`<input[^>]+(?:id|name)=["']${escaped}["'][^>]*value=["']([^"']*)["']`, "i");
  const m1 = html.match(r1);
  if (m1) return decodeHtml(m1[1]).trim();
  const r2 = new RegExp(`<input[^>]+value=["']([^"']*)["'][^>]*(?:id|name)=["']${escaped}["']`, "i");
  const m2 = html.match(r2);
  return m2 ? decodeHtml(m2[1]).trim() : "";
}

function dataForChecksum(html, itemName) {
  const escaped = itemName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const r1 = new RegExp(`data-for=["']${escaped}["'][^>]*value=["']([^"']*)["']`, "i");
  const m1 = html.match(r1);
  if (m1) return decodeHtml(m1[1]).trim();
  const r2 = new RegExp(`value=["']([^"']*)["'][^>]*data-for=["']${escaped}["']`, "i");
  const m2 = html.match(r2);
  return m2 ? decodeHtml(m2[1]).trim() : "";
}

async function performApexLogin(jar, username, password, userType) {
  const pubRes = await fetchWithCookies(jar, PUBLIC_URL);
  const decodedPub = decodeHtml(pubRes.text)
    .replace(/\\u002F/g, "/")
    .replace(/\\u0026/g, "&")
    .replace(/\\u003D/g, "=");

  const apexCode = Number(userType) === 1 ? 1 : 2;
  const dlgRegex = new RegExp(`(/ords/r/agenda/main/login_desktop\\?p101_user_type=${apexCode}[^'"]+)`);
  const dlgMatch = decodedPub.match(dlgRegex);
  if (!dlgMatch) {
    throw new Error("Nu s-a găsit dialogul de autentificare pe agenda.sime.md.");
  }

  const dlgRes = await fetchWithCookies(jar, dlgMatch[1]);
  const dlgHtml = dlgRes.text;

  const pInstance = hiddenInputValue(dlgHtml, "p_instance") || hiddenInputValue(dlgHtml, "pInstance");
  const pSubId = hiddenInputValue(dlgHtml, "p_page_submission_id") || hiddenInputValue(dlgHtml, "pPageSubmissionId");
  const pContext = hiddenInputValue(dlgHtml, "pContext");
  const pSalt = hiddenInputValue(dlgHtml, "pSalt");
  const utVal = hiddenInputValue(dlgHtml, "P101_USER_TYPE");
  const utCk = dataForChecksum(dlgHtml, "P101_USER_TYPE");
  const utnVal = hiddenInputValue(dlgHtml, "P101_USER_TYPE_NAME");
  const utnCk = dataForChecksum(dlgHtml, "P101_USER_TYPE_NAME");
  const pProtected = hiddenInputValue(dlgHtml, "pPageItemsProtected");

  if (!pInstance || !pSubId || !pSalt) {
    throw new Error("Formularul de autentificare SIME nu a putut fi citit.");
  }

  const pJson = JSON.stringify({
    pageItems: {
      itemsToSubmit: [
        { n: "P101_USER_TYPE", v: utVal, ck: utCk },
        { n: "P101_USER_TYPE_NAME", v: utnVal, ck: utnCk },
        { n: "P101_USERNAME", v: username.trim() },
        { n: "P101_PASSWORD", v: password }
      ],
      protected: pProtected,
      rowVersion: "",
      formRegionChecksums: []
    },
    salt: pSalt
  });

  const form = new URLSearchParams({
    p_flow_id: "104",
    p_flow_step_id: "101",
    p_instance: pInstance,
    p_debug: "",
    p_request: "LOGIN",
    p_reload_on_submit: "S",
    p_page_submission_id: pSubId,
    p_json: pJson
  });

  const acceptRes = await fetchWithCookies(
    jar,
    `${SIME_BASE}/ords/wwv_flow.accept?p_context=${encodeURIComponent(pContext)}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
        "X-Requested-With": "XMLHttpRequest"
      },
      body: form.toString()
    }
  );

  let acceptData;
  try {
    acceptData = JSON.parse(acceptRes.text);
  } catch {
    throw new Error("Răspuns invalid de la agenda.sime.md la autentificare.");
  }

  if (acceptData.error || acceptData.errors?.length) {
    const errText = acceptData.error?.message || acceptData.errors?.[0]?.message || "Autentificare eșuată.";
    const err = new Error(stripTags(errText));
    err.statusCode = 401;
    throw err;
  }

  const redirUrl = acceptData.redirectURL || "";
  const b64Match = redirUrl.match(/atob\(["']([^"']+)["']\)/);
  if (!b64Match) {
    const err = new Error("Nume de utilizator sau parolă incorectă.");
    err.statusCode = 401;
    throw err;
  }

  const mainPath = Buffer.from(b64Match[1], "base64").toString("utf8");
  const mainRes = await fetchWithCookies(jar, mainPath);
  if (!mainRes.finalUrl.includes("/main-page") || !mainRes.text.includes("RegBadge")) {
    const err = new Error("Sesiunea nu s-a putut deschide după autentificare.");
    err.statusCode = 401;
    throw err;
  }
  return mainRes;
}

function unescapeApexId(raw) {
  return (raw || "").replace(/\\u002F/g, "/").replace(/\\u0026/g, "&").replace(/\\u003D/g, "=");
}

async function callApexAjax(jar, mCtx, paramsObj) {
  const form = new URLSearchParams();
  for (const [k, v] of Object.entries(paramsObj)) {
    if (v !== undefined && v !== null) form.set(k, String(v));
  }
  const url = `${SIME_BASE}/ords/wwv_flow.ajax?p_context=${encodeURIComponent(mCtx)}`;
  const res = await fetchWithCookies(jar, url, {
    method: "POST",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
      "X-Requested-With": "XMLHttpRequest"
    },
    body: form.toString()
  });
  return res.text;
}

function parseTableRows(tableHtml) {
  const rows = [];
  const rowMatches = tableHtml.matchAll(/<tr\b[^>]*>([\s\S]*?)<\/tr>/gi);
  for (const rm of rowMatches) {
    const rowHtml = rm[1];
    if (/<th\b/i.test(rowHtml) && !/<td\b/i.test(rowHtml)) continue;
    const cells = [];
    const cellMatches = rowHtml.matchAll(/<t[dh]\b([^>]*)>([\s\S]*?)<\/t[dh]>/gi);
    for (const cm of cellMatches) {
      const attrs = cm[1] || "";
      const headerMatch = attrs.match(/headers=["']([^"']+)["']/i);
      cells.push({
        header: headerMatch ? headerMatch[1].trim() : "",
        html: cm[2],
        text: stripInlineTags(cm[2])
      });
    }
    if (cells.length > 0) rows.push({ cells, raw: rowHtml });
  }
  return rows;
}

function parseSectionById(html, elementId, endMarker) {
  const startIdx = html.indexOf(`id="${elementId}"`);
  if (startIdx < 0) return "";
  const endIdx = endMarker ? html.indexOf(endMarker, startIdx) : -1;
  return endIdx > startIdx ? html.slice(startIdx, endIdx) : html.slice(startIdx, startIdx + 50000);
}

function normalizeSubject(subject) {
  return String(subject || "")
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

function canonicalQualifier(raw) {
  const norm = normalizeSubject(raw);
  if (norm === "fb" || norm.includes("foarte bine")) return "Foarte bine";
  if (norm === "b" || norm === "bine") return "Bine";
  if (norm === "s" || norm === "suficient") return "Suficient";
  if (norm === "i" || norm.includes("insuficient") || norm.includes("nesuficient")) return "Insuficient";
  return null;
}

function parseDisplayDateToIso(dateDisplay) {
  const m = String(dateDisplay || "").trim().match(/^(\d{2})\.(\d{2})\.(\d{4})$/);
  return m ? `${m[3]}-${m[2]}-${m[1]}` : null;
}

function parseDisplayDateToEpochDay(dateDisplay) {
  const iso = parseDisplayDateToIso(dateDisplay);
  if (!iso) return 0;
  return Math.floor(Date.parse(`${iso}T00:00:00Z`) / 86400000);
}

function calculateSubjectAverage(numericGrades, teza) {
  if ((!numericGrades || numericGrades.length === 0) && teza == null) return null;
  if (!numericGrades || numericGrades.length === 0) return Number(teza);
  const mean = numericGrades.reduce((a, b) => a + b, 0) / numericGrades.length;
  return teza != null ? (mean + Number(teza)) / 2 : mean;
}

function parseSemesterGradesHtml(reportHtml, semester) {
  if (!reportHtml) return [];
  const tableMatch =
    reportHtml.match(new RegExp(`<table[^>]*id=["']report_table_sem${semester}["'][^>]*>[\\s\\S]*?<\\/table>`, "i")) ||
    reportHtml.match(/<table[^>]*class=["'][^"']*t-Report-report[^"']*["'][^>]*>[\s\S]*?<\/table>/i);
  if (!tableMatch) return [];

  const rows = parseTableRows(tableMatch[0]);
  const subjects = [];
  for (const row of rows) {
    const subjCell = row.cells.find((c) => c.header === "DISCIPLINA") || row.cells[0];
    const subject = subjCell?.text?.trim();
    if (!subject) continue;

    const listaCell = row.cells.find((c) => c.header.startsWith("LISTA_EVALUATION")) || row.cells[1];
    const tezaCell = row.cells.find((c) => c.header.startsWith("TEZA")) || row.cells[2];
    const medCell = row.cells.find((c) => c.header.startsWith("MED_SEM")) || row.cells[3];

    const rawText = listaCell?.text?.trim() || "";
    const entries = rawText
      ? rawText.split(",").map((s) => s.trim()).filter(Boolean)
      : [];

    const numericGrades = [];
    const qualifiers = [];
    const datedGrades = entries.map((token, idx) => {
      const parsedInt = /^\d+$/.test(token) ? parseInt(token, 10) : null;
      if (parsedInt != null) numericGrades.push(parsedInt);
      else qualifiers.push(token);
      return {
        id: `s${semester}_${subject}_${idx}_${token}`,
        numericValue: parsedInt,
        rawText: token,
        dateDisplay: null,
        dateIso: null,
        periodNumber: null,
        lessonId: null,
        detailUrl: null,
        isSimulated: false
      };
    });

    const tezaRaw = tezaCell?.text?.trim() || null;
    const teza = tezaRaw && /^\d+$/.test(tezaRaw) ? parseInt(tezaRaw, 10) : null;
    const officialAverage = medCell?.text?.trim() || null;

    subjects.push({
      semester,
      subject,
      rawEntries: entries,
      datedGrades,
      numericGrades,
      qualifiers,
      teza,
      tezaRaw,
      officialAverage,
      liveAverage: calculateSubjectAverage(numericGrades, teza)
    });
  }
  return subjects;
}

function enrichGradesWithDates(subjects, recentEvaluations, scheduleEvents) {
  const poolBySubject = new Map();
  const seenKeys = new Set();

  const sortedEvents = [...scheduleEvents]
    .filter((ev) => ev.dateIso && !ev.isAbsent && (ev.numericGrade != null || ev.badgeText))
    .sort((a, b) => a.dateIso.localeCompare(b.dateIso) || a.periodNumber - b.periodNumber);

  for (const ev of sortedEvents) {
    const subjKey = normalizeSubject(ev.subject);
    const grade = ev.numericGrade;
    const qualifier = grade == null && ev.badgeText ? canonicalQualifier(ev.badgeText) : null;
    if (grade == null && qualifier == null) continue;
    const tokenKey = grade != null ? String(grade) : qualifier;
    const dedupKey = `${subjKey}|${ev.dateDisplay}|${tokenKey}|${ev.periodNumber}`;
    if (seenKeys.has(dedupKey)) continue;
    seenKeys.add(dedupKey);
    if (!poolBySubject.has(subjKey)) poolBySubject.set(subjKey, []);
    poolBySubject.get(subjKey).push({
      grade,
      qualifier,
      dateDisplay: ev.dateDisplay,
      dateIso: ev.dateIso,
      periodNumber: ev.periodNumber,
      lessonId: ev.id,
      detailUrl: ev.detailUrl
    });
  }

  for (const ev of recentEvaluations) {
    const raw = (ev.grade || "").trim();
    const grade = /^\d+$/.test(raw) ? parseInt(raw, 10) : null;
    const qualifier = grade == null ? canonicalQualifier(raw) : null;
    if (grade == null && qualifier == null) continue;
    const subjKey = normalizeSubject(ev.subject);
    const dateIso = parseDisplayDateToIso(ev.date);
    if (!dateIso) continue;
    if (!poolBySubject.has(subjKey)) poolBySubject.set(subjKey, []);
    const list = poolBySubject.get(subjKey);
    const exists = list.some(
      (x) =>
        x.dateDisplay === ev.date.trim() &&
        ((grade != null && x.grade === grade) || (qualifier != null && x.qualifier === qualifier))
    );
    if (!exists) {
      list.push({
        grade,
        qualifier,
        dateDisplay: ev.date.trim(),
        dateIso,
        periodNumber: null,
        lessonId: null,
        detailUrl: null
      });
    }
  }

  for (const list of poolBySubject.values()) {
    list.sort((a, b) => a.dateIso.localeCompare(b.dateIso) || (a.periodNumber || 0) - (b.periodNumber || 0));
  }

  return subjects.map((sub) => {
    const subjKey = normalizeSubject(sub.subject);
    const available = [...(poolBySubject.get(subjKey) || [])];
    const matched = new Array(sub.datedGrades.length);

    for (let i = sub.datedGrades.length - 1; i >= 0; i--) {
      const g = sub.datedGrades[i];
      const qual = canonicalQualifier(g.rawText);
      let matchIdx = -1;
      if (g.numericValue != null) {
        for (let k = available.length - 1; k >= 0; k--) {
          if (available[k].grade === g.numericValue) {
            matchIdx = k;
            break;
          }
        }
      } else if (qual != null) {
        for (let k = available.length - 1; k >= 0; k--) {
          if (available[k].qualifier === qual) {
            matchIdx = k;
            break;
          }
        }
      }
      if (matchIdx >= 0) {
        const occ = available.splice(matchIdx, 1)[0];
        matched[i] = {
          ...g,
          dateDisplay: occ.dateDisplay,
          dateIso: occ.dateIso,
          periodNumber: occ.periodNumber,
          lessonId: occ.lessonId,
          detailUrl: occ.detailUrl
        };
      } else {
        matched[i] = g;
      }
    }

    return {
      ...sub,
      datedGrades: matched,
      liveAverage: calculateSubjectAverage(sub.numericGrades, sub.teza)
    };
  });
}

function extractDialogUrl(rawUrl) {
  if (!rawUrl) return null;
  if (rawUrl.startsWith("http")) return rawUrl;
  const decodedRaw = decodeHtml(rawUrl)
    .replace(/\\u002F/g, "/")
    .replace(/\\u0026/g, "&")
    .replace(/\\u003D/g, "=");
  const m = decodedRaw.match(/apex\.theme42\.dialog\(\s*['"]([^'"]+)['"]/);
  if (!m) return null;
  const p = m[1];
  if (p.startsWith("http")) return p;
  if (p.startsWith("/")) return `${SIME_BASE}${p}`;
  return `${SIME_BASE}/ords/r/agenda/main/${p}`;
}

function extractTagByClass(html, cls) {
  const escaped = cls.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const r = new RegExp(`<(div|span)\\b[^>]*class=["'][^"']*\\b${escaped}\\b[^"']*["'][^>]*>([\\s\\S]*?)<\\/\\1>`, "i");
  const m = html.match(r);
  return m ? m[2] : "";
}

function parseCalendarEventsArray(jsonStr) {
  let arr;
  try {
    arr = JSON.parse(jsonStr);
  } catch {
    return [];
  }
  if (!Array.isArray(arr)) return [];

  const events = [];
  for (let i = 0; i < arr.length; i++) {
    const obj = arr[i];
    if (!obj) continue;
    const start = String(obj.start || "");
    const end = String(obj.end || "");
    const titleHtml = String(obj.title || "");
    const dateIso = start.split("T")[0] || "";
    const parts = dateIso.split("-");
    const dateDisplay = parts.length === 3 ? `${parts[2]}.${parts[1]}.${parts[0]}` : dateIso;

    const orderText = stripInlineTags(extractTagByClass(titleHtml, "lesson-order"));
    const periodNumber = /^\d+$/.test(orderText) ? parseInt(orderText, 10) : i + 1;

    const timeBlockMatch = titleHtml.match(/class=["'][^"']*lesson-time[^"']*["'][^>]*>([\s\S]*?)<\/(div|span)>/i);
    const timeSpans = timeBlockMatch
      ? [...timeBlockMatch[1].matchAll(/<span[^>]*>([\s\S]*?)<\/span>/gi)].map((x) => stripInlineTags(x[1]))
      : [];
    const startTime = timeSpans[0] || start.split("T")[1]?.slice(0, 5) || "";
    const endTime = timeSpans[1] || end.split("T")[1]?.slice(0, 5) || "";

    const subject = stripInlineTags(extractTagByClass(titleHtml, "lesson-text-wrap"));
    const room = stripInlineTags(extractTagByClass(titleHtml, "lesson-room")) || null;
    const teacher = stripInlineTags(extractTagByClass(titleHtml, "lesson-teacher"));
    const homeworkSnippet = stripInlineTags(extractTagByClass(titleHtml, "lesson-homework")) || null;
    const badgeText = stripInlineTags(extractTagByClass(titleHtml, "lesson-grade")) || null;

    const numericGrade = badgeText && /^\d+$/.test(badgeText) ? parseInt(badgeText, 10) : null;
    const isAbsent = badgeText ? badgeText.toLowerCase() === "a" : false;
    const detailUrl = extractDialogUrl(obj.url || "");

    events.push({
      id: `${dateIso}_${periodNumber}_${subject}`,
      dateIso,
      dateDisplay,
      periodNumber,
      startTime,
      endTime,
      subject,
      room,
      teacher,
      homeworkSnippet,
      badgeText,
      numericGrade,
      isAbsent,
      detailUrl
    });
  }

  return events.sort((a, b) => a.dateIso.localeCompare(b.dateIso) || a.periodNumber - b.periodNumber);
}

function parseMainPageData(mc, sem1Html, sem2Html, calJsonStr, messagesHtml = "") {
  const sessionId = hiddenInputValue(mc, "pInstance");

  // Profile
  const dosarBlock = parseSectionById(mc, "dosarRegion", "id=\"reusita-container\"");
  const studentNameMatch = mc.match(/id=["']student-name["'][^>]*>([\s\S]*?)<\/[a-z0-9]+>/i);
  const fullName = studentNameMatch ? stripInlineTags(studentNameMatch[1]) : hiddenInputValue(mc, "P1_ELEVE");
  const shortName = fullName
    ? fullName
        .toLowerCase()
        .split(/\s+/)
        .filter(Boolean)
        .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
        .join(" ")
    : "";

  const infoMap = {};
  for (const m of dosarBlock.matchAll(/class=["'][^"']*info-item[^"']*["'][^>]*>([\s\S]*?)<\/div>/gi)) {
    const txt = stripInlineTags(m[1]);
    const colon = txt.indexOf(":");
    if (colon > 0) {
      infoMap[txt.slice(0, colon).trim().toLowerCase()] = txt.slice(colon + 1).trim();
    }
  }

  const findInfo = (needle) =>
    Object.entries(infoMap).find(([k]) => k.includes(needle))?.[1] || "";

  const initials = (fullName || "AE")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("") || "AE";

  const profile = {
    fullName,
    shortName: shortName || fullName,
    initials,
    birthDate: findInfo("nașter") || findInfo("naster") || hiddenInputValue(mc, "P1_BIRTH_DATE"),
    permanentCode: findInfo("cod") || hiddenInputValue(mc, "P1_ELEVE_UK"),
    className: findInfo("clas") || hiddenInputValue(mc, "P1_CLASS_NAME"),
    homeroomTeacher: findInfo("dirig") || hiddenInputValue(mc, "P1_DIRIGINTE"),
    schoolName: hiddenInputValue(mc, "P1_INSTITUTION_NAME"),
    schoolAddress: hiddenInputValue(mc, "P1_ADDRESS"),
    schoolPhone: hiddenInputValue(mc, "P1_PHONE"),
    schoolWebsite: hiddenInputValue(mc, "P1_WEBSITE"),
    schoolDirector: hiddenInputValue(mc, "P1_DIRECTOR")
  };

  // Multi-student options
  const studentOptions = [];
  const navMatch = mc.match(/<nav[^>]*aria-label=["']Application["'][^>]*>([\s\S]*?)<\/nav>/i) ||
    mc.match(/class=["'][^"']*t-TreeNav[^"']*["'][^>]*>([\s\S]*?)<\/ul>/i);
  if (navMatch) {
    for (const am of navMatch[1].matchAll(/<li([^>]*)>\s*<a[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi)) {
      const liAttrs = am[1] || "";
      const href = decodeHtml(am[2]).trim();
      const name = stripInlineTags(am[3]);
      if (!name || !href) continue;
      const idM = href.match(/user_eleve_id=([A-Za-z0-9]+)/);
      studentOptions.push({
        id: idM ? idM[1] : name,
        name,
        switchUrl: href.startsWith("http") ? href : `${SIME_BASE}${href}`,
        isSelected: /is-current/.test(liAttrs)
      });
    }
  }

  // Presence
  const hiddenPresence = hiddenInputValue(mc, "P1_PRESENCE_PERCENT").replace("%", "").trim();
  let presencePercent = /^\d+$/.test(hiddenPresence) ? parseInt(hiddenPresence, 10) : 0;
  if (!presencePercent) {
    const pm = mc.match(/class=["'][^"']*presence-fill[^"']*["'][^>]*style=["'][^"']*width\s*:\s*(\d+)%/i);
    if (pm) presencePercent = parseInt(pm[1], 10);
  }

  // Homework (#P1_HOMEWORK or #homework_list)
  const homeworkItems = [];
  const hwSource = hiddenInputValue(mc, "P1_HOMEWORK") ||
    (mc.match(/id=["']homework_list["'][^>]*>([\s\S]*?)<\/ul>/i)?.[1] || "");
  if (hwSource) {
    let curDate = "";
    for (const tok of hwSource.matchAll(/<(b|strong|li)\b[^>]*>([\s\S]*?)<\/\1>/gi)) {
      const tag = tok[1].toLowerCase();
      if (tag === "b" || tag === "strong") {
        curDate = stripInlineTags(tok[2]);
      } else {
        const spans = [...tok[2].matchAll(/<span[^>]*>([\s\S]*?)<\/span>/gi)].map((s) => stripInlineTags(s[1]));
        let subject = "";
        let text = "";
        if (spans.length >= 2) {
          subject = spans[0].replace(/:$/, "").trim();
          text = spans.slice(1).join(" ").trim();
        } else {
          const raw = stripInlineTags(tok[2]);
          const idx = raw.indexOf(":");
          subject = idx > 0 ? raw.slice(0, idx).trim() : "";
          text = idx > 0 ? raw.slice(idx + 1).trim() : raw;
        }
        if (subject || text) {
          homeworkItems.push({
            id: `${curDate}_${subject}_${text.length}_${text.slice(0, 16)}`,
            date: curDate,
            subject,
            text
          });
        }
      }
    }
  }

  // Recent evaluations (#P1_LAST_3_EVAL_LIST or #last3-eval)
  const recentEvaluations = [];
  const l3Source = hiddenInputValue(mc, "P1_LAST_3_EVAL_LIST") ||
    (mc.match(/id=["']last3-eval["'][^>]*>([\s\S]*?)<\/ul>/i)?.[1] || "");
  if (l3Source) {
    if (l3Source.trim().startsWith("[")) {
      try {
        for (const item of JSON.parse(l3Source)) {
          const date = item.EVAL_DATE || item.evalDate || item.date || "";
          const subject = item.DISCIPLINY_NAME || item.discipline || item.subject || "";
          const grade = String(item.EVALUATION || item.grade || "");
          if (subject || grade) recentEvaluations.push({ date, subject, grade });
        }
      } catch {}
    } else {
      for (const li of l3Source.matchAll(/<li[^>]*>([\s\S]*?)<\/li>/gi)) {
        const dm = li[1].match(/eval-date[^>]*>([\s\S]*?)<\/span>/i);
        const sm = li[1].match(/eval-discipline[^>]*>([\s\S]*?)<\/span>/i);
        const gm = li[1].match(/class=["'][^"']*grade[^"']*["'][^>]*>([\s\S]*?)<\/span>/i);
        const date = dm ? stripInlineTags(dm[1]) : "";
        const subject = sm ? stripInlineTags(sm[1]).replace(/:$/, "").trim() : "";
        const grade = gm ? stripInlineTags(gm[1]) : "";
        if (subject || grade) recentEvaluations.push({ date, subject, grade });
      }
    }
  }

  // Replacements
  let replacementsText = "";
  let hasReplacements = false;
  const repMatch = mc.match(/id=["']replacements["'][^>]*>([\s\S]*?)<\/div>/i);
  if (repMatch) {
    if (/empty-replacements/i.test(repMatch[1])) {
      replacementsText = stripInlineTags(repMatch[1]);
      hasReplacements = false;
    } else {
      replacementsText = stripInlineTags(repMatch[1]);
      hasReplacements = Boolean(replacementsText);
    }
  }

  // Absences (#P1_JSON_ABSENCES + #report_table_abs-region + cumulative)
  const dailyAbsences = [];
  const absJsonRaw = hiddenInputValue(mc, "P1_JSON_ABSENCES");
  if (absJsonRaw && absJsonRaw.trim().startsWith("[")) {
    try {
      for (const a of JSON.parse(absJsonRaw)) {
        const date = String(a.lessonDate || "").trim();
        const absName = String(a.absenceName || "").trim().toLowerCase();
        const isMotivated = absName.includes("motivat") && !absName.includes("nemotivat");
        dailyAbsences.push({
          date,
          dateEpochDay: parseDisplayDateToEpochDay(date),
          periodNumber: String(a.lessonOrderId ?? ""),
          subject: String(a.discipliny || "").trim(),
          isMotivated,
          cumulativeCount: Number(a.cumulativeAbsences || 0),
          monthTitle: String(a.monthYear || "").trim()
        });
      }
    } catch {}
  }

  // Subject absences (#report_table_abs-region)
  const subjectAbsences = [];
  const absTableMatch = mc.match(/id=["']report_table_abs-region["'][^>]*>([\s\S]*?)<\/table>/i);
  if (absTableMatch) {
    const seenSubj = new Set();
    for (const row of parseTableRows(absTableMatch[0])) {
      const txt = row.cells.map((c) => c.text).join(" ").replace(/\s+/g, " ").trim();
      if (!/Semestrul\s*1/i.test(txt)) continue;
      const subject = txt.split(/Semestru/i)[0].trim();
      if (!subject || seenSubj.has(subject)) continue;
      seenSubj.add(subject);
      const s1 = txt.match(/Semestrul\s*1\s+(\d+)\s+(\d+)\s+(\d+)/i);
      const s2 = txt.match(/Semestrul\s*2\s+(\d+)\s+(\d+)\s+(\d+)/i);
      if (s1) {
        subjectAbsences.push({
          subject,
          sem1Motivated: parseInt(s1[1], 10),
          sem1Unmotivated: parseInt(s1[2], 10),
          sem1Total: parseInt(s1[3], 10),
          sem2Motivated: s2 ? parseInt(s2[1], 10) : 0,
          sem2Unmotivated: s2 ? parseInt(s2[2], 10) : 0,
          sem2Total: s2 ? parseInt(s2[3], 10) : 0
        });
      }
    }
  }

  const m1 = parseInt(hiddenInputValue(mc, "P1_MOTIVATED"), 10);
  const u1 = parseInt(hiddenInputValue(mc, "P1_UNMOTIVATED"), 10);
  const t1 = parseInt(hiddenInputValue(mc, "P1_TOTAL_ABSENCES1"), 10);
  const m2 = parseInt(hiddenInputValue(mc, "P1_MOTIVATED2"), 10);
  const u2 = parseInt(hiddenInputValue(mc, "P1_UNMOTIVATED2"), 10);
  const t2 = parseInt(hiddenInputValue(mc, "P1_TOTAL_ABSENCES2"), 10);

  let cumulativeAbsences;
  if (!Number.isNaN(m1) || !Number.isNaN(u1) || !Number.isNaN(t1)) {
    const mot1 = Number.isNaN(m1) ? 0 : m1;
    const unmot1 = Number.isNaN(u1) ? 0 : u1;
    const mot2 = Number.isNaN(m2) ? 0 : m2;
    const unmot2 = Number.isNaN(u2) ? 0 : u2;
    cumulativeAbsences = {
      sem1Motivated: mot1,
      sem1Unmotivated: unmot1,
      sem1Total: Number.isNaN(t1) ? mot1 + unmot1 : t1,
      sem2Motivated: mot2,
      sem2Unmotivated: unmot2,
      sem2Total: Number.isNaN(t2) ? mot2 + unmot2 : t2
    };
  } else {
    const mot = dailyAbsences.filter((a) => a.isMotivated).length;
    const unmot = dailyAbsences.filter((a) => !a.isMotivated).length;
    cumulativeAbsences = {
      sem1Motivated: mot,
      sem1Unmotivated: unmot,
      sem1Total: mot + unmot,
      sem2Motivated: 0,
      sem2Unmotivated: 0,
      sem2Total: 0
    };
  }

  // Messages (from AJAX get_messages response)
  const messages = [];
  const msgSource = messagesHtml || parseSectionById(mc, "messageContainer", "id=\"P1_INSTITUTION_NAME\"");
  const cardBlocks = msgSource.split(/(?=<div[^>]*class=["'][^"']*\bcard\b(?![^"']*-)[^"']*["'])/i).filter((b) => /\bcard\b/i.test(b));
  for (const block of cardBlocks) {
    const dm = block.match(/class=["'][^"']*\bdate\b[^"']*["'][^>]*>([\s\S]*?)<\/[^>]+>/i);
    const pm = block.match(/class=["'][^"']*\bfrom\b[^"']*["'][^>]*>([\s\S]*?)<\/[^>]+>/i);
    const sm = block.match(/class=["'][^"']*\bcard-discipline\b[^"']*["'][^>]*>([\s\S]*?)<\/[^>]+>/i);
    const tm = block.match(/class=["'][^"']*\bcard-text\b[^"']*["'][^>]*>([\s\S]*?)<\/[^>]+>/i);
    const date = dm ? stripInlineTags(dm[1]) : "";
    const professor = pm ? stripInlineTags(pm[1]) : "";
    const subject = sm ? stripInlineTags(sm[1]) : "";
    const text = tm ? stripInlineTags(tm[1]) : "";
    if (professor || text) {
      messages.push({
        id: `${date}_${professor}_${subject}_${text.slice(0, 16)}`,
        date,
        professor,
        subject,
        text
      });
    }
  }

  // Teachers (#report_table_R546889360489434893)
  const teachers = [];
  const teacherTbl = mc.match(/<table[^>]*id=["']report_table_R546889360489434893["'][^>]*>([\s\S]*?)<\/table>/i);
  if (teacherTbl) {
    for (const row of parseTableRows(teacherTbl[0])) {
      if (row.cells.length < 2) continue;
      const subject = row.cells[0].text;
      const teacherName = row.cells[1].text;
      if (subject || teacherName) teachers.push({ subject, teacherName });
    }
  }

  const scheduleEvents = parseCalendarEventsArray(calJsonStr);
  const sem1Grades = enrichGradesWithDates(parseSemesterGradesHtml(sem1Html, 1), recentEvaluations, scheduleEvents);
  const sem2Grades = enrichGradesWithDates(parseSemesterGradesHtml(sem2Html, 2), recentEvaluations, scheduleEvents);

  return {
    updatedAtMillis: Date.now(),
    sessionId,
    profile,
    studentOptions,
    presencePercent,
    homeworkItems,
    recentEvaluations,
    replacementsText,
    hasReplacements,
    sem1Grades,
    sem2Grades,
    dailyAbsences,
    subjectAbsences,
    cumulativeAbsences,
    messages,
    teachers,
    scheduleEvents
  };
}

function parseLessonDetailHtml(html) {
  const periodText = attrMatch(html, /class=["'][^"']*lesson-hero-badge[^"']*["'][^>]*>([\s\S]*?)<\/[^>]+>/i) ||
    hiddenInputValue(html, "P19_LESSON_ORDER_ID");
  const periodNumber = /^\d+$/.test(periodText) ? parseInt(periodText, 10) : 0;

  const subject = attrMatch(html, /class=["'][^"']*lesson-hero-title[^"']*["'][^>]*>([\s\S]*?)<\/[^>]+>/i) ||
    hiddenInputValue(html, "P19_DISCIPLINY_NAME");

  const dateMatch = html.match(/class=["'][^"']*lesson-hero-date[^"']*["'][^>]*>[\s\S]*?<span[^>]*>([\s\S]*?)<\/span>/i);
  const date = (dateMatch ? stripInlineTags(dateMatch[1]) : "") || hiddenInputValue(html, "P19_LESSON_DATE");

  let topic = "";
  let teacherMessage = "";
  let evaluation = null;
  let homework = "";
  let homeworkDueDate = null;
  let homeworkCreatedDate = null;

  for (const secM of html.matchAll(/class=["'][^"']*card-section[^"']*["'][^>]*>([\s\S]*?)(?=<div[^>]*class=["'][^"']*card-section|<\/div>\s*<\/div>\s*<\/div>)/gi)) {
    const secHtml = secM[1];
    const hMatch = secHtml.match(/<h3[^>]*>([\s\S]*?)<\/h3>/i);
    const heading = (hMatch ? stripInlineTags(hMatch[1]) : "").toLowerCase();
    const ltMatch = secHtml.match(/class=["'][^"']*lesson-text[^"']*["'][^>]*>([\s\S]*?)<\/[^>]+>/i);

    if (heading.includes("subiect")) {
      topic = ltMatch ? stripTags(ltMatch[1]) : "";
    } else if (heading.includes("mesaj")) {
      const rawMsg = ltMatch ? stripTags(ltMatch[1]) : "";
      teacherMessage = rawMsg.toLowerCase() === "nota / absență" ? "" : rawMsg;
      for (const sm of secHtml.matchAll(/<(?:span|div)[^>]*>([\s\S]*?)<\/(?:span|div)>/gi)) {
        const t = stripInlineTags(sm[1]);
        if (/^(\d{1,2}|a|A|Foarte bine|Bine|Suficient|Insuficient)$/.test(t)) {
          evaluation = t;
          break;
        }
      }
    } else if (heading.includes("activit")) {
      for (const liM of secHtml.matchAll(/<li[^>]*>([\s\S]*?)<\/li>/gi)) {
        const t = stripInlineTags(liM[1]);
        if (/^Pentru data de:/i.test(t)) {
          homeworkDueDate = t.split(":").slice(1).join(":").trim() || null;
        } else if (/^Data complet/i.test(t)) {
          homeworkCreatedDate = t.split(":").slice(1).join(":").trim() || null;
        }
      }
      const hwEl = secHtml.match(/class=["'][^"']*(?:lesson-text|homework-text)[^"']*["'][^>]*>([\s\S]*?)<\/[^>]+>/i);
      homework = hwEl ? stripTags(hwEl[1]) : "";
    }
  }

  if (!topic) topic = hiddenInputValue(html, "P19_LESSON_SUBJECT");
  if (!homework) homework = hiddenInputValue(html, "P19_HOME_WORK_TXT");
  if (!evaluation) evaluation = hiddenInputValue(html, "P19_EVALUATION") || null;

  const meetingLink =
    hiddenInputValue(html, "P19_MEETING_ROOM_LINK") ||
    attrMatch(html, /class=["'][^"']*lesson-view[\s\S]*?<a[^>]*href=["'](https?:\/\/[^"']+)["']/i) ||
    null;

  const isSummative =
    hiddenInputValue(html, "P19_SUMATIVA") === "1" ||
    hiddenInputValue(html, "P19_SUMATIVA").toUpperCase() === "Y" ||
    /sumativ/i.test(stripTags(html));
  const isTeza =
    hiddenInputValue(html, "P19_TEZA_FLG") === "1" ||
    hiddenInputValue(html, "P19_TEZA_FLG").toUpperCase() === "Y";

  return {
    subject,
    periodNumber,
    date,
    topic,
    teacherMessage,
    evaluation,
    homework,
    homeworkDueDate,
    homeworkCreatedDate,
    isSummative,
    isTeza,
    meetingLink
  };
}

async function fetchFullPortalFromMainPage(jar, mainRes) {
  const mc = mainRes.text;
  const mInst = hiddenInputValue(mc, "p_instance") || hiddenInputValue(mc, "pInstance");
  const mCtx = hiddenInputValue(mc, "pContext");
  const mSalt = hiddenInputValue(mc, "pSalt");
  const mProt = hiddenInputValue(mc, "pPageItemsProtected");
  const eleveId = hiddenInputValue(mc, "P1_ELEVE_ID");
  const eleveCk = dataForChecksum(mc, "P1_ELEVE_ID");

  const sem1Match = mc.match(/report\.init\(\s*["']sem1["']\s*,\s*["']([^"']+)["']/);
  const sem2Match = mc.match(/report\.init\(\s*["']sem2["']\s*,\s*["']([^"']+)["']/);
  const calMatch = mc.match(/"ajaxIdentifier"\s*:\s*"(UkVHSU9OIFRZUEV-fjU0Njg4NjE2[^"]+)"/);

  const sem1Ajax = sem1Match ? unescapeApexId(sem1Match[1]) : "";
  const sem2Ajax = sem2Match ? unescapeApexId(sem2Match[1]) : "";
  const calAjax = calMatch ? unescapeApexId(calMatch[1]) : "";

  const reportPayload = JSON.stringify({
    pageItems: {
      itemsToSubmit: eleveId ? [{ n: "P1_ELEVE_ID", v: eleveId, ck: eleveCk }] : [],
      protected: mProt,
      rowVersion: "",
      formRegionChecksums: []
    },
    salt: mSalt
  });

  const studyYearStart = parseInt(hiddenInputValue(mc, "P1_STUDY_YEAR"), 10) || new Date().getFullYear();
  const calStart = `${studyYearStart}0825000000`;
  const calEnd = `${studyYearStart + 1}0620000000`;

  const [sem1Html, sem2Html, calJsonStr, messagesHtml] = await Promise.all([
    sem1Ajax
      ? callApexAjax(jar, mCtx, {
          p_flow_id: "104",
          p_flow_step_id: "1",
          p_instance: mInst,
          p_debug: "",
          p_request: `PLUGIN=${sem1Ajax}`,
          p_widget_action: "reset",
          x01: "44763502292407793",
          p_json: reportPayload
        })
      : Promise.resolve(""),
    sem2Ajax
      ? callApexAjax(jar, mCtx, {
          p_flow_id: "104",
          p_flow_step_id: "1",
          p_instance: mInst,
          p_debug: "",
          p_request: `PLUGIN=${sem2Ajax}`,
          p_widget_action: "reset",
          x01: "50916749730695550",
          p_json: reportPayload
        })
      : Promise.resolve(""),
    calAjax
      ? callApexAjax(jar, mCtx, {
          p_flow_id: "104",
          p_flow_step_id: "1",
          p_instance: mInst,
          p_debug: "",
          p_request: `PLUGIN=${calAjax}`,
          x01: "GET",
          x02: calStart,
          x03: calEnd,
          p_json: JSON.stringify({ salt: mSalt })
        })
      : Promise.resolve("[]"),
    eleveId
      ? callApexAjax(jar, mCtx, {
          p_flow_id: "104",
          p_flow_step_id: "1",
          p_instance: mInst,
          p_debug: "",
          p_request: "APPLICATION_PROCESS=get_messages",
          x01: eleveId,
          p_json: JSON.stringify({ salt: mSalt })
        })
      : Promise.resolve("")
  ]);

  const snapshot = parseMainPageData(mc, sem1Html, sem2Html, calJsonStr, messagesHtml);
  return {
    snapshot,
    sessionToken: {
      cookie: cookieHeaderFromJar(jar),
      mainPageUrl: mainRes.finalUrl,
      sessionId: snapshot.sessionId
    }
  };
}

async function handleSync(body) {
  const { username, password, userType = 2, sessionToken } = body;
  const jar = jarFromCookieString(sessionToken?.cookie);
  let mainRes = null;

  if (sessionToken?.cookie && sessionToken?.mainPageUrl) {
    try {
      const probe = await fetchWithCookies(jar, sessionToken.mainPageUrl);
      if (probe.finalUrl.includes("/main-page") && probe.text.includes("RegBadge")) {
        mainRes = probe;
      }
    } catch {}
  }

  if (!mainRes) {
    if (!username || !password) {
      const err = new Error("Sesiunea a expirat. Te rugăm să te autentifici din nou.");
      err.statusCode = 401;
      throw err;
    }
    mainRes = await performApexLogin(jar, username, password, userType);
  }

  return fetchFullPortalFromMainPage(jar, mainRes);
}

async function handleSwitchStudent(body) {
  const { switchUrl, username, password, userType = 2, sessionToken } = body;
  if (!switchUrl) throw new Error("Lipsește switchUrl.");
  const jar = jarFromCookieString(sessionToken?.cookie);

  let mainRes = null;
  if (sessionToken?.cookie) {
    const res = await fetchWithCookies(jar, switchUrl);
    if (res.finalUrl.includes("/main-page") && res.text.includes("RegBadge")) {
      mainRes = res;
    }
  }
  if (!mainRes && username && password) {
    await performApexLogin(jar, username, password, userType);
    const res = await fetchWithCookies(jar, switchUrl);
    if (res.finalUrl.includes("/main-page") && res.text.includes("RegBadge")) {
      mainRes = res;
    }
  }
  if (!mainRes) {
    const err = new Error("Sesiunea a expirat.");
    err.statusCode = 401;
    throw err;
  }
  return fetchFullPortalFromMainPage(jar, mainRes);
}

async function handleLessonDetail(body) {
  const { detailUrl, dateIso, periodNumber, subject, username, password, userType = 2, sessionToken } = body;
  let jar = jarFromCookieString(sessionToken?.cookie);
  let targetUrl = detailUrl;

  if (targetUrl && jar.size > 0) {
    const res = await fetchWithCookies(jar, targetUrl);
    if (res.resp.status === 200 && res.text.includes("lesson-view") && !res.text.includes("P101_USERNAME")) {
      return {
        detail: parseLessonDetailHtml(res.text),
        sessionToken: {
          ...sessionToken,
          cookie: cookieHeaderFromJar(jar)
        }
      };
    }
  }

  if (!username || !password) {
    const err = new Error("Sesiunea SIME a expirat. Apasă actualizare pentru reconectare.");
    err.statusCode = 401;
    throw err;
  }

  jar = new Map();
  const mainRes = await performApexLogin(jar, username, password, userType);
  const full = await fetchFullPortalFromMainPage(jar, mainRes);
  const normTarget = normalizeSubject(subject);
  const match =
    full.snapshot.scheduleEvents.find(
      (ev) => ev.dateIso === dateIso && ev.periodNumber === Number(periodNumber) && normalizeSubject(ev.subject) === normTarget
    ) ||
    full.snapshot.scheduleEvents.find(
      (ev) => ev.dateIso === dateIso && normalizeSubject(ev.subject) === normTarget
    );

  if (!match || !match.detailUrl) {
    throw new Error("Această lecție nu are pagină de detalii disponibilă.");
  }

  const detailRes = await fetchWithCookies(jar, match.detailUrl);
  return {
    detail: parseLessonDetailHtml(detailRes.text),
    sessionToken: full.sessionToken
  };
}

module.exports = async function handler(req, res) {
  res.setHeader("Cache-Control", "no-store, max-age=0");
  if (req.method === "OPTIONS") {
    return res.status(204).end();
  }
  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method not allowed" });
  }

  try {
    const body = typeof req.body === "string" ? JSON.parse(req.body || "{}") : req.body || {};
    const action = body.action || "sync";
    let result;
    if (action === "sync") {
      result = await handleSync(body);
    } else if (action === "lessonDetail") {
      result = await handleLessonDetail(body);
    } else if (action === "switchStudent") {
      result = await handleSwitchStudent(body);
    } else {
      return res.status(400).json({ error: `Unknown action: ${action}` });
    }
    return res.status(200).json(result);
  } catch (err) {
    const status = err.statusCode || 500;
    return res.status(status).json({ error: err.message || "Eroare internă de server." });
  }
};
