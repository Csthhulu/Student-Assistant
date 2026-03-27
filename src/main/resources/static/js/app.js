(() => {
  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

  const els = {
    statusPill: $("#status-pill"),
    tabs: $$("nav.tabs button"),
    panels: $$(".panel"),
    btnRefresh: $("#btn-refresh"),
    btnLoadDash: $("#btn-load-dashboard"),
    coachBody: $("#coach-body"),
    assignments: $("#assignments"),
    habitsHint: $("#habits-hint"),
    habitsTable: $("#habits-table-body"),
    habitsMeta: $("#habits-meta"),
    dashError: $("#dash-error"),
    chatLog: $("#chat-log"),
    chatForm: $("#chat-form"),
    chatInput: $("#chat-input"),
    includeCanvas: $("#include-canvas"),
    habitForm: $("#habit-form"),
    habitAssignment: $("#habit-assignment"),
    habitEvent: $("#habit-event"),
    habitDue: $("#habit-due"),
    habitMsg: $("#habit-msg"),
  };

  let chatMessages = [];
  let lastAssignments = [];

  async function fetchJson(url, options = {}) {
    const res = await fetch(url, {
      ...options,
      headers: {
        Accept: "application/json",
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...options.headers,
      },
    });
    const text = await res.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = { raw: text };
    }
    if (!res.ok) {
      const msg =
        data?.detail || data?.message || data?.raw || res.statusText || "Request failed";
      throw new Error(typeof msg === "string" ? msg : JSON.stringify(msg));
    }
    return data;
  }

  function setStatus(ok, canvasConfigured) {
    els.statusPill.classList.remove("ok", "warn");
    if (ok) {
      els.statusPill.classList.add("ok");
      els.statusPill.querySelector("span:last-child").textContent = canvasConfigured
        ? "API up · Canvas linked"
        : "API up · add Canvas env for coursework";
    } else {
      els.statusPill.classList.add("warn");
      els.statusPill.querySelector("span:last-child").textContent = "API unreachable";
    }
  }

  async function checkHealth() {
    try {
      const h = await fetchJson("/health");
      setStatus(!!h?.ok, !!h?.canvas_configured);
    } catch {
      setStatus(false, false);
    }
  }

  function fmtDue(iso) {
    if (!iso) return "No due date";
    try {
      return new Date(iso).toLocaleString(undefined, {
        weekday: "short",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return iso;
    }
  }

  function renderAssignments(list) {
    if (!list || list.length === 0) {
      els.assignments.innerHTML =
        '<div class="empty-state">No upcoming assignments (or Canvas not configured).</div>';
      return;
    }
    els.assignments.innerHTML = list
      .map((a) => {
        const score = typeof a.habit_score === "number" ? a.habit_score : 0;
        const pct = Math.round(score * 100);
        const link = a.html_url
          ? `<a class="canvas-link" href="${a.html_url}" target="_blank" rel="noopener">Open in Canvas</a>`
          : "";
        return `<article class="assignment-card">
          <div class="course">${escapeHtml(a.course_name || "Course")}</div>
          <div class="title">${escapeHtml(a.name || "Assignment")}</div>
          <div class="meta">
            <span>${escapeHtml(fmtDue(a.due_at))}</span>
            ${
              a.points_possible != null
                ? `<span>${escapeHtml(String(a.points_possible))} pts</span>`
                : ""
            }
            <span>Focus ${pct}%</span>
          </div>
          <div class="score-bar" title="Habit-adjusted urgency"><span style="width:${pct}%"></span></div>
          ${link}
        </article>`;
      })
      .join("");
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function renderHabits(summary) {
    if (!summary) return;
    const hint = summary.hint || "";
    els.habitsHint.textContent = hint;
    els.habitsMeta.textContent = `Logged events: ${summary.total_events ?? 0}`;
    const rows = summary.tracked_courses || [];
    if (rows.length === 0) {
      els.habitsTable.innerHTML =
        '<tr><td colspan="4" class="empty-state" style="border:none">No per-course habits yet. Submit assignments with due dates to learn your rhythm.</td></tr>';
      return;
    }
    els.habitsTable.innerHTML = rows
      .map(
        (r) => `<tr>
        <td>${escapeHtml(String(r.course_id))}</td>
        <td>${r.avg_days_before_due != null ? Number(r.avg_days_before_due).toFixed(2) : "—"}</td>
        <td>${escapeHtml(String(r.n_samples ?? ""))}</td>
        <td>${escapeHtml(String(r.snooze_count ?? ""))}</td>
      </tr>`
      )
      .join("");
  }

  function populateHabitAssignments() {
    const sel = els.habitAssignment;
    const cur = sel.value;
    sel.innerHTML = '<option value="">Select assignment…</option>';
    lastAssignments.forEach((a) => {
      const opt = document.createElement("option");
      opt.value = a.id;
      opt.textContent = `${a.course_name || "?"} — ${a.name || a.id}`;
      sel.appendChild(opt);
    });
    if ([...sel.options].some((o) => o.value === cur)) sel.value = cur;
  }

  async function loadDashboard() {
    els.dashError.hidden = true;
    els.dashError.textContent = "";
    try {
      const d = await fetchJson("/dashboard");
      els.coachBody.textContent = d.coach_message || "";
      lastAssignments = d.assignments || [];
      renderAssignments(lastAssignments);
      renderHabits(d.habit_summary);
      populateHabitAssignments();
    } catch (e) {
      els.dashError.hidden = false;
      els.dashError.textContent = e.message || String(e);
    }
  }

  function switchTab(name) {
    els.tabs.forEach((b) => b.classList.toggle("active", b.dataset.tab === name));
    els.panels.forEach((p) => p.classList.toggle("active", p.id === `panel-${name}`));
  }

  els.tabs.forEach((b) => {
    b.addEventListener("click", () => switchTab(b.dataset.tab));
  });

  els.btnRefresh?.addEventListener("click", checkHealth);
  els.btnLoadDash?.addEventListener("click", loadDashboard);

  els.chatForm?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const text = (els.chatInput.value || "").trim();
    if (!text) return;
    els.chatInput.value = "";
    chatMessages.push({ role: "user", content: text });
    appendChatBubble("user", text);
    appendChatBubble("assistant", "…");
    const pending = els.chatLog.lastElementChild;
    try {
      const body = {
        messages: chatMessages,
        include_canvas: els.includeCanvas.checked,
      };
      const out = await fetchJson("/chat", { method: "POST", body: JSON.stringify(body) });
      const reply = out?.reply || "(empty reply)";
      pending.textContent = reply;
      chatMessages.push({ role: "assistant", content: reply });
    } catch (e) {
      pending.textContent = `Error: ${e.message}`;
    }
    els.chatLog.scrollTop = els.chatLog.scrollHeight;
  });

  function appendChatBubble(role, content) {
    const div = document.createElement("div");
    div.className = `msg ${role}`;
    div.textContent = content;
    els.chatLog.appendChild(div);
    els.chatLog.scrollTop = els.chatLog.scrollHeight;
  }

  els.habitForm?.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    els.habitMsg.textContent = "";
    const id = els.habitAssignment.value;
    const event = els.habitEvent.value;
    if (!id) {
      els.habitMsg.textContent = "Assign an assignment.";
      return;
    }
    const a = lastAssignments.find((x) => x.id === id);
    let due = null;
    if (event === "submitted" && els.habitDue.value) {
      due = new Date(els.habitDue.value).toISOString();
    } else if (event === "submitted" && a?.due_at) {
      due = a.due_at;
    }
    const body = {
      assignment_id: id,
      course_id: a?.course_id ?? null,
      event,
      due_at: due,
      metadata: {},
    };
    try {
      await fetchJson("/habits/event", { method: "POST", body: JSON.stringify(body) });
      els.habitMsg.textContent = "Logged.";
      await loadDashboard();
    } catch (e) {
      els.habitMsg.textContent = e.message;
    }
  });

  checkHealth();
  loadDashboard();
  setInterval(checkHealth, 60000);
})();
