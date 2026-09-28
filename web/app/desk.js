"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { getSupabase } from "../lib/supabase";
import TvPreview from "./preview";
import { CADENCES, DAYS, ICONS, LOOKS, SHOW_WHEN, cadenceText, dayKey, lookOf, occurs } from "../lib/board";

const VIEWS = [
  ["dashboard", "Dashboard"],
  ["users", "Users"],
  ["tasks", "Tasks"],
  ["schedule", "Schedule"],
  ["awards", "Awards"],
  ["settings", "Settings"],
];

let sequence = 0;

function tempId() {
  sequence += 1;
  return `new-${sequence}`;
}

function isNew(id) {
  return typeof id === "string" && id.startsWith("new-");
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function mapped(idMap, id) {
  if (!id) return null;
  return idMap[id] || id;
}

function taskPayload(row, index, idMap) {
  return {
    user_id: mapped(idMap, row.user_id),
    label: row.label.trim(),
    detail: (row.detail || "").trim(),
    icon: row.icon || "star",
    sort_order: index + 1,
    cadence: row.cadence,
    weekdays: row.cadence === "weekly" ? row.weekdays || [] : [],
    every_n_days: row.cadence === "periodic" ? Number(row.every_n_days) || 1 : null,
    anchor_date: row.cadence === "periodic" ? row.anchor_date || null : null,
    on_date: row.cadence === "once" ? row.on_date || null : null,
    goal_count: Number(row.goal_count) || 1,
    active: true,
  };
}

function itemPayload(row, index, idMap) {
  return {
    user_id: mapped(idMap, row.user_id),
    title: row.title.trim(),
    time_label: (row.time_label || "").trim(),
    sort_order: index + 1,
    task_id: mapped(idMap, row.task_id),
    cadence: row.cadence,
    weekdays: row.cadence === "weekly" ? row.weekdays || [] : [],
    every_n_days: row.cadence === "periodic" ? Number(row.every_n_days) || 1 : null,
    anchor_date: row.cadence === "periodic" ? row.anchor_date || null : null,
    on_date: row.cadence === "once" ? row.on_date || null : null,
    active: true,
  };
}

function awardPayload(row, index, idMap) {
  return {
    user_id: mapped(idMap, row.user_id),
    title: (row.title || "").trim(),
    message: row.message.trim(),
    minutes: Number(row.minutes) || null,
    icon: row.icon || "star",
    show_when: row.show_when,
    min_done: row.show_when === "min_done" ? Number(row.min_done) || 1 : null,
    sort_order: index + 1,
    active: true,
  };
}

function userPayload(row, index) {
  return { name: row.name.trim(), sort_order: index + 1, active: true };
}

const blankTask = {
  label: "",
  detail: "",
  icon: "star",
  cadence: "daily",
  weekdays: [],
  every_n_days: 2,
  anchor_date: "",
  on_date: "",
  goal_count: 1,
};

const blankItem = {
  title: "",
  time_label: "",
  task_id: "",
  cadence: "once",
  weekdays: [],
  every_n_days: 2,
  anchor_date: "",
  on_date: "",
};

const blankAward = {
  title: "TV time",
  message: "",
  minutes: 60,
  icon: "star",
  show_when: "all_done",
  min_done: 1,
};

export default function Desk() {
  const supabase = useMemo(() => getSupabase(), []);
  const [view, setView] = useState("dashboard");
  const [server, setServer] = useState(null);
  const [draft, setDraft] = useState(null);
  const [counts, setCounts] = useState({});
  const [userId, setUserId] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(null);

  useEffect(() => {
    setNow(new Date());
    const timer = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  const load = useCallback(async () => {
    if (!supabase) return;
    const today = dayKey(new Date());
    const [settingsRes, userRes, taskRes, itemRes, awardRes, logRes] = await Promise.all([
      supabase.from("tvdesk_settings").select("*").eq("id", 1).maybeSingle(),
      supabase.from("tvdesk_users").select("*").eq("active", true).order("sort_order"),
      supabase.from("tvdesk_tasks").select("*").eq("active", true).order("sort_order"),
      supabase.from("tvdesk_schedule_items").select("*").eq("active", true).order("sort_order"),
      supabase.from("tvdesk_awards").select("*").eq("active", true).order("sort_order"),
      supabase.from("tvdesk_task_logs").select("task_id,count").eq("day", today),
    ]);
    const failed = settingsRes.error || userRes.error || taskRes.error || itemRes.error || awardRes.error || logRes.error;
    if (failed) {
      setError(`${failed.message} — run supabase/schema.sql in the Supabase SQL editor first.`);
      return;
    }
    const next = {
      settings: settingsRes.data || { id: 1, theme: "cards", city: "Bothell", countdown_label: "Weekend", countdown_date: null, active_user_id: null, meta: {} },
      users: userRes.data || [],
      tasks: taskRes.data || [],
      items: itemRes.data || [],
      awards: awardRes.data || [],
    };
    const tally = {};
    (logRes.data || []).forEach((row) => {
      tally[row.task_id] = row.count;
    });
    setError("");
    setCounts(tally);
    setServer(next);
    setDraft(clone(next));
    setUserId((current) => {
      const ids = next.users.map((row) => row.id);
      if (current && ids.includes(current)) return current;
      if (next.settings.active_user_id && ids.includes(next.settings.active_user_id)) return next.settings.active_user_id;
      return ids[0] || "";
    });
  }, [supabase]);

  useEffect(() => {
    load();
  }, [load]);

  if (!supabase) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">TV DESK</p>
          <h1>Connect Supabase</h1>
          <p className="lede">Add NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY, then reload.</p>
        </section>
      </main>
    );
  }

  const dirty = Boolean(server && draft) && JSON.stringify(server) !== JSON.stringify(draft);
  const today = now ? dayKey(now) : "";
  const look = draft ? lookOf(draft.settings) : "navy";

  function patch(part) {
    setDraft((current) => ({ ...current, ...part }));
    setNotice("");
  }

  function patchSettings(part) {
    setDraft((current) => ({ ...current, settings: { ...current.settings, ...part } }));
    setNotice("");
  }

  function patchRow(key, id, part) {
    setDraft((current) => ({
      ...current,
      [key]: current[key].map((row) => (row.id === id ? { ...row, ...part } : row)),
    }));
    setNotice("");
  }

  function removeRow(key, id) {
    setDraft((current) => ({ ...current, [key]: current[key].filter((row) => row.id !== id) }));
    setNotice("");
  }

  function addRow(key, row) {
    setDraft((current) => ({ ...current, [key]: current[key].concat({ ...row, id: tempId() }) }));
    setNotice("");
  }

  function discard() {
    setDraft(clone(server));
    setError("");
    setNotice("Changes discarded.");
  }

  async function pushRows(table, draftRows, serverRows, payloadOf, idMap) {
    const kept = new Set(draftRows.map((row) => row.id));
    const gone = serverRows.filter((row) => !kept.has(row.id)).map((row) => row.id);
    if (gone.length > 0) {
      const { error: deleteError } = await supabase.from(table).delete().in("id", gone);
      if (deleteError) throw deleteError;
    }
    for (let index = 0; index < draftRows.length; index += 1) {
      const row = draftRows[index];
      const body = payloadOf(row, index, idMap);
      if (isNew(row.id)) {
        const { data, error: insertError } = await supabase.from(table).insert(body).select("id").single();
        if (insertError) throw insertError;
        idMap[row.id] = data.id;
        continue;
      }
      const before = serverRows.find((other) => other.id === row.id);
      if (before && JSON.stringify(payloadOf(before, index, idMap)) === JSON.stringify(body)) continue;
      const { error: updateError } = await supabase.from(table).update(body).eq("id", row.id);
      if (updateError) throw updateError;
    }
  }

  async function save() {
    const blankUser = draft.users.some((row) => !row.name.trim());
    const blankLabel = draft.tasks.some((row) => !row.label.trim());
    const blankTitle = draft.items.some((row) => !row.title.trim());
    const blankMessage = draft.awards.some((row) => !row.message.trim());
    if (blankUser) return setError("Every person needs a name.");
    if (blankLabel) return setError("Every task needs a name.");
    if (blankTitle) return setError("Every schedule line needs a title.");
    if (blankMessage) return setError("Every award needs a message.");

    setBusy(true);
    setError("");
    setNotice("");
    const idMap = {};
    try {
      await pushRows("tvdesk_users", draft.users, server.users, userPayload, idMap);
      await pushRows("tvdesk_tasks", draft.tasks, server.tasks, taskPayload, idMap);
      await pushRows("tvdesk_schedule_items", draft.items, server.items, itemPayload, idMap);
      await pushRows("tvdesk_awards", draft.awards, server.awards, awardPayload, idMap);
      const settingsBody = {
        theme: "cards",
        city: (draft.settings.city || "Bothell").trim() || "Bothell",
        countdown_date: draft.settings.countdown_date || null,
        active_user_id: mapped(idMap, draft.settings.active_user_id),
        meta: { ...(draft.settings.meta || {}), look: lookOf(draft.settings) },
      };
      const { error: settingsError } = await supabase.from("tvdesk_settings").upsert({ id: 1, ...settingsBody });
      if (settingsError) throw settingsError;
      if (idMap[userId]) setUserId(idMap[userId]);
      await load();
      setNotice("Saved. The TV shows this within 15 seconds — no new APK needed.");
    } catch (thrown) {
      setError(`${thrown.message || thrown} — nothing after that point was saved.`);
      await load();
    } finally {
      setBusy(false);
    }
  }

  async function toggleToday(item) {
    if (!item || isNew(item.id) || !today) return;
    const meta = { ...(item.meta || {}) };
    const days = Array.isArray(meta.done_days) ? meta.done_days.filter(Boolean) : [];
    const nextDays = days.includes(today) ? days.filter((day) => day !== today) : days.concat(today);
    const nextMeta = { ...meta, done_days: nextDays };
    const { error: writeError } = await supabase.from("tvdesk_schedule_items").update({ meta: nextMeta }).eq("id", item.id);
    if (writeError) {
      setError(writeError.message);
      return;
    }
    const stamp = (rows) => rows.map((row) => (row.id === item.id ? { ...row, meta: nextMeta } : row));
    setServer((current) => (current ? { ...current, items: stamp(current.items) } : current));
    setDraft((current) => (current ? { ...current, items: stamp(current.items) } : current));
  }

  if (!draft) {
    return (
      <main className="shell">
        <section className="panel">
          <p className="kicker">TV DESK</p>
          <h1>Loading the board…</h1>
          {error ? <p className="error">{error}</p> : null}
        </section>
      </main>
    );
  }

  const myTasks = draft.tasks.filter((row) => row.user_id === userId);
  const myItems = draft.items.filter((row) => row.user_id === userId);
  const person = draft.users.find((row) => row.id === userId) || null;
  const unsavedPerson = person ? isNew(person.id) : false;

  return (
    <div className="app">
      <header className="topbar">
        <div>
          <p className="kicker">TV DESK</p>
          <h1>Board control</h1>
        </div>
        <div className="savebar">
          <span className={dirty ? "state dirty" : "state clean"}>
            {dirty ? "Unsaved changes — the TV still shows the last saved board" : "Everything is saved and live on the TV"}
          </span>
          <button type="button" onClick={save} disabled={!dirty || busy}>
            {busy ? "Saving…" : "Save changes"}
          </button>
          <button type="button" className="ghost" onClick={discard} disabled={!dirty || busy}>
            Discard
          </button>
        </div>
      </header>

      <nav className="menu">
        {VIEWS.map(([value, label]) => (
          <button key={value} type="button" className={value === view ? "tab on" : "tab"} onClick={() => setView(value)}>
            {label}
          </button>
        ))}
      </nav>

      {error ? <p className="error">{error}</p> : null}
      {notice ? <p className="notice">{notice}</p> : null}

      {view === "dashboard" || view === "tasks" || view === "schedule" ? (
        <div className="people">
          <span className="field-label">Person</span>
          <div className="inline">
            {draft.users.map((row) => (
              <button
                key={row.id}
                type="button"
                className={row.id === userId ? "chip on" : "chip"}
                onClick={() => setUserId(row.id)}
              >
                {row.name || "Unnamed"}
              </button>
            ))}
          </div>
          <p className="field-hint">
            {view === "dashboard"
              ? "The preview below shows this person's board. Change who the TV starts on in Settings."
              : "You are editing this person only. Everyone else keeps their own tasks and schedule."}
          </p>
        </div>
      ) : null}

      {view === "dashboard" ? (
        <section className="panel">
          <h2>What the TV shows</h2>
          <p className="sub">
            This is the same board the TV paints, drawn from your current edits. Save when you are happy with it and the
            television picks it up on its next check.
          </p>
          <div className="themepick">
            <span className="field-label">Background</span>
            <div className="inline">
              {LOOKS.map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  className={value === look ? "chip on" : "chip"}
                  onClick={() => patchSettings({ theme: "cards", meta: { ...(draft.settings.meta || {}), look: value } })}
                >
                  {label}
                </button>
              ))}
            </div>
            <p className="field-hint">
              Navy, Warm paper, Daylight, Meadow, or Sunset. The preview changes now. Save to send it to the TV. The look button on the remote cycles the same five.
            </p>
          </div>
          <div className="tv-frame">
            {now ? (
              <TvPreview
                board={{ ...draft, doneCounts: counts }}
                userId={userId}
                today={today}
                now={now}
                onToggleToday={toggleToday}
              />
            ) : null}
          </div>
          <p className="sub">
            Click a line in Today to mark it done. That saves immediately, like the remote. Task cards stay smaller so Today and Tomorrow have more room. Temperature is blank here because only the TV holds the weather key.
          </p>
        </section>
      ) : null}

      {view === "users" ? (
        <section className="panel">
          <h2>Users</h2>
          <p className="sub">
            Each person gets their own tasks, schedule, and awards. The TV shows one person at a time and the remote can
            switch between them.
          </p>
          {draft.users.map((row) => (
            <div className="block" key={row.id}>
              <div className="row">
                <Field label="Name" hint="Shown on the TV as TV DESK · NAME.">
                  <input type="text" value={row.name} onChange={(event) => patchRow("users", row.id, { name: event.target.value })} />
                </Field>
                <div className="rowend">
                  <button
                    type="button"
                    className="ghost danger"
                    onClick={() => {
                      removeRow("users", row.id);
                      setDraft((current) => ({
                        ...current,
                        tasks: current.tasks.filter((task) => task.user_id !== row.id),
                        items: current.items.filter((item) => item.user_id !== row.id),
                        awards: current.awards.filter((award) => award.user_id !== row.id),
                        settings: {
                          ...current.settings,
                          active_user_id: current.settings.active_user_id === row.id ? null : current.settings.active_user_id,
                        },
                      }));
                      if (userId === row.id) setUserId("");
                    }}
                  >
                    Delete person
                  </button>
                </div>
              </div>
              <p className="field-hint">
                Deleting removes their tasks, schedule lines, and personal awards when you save.
              </p>
            </div>
          ))}
          <button type="button" className="ghost" onClick={() => addRow("users", { name: "" })}>
            Add person
          </button>
        </section>
      ) : null}

      {view === "tasks" ? (
        <section className="panel">
          <h2>Tasks</h2>
          <p className="sub">
            Tasks are the big cards on the TV. A task counts for today only if its repeat rule includes today, and it is
            done once it has been marked as many times as the goal.
          </p>
          {!person ? <p className="field-hint">Add a person on the Users page first.</p> : null}
          {person && myTasks.length === 0 ? <p className="field-hint">{person.name || "This person"} has no tasks yet.</p> : null}
          {myTasks.map((row) => (
            <div className="block" key={row.id}>
              <div className="blockhead">
                <strong>{row.label || "New task"}</strong>
                <span className="field-hint">
                  {cadenceText(row)}
                  {today && occurs(row, today) ? " · due today" : " · not due today"}
                </span>
              </div>
              <div className="grid">
                <Field label="Task name" hint="The heading on the card, for example Maths.">
                  <input type="text" value={row.label} onChange={(event) => patchRow("tasks", row.id, { label: event.target.value })} />
                </Field>
                <Field label="Detail line" hint="Small grey line under the name, for example Chapter 6.">
                  <input type="text" value={row.detail || ""} onChange={(event) => patchRow("tasks", row.id, { detail: event.target.value })} />
                </Field>
                <Field label="Picture" hint="Drawing shown in the corner of the card.">
                  <select value={row.icon || "star"} onChange={(event) => patchRow("tasks", row.id, { icon: event.target.value })}>
                    {ICONS.map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </Field>
                <Field label="Marks needed" hint="How many times it must be marked on the TV before it counts as done.">
                  <input
                    type="number"
                    min="1"
                    value={row.goal_count || 1}
                    onChange={(event) => patchRow("tasks", row.id, { goal_count: event.target.value })}
                  />
                </Field>
              </div>
              <Cadence row={row} onChange={(part) => patchRow("tasks", row.id, part)} />
              <button type="button" className="ghost danger" onClick={() => removeRow("tasks", row.id)}>
                Delete task
              </button>
            </div>
          ))}
          {person ? (
            <button
              type="button"
              className="ghost"
              disabled={unsavedPerson}
              onClick={() => addRow("tasks", { ...blankTask, user_id: userId })}
            >
              Add task for {person.name || "this person"}
            </button>
          ) : null}
          {unsavedPerson ? <p className="field-hint">Save the new person first, then add their tasks.</p> : null}
        </section>
      ) : null}

      {view === "schedule" ? (
        <section className="panel">
          <h2>Schedule</h2>
          <p className="sub">
            These lines fill the Today and Tomorrow boxes on the TV. The repeat rule decides which box a line lands in.
          </p>
          {!person ? <p className="field-hint">Add a person on the Users page first.</p> : null}
          {person && myItems.length === 0 ? <p className="field-hint">{person.name || "This person"} has no schedule lines yet.</p> : null}
          {myItems.map((row) => (
            <div className="block" key={row.id}>
              <div className="blockhead">
                <strong>{row.title || "New line"}</strong>
                <span className="field-hint">{cadenceText(row)}</span>
              </div>
              <div className="grid">
                <Field label="Time" hint="Free text shown on the left, for example 9:00 AM.">
                  <input type="text" value={row.time_label || ""} onChange={(event) => patchRow("items", row.id, { time_label: event.target.value })} />
                </Field>
                <Field label="What happens" hint="The line the TV prints, for example SAT practice test.">
                  <input type="text" value={row.title} onChange={(event) => patchRow("items", row.id, { title: event.target.value })} />
                </Field>
                <Field label="Linked task" hint="Optional. Ties this line to one of the task cards for your own reference.">
                  <select value={row.task_id || ""} onChange={(event) => patchRow("items", row.id, { task_id: event.target.value })}>
                    <option value="">Not linked</option>
                    {myTasks.map((task) => (
                      <option key={task.id} value={task.id}>{task.label || "Unnamed task"}</option>
                    ))}
                  </select>
                </Field>
              </div>
              <Cadence row={row} onChange={(part) => patchRow("items", row.id, part)} />
              <button type="button" className="ghost danger" onClick={() => removeRow("items", row.id)}>
                Delete line
              </button>
            </div>
          ))}
          {person ? (
            <button
              type="button"
              className="ghost"
              disabled={unsavedPerson}
              onClick={() => addRow("items", { ...blankItem, user_id: userId, on_date: today })}
            >
              Add schedule line for {person.name || "this person"}
            </button>
          ) : null}
        </section>
      ) : null}

      {view === "awards" ? (
        <section className="panel">
          <h2>Awards</h2>
          <p className="sub">
            When every task due today is marked done, the TV hides the task cards and shows the awards instead. The
            remote can switch back to the tasks.
          </p>
          {draft.awards.map((row) => (
            <div className="block" key={row.id}>
              <div className="blockhead">
                <strong>{row.title || "Award"}</strong>
                <span className="field-hint">{row.user_id ? "One person" : "Everyone"}</span>
              </div>
              <div className="grid">
                <Field label="Title" hint="Small heading on the award card, for example TV time.">
                  <input type="text" value={row.title || ""} onChange={(event) => patchRow("awards", row.id, { title: event.target.value })} />
                </Field>
                <Field label="Message" hint="The exact sentence the TV prints, for example You have been awarded with 60 mins TV time today.">
                  <input type="text" value={row.message} onChange={(event) => patchRow("awards", row.id, { message: event.target.value })} />
                </Field>
                <Field label="Minutes" hint="Kept with the award so you can report on it later.">
                  <input type="number" min="1" value={row.minutes || ""} onChange={(event) => patchRow("awards", row.id, { minutes: event.target.value })} />
                </Field>
                <Field label="Who earns it" hint="Everyone means any person who finishes their own tasks.">
                  <select value={row.user_id || ""} onChange={(event) => patchRow("awards", row.id, { user_id: event.target.value || null })}>
                    <option value="">Everyone</option>
                    {draft.users.map((user) => (
                      <option key={user.id} value={user.id}>{user.name || "Unnamed"}</option>
                    ))}
                  </select>
                </Field>
                <Field label="When it appears" hint="Awards only show after the whole day is finished; this narrows it further.">
                  <select value={row.show_when} onChange={(event) => patchRow("awards", row.id, { show_when: event.target.value })}>
                    {SHOW_WHEN.map(([value, label]) => (
                      <option key={value} value={value}>{label}</option>
                    ))}
                  </select>
                </Field>
                {row.show_when === "min_done" ? (
                  <Field label="Tasks needed" hint="Hide this award unless at least this many tasks were done today.">
                    <input type="number" min="1" value={row.min_done || 1} onChange={(event) => patchRow("awards", row.id, { min_done: event.target.value })} />
                  </Field>
                ) : null}
              </div>
              <button type="button" className="ghost danger" onClick={() => removeRow("awards", row.id)}>
                Delete award
              </button>
            </div>
          ))}
          <button type="button" className="ghost" onClick={() => addRow("awards", { ...blankAward, user_id: null })}>
            Add award
          </button>
        </section>
      ) : null}

      {view === "settings" ? (
        <section className="panel">
          <h2>Settings</h2>
          <p className="sub">One set of settings covers the whole TV, whoever is showing.</p>
          <div className="grid">
            <Field label="Weather city" hint="Used for the temperature pill on the TV, for example Bothell.">
              <input type="text" value={draft.settings.city || ""} onChange={(event) => patchSettings({ city: event.target.value })} />
            </Field>
            <Field label="Countdown date" hint="Leave blank and the TV counts to the coming Saturday. The pill always reads Days for Weekend.">
              <input type="date" value={draft.settings.countdown_date || ""} onChange={(event) => patchSettings({ countdown_date: event.target.value || null })} />
            </Field>
            <Field label="Background" hint="Navy is the dark board. Warm paper, Daylight, Meadow, and Sunset are lighter.">
              <div className="inline">
                {LOOKS.map(([value, label, hint]) => (
                  <button
                    key={value}
                    type="button"
                    className={value === look ? "chip on" : "chip"}
                    onClick={() => patchSettings({ theme: "cards", meta: { ...(draft.settings.meta || {}), look: value } })}
                    title={hint}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </Field>
            <Field label="Person the TV starts on" hint="The remote can switch to anyone else afterwards.">
              <select value={draft.settings.active_user_id || ""} onChange={(event) => patchSettings({ active_user_id: event.target.value || null })}>
                <option value="">Whoever is first</option>
                {draft.users.map((user) => (
                  <option key={user.id} value={user.id}>{user.name || "Unnamed"}</option>
                ))}
              </select>
            </Field>
          </div>
          <p className="sub">
            Saving here is all that is needed. The APK only has to be rebuilt if the Supabase keys change, not when you
            edit the board.
          </p>
        </section>
      ) : null}
    </div>
  );
}

function Field({ label, hint, children }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  );
}

function Cadence({ row, onChange }) {
  function toggleDay(day) {
    const current = row.weekdays || [];
    const weekdays = current.includes(day) ? current.filter((value) => value !== day) : current.concat(day).sort();
    onChange({ weekdays });
  }
  return (
    <div className="grid">
      <Field label="Repeat" hint="Every day, only on chosen weekdays, every few days, or a single date.">
        <select value={row.cadence} onChange={(event) => onChange({ cadence: event.target.value })}>
          {CADENCES.map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
      </Field>
      {row.cadence === "weekly" ? (
        <Field label="Which weekdays" hint="Tick every day it should appear.">
          <span className="days">
            {DAYS.map((label, index) => (
              <label key={label} className="day">
                <input type="checkbox" checked={(row.weekdays || []).includes(index)} onChange={() => toggleDay(index)} />
                {label}
              </label>
            ))}
          </span>
        </Field>
      ) : null}
      {row.cadence === "periodic" ? (
        <>
          <Field label="Every how many days" hint="2 means every other day.">
            <input type="number" min="1" value={row.every_n_days || 1} onChange={(event) => onChange({ every_n_days: event.target.value })} />
          </Field>
          <Field label="Counting from" hint="The first day it appears; the count runs forward from here.">
            <input type="date" value={row.anchor_date || ""} onChange={(event) => onChange({ anchor_date: event.target.value })} />
          </Field>
        </>
      ) : null}
      {row.cadence === "once" ? (
        <Field label="On this date" hint="Appears on this one day only.">
          <input type="date" value={row.on_date || ""} onChange={(event) => onChange({ on_date: event.target.value })} />
        </Field>
      ) : null}
    </div>
  );
}
