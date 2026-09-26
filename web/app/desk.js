"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { getSupabase } from "../lib/supabase";

const ICONS = ["document", "math", "game", "art", "book", "star"];
const CADENCES = [
  ["daily", "Every day"],
  ["weekly", "Weekly"],
  ["periodic", "Every N days"],
  ["once", "One date"],
];
const DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const THEMES = [
  ["cards", "Cards"],
  ["classic", "Classic"],
  ["agenda", "Agenda"],
  ["night", "Night"],
];

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
  cadence: "once",
  weekdays: [],
  every_n_days: 2,
  anchor_date: "",
  on_date: "",
  task_id: "",
};

export default function Desk() {
  const supabase = useMemo(() => getSupabase(), []);
  const [settings, setSettings] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [items, setItems] = useState([]);
  const [error, setError] = useState("");
  const [users, setUsers] = useState([]);
  const [awards, setAwards] = useState([]);
  const [userId, setUserId] = useState("");
  const [userDraft, setUserDraft] = useState("");
  const [awardDraft, setAwardDraft] = useState({ title: "TV time", message: "", minutes: 60, show_when: "all_done" });
  const [taskDraft, setTaskDraft] = useState(blankTask);
  const [itemDraft, setItemDraft] = useState(blankItem);

  const load = useCallback(async () => {
    if (!supabase) return;
    const [settingsRes, userRes, taskRes, itemRes, awardRes] = await Promise.all([
      supabase.from("tvdesk_settings").select("*").eq("id", 1).maybeSingle(),
      supabase.from("tvdesk_users").select("*").eq("active", true).order("sort_order"),
      supabase.from("tvdesk_tasks").select("*").eq("active", true).order("sort_order"),
      supabase.from("tvdesk_schedule_items").select("*").eq("active", true).order("sort_order"),
      supabase.from("tvdesk_awards").select("*").eq("active", true).order("sort_order"),
    ]);
    const failed = settingsRes.error || userRes.error || taskRes.error || itemRes.error || awardRes.error;
    if (failed) {
      setError(failed.message + " — run supabase/schema.sql in the Supabase SQL editor first.");
      return;
    }
    setError("");
    setSettings(settingsRes.data);
    setUsers(userRes.data || []);
    setTasks(taskRes.data || []);
    setItems(itemRes.data || []);
    setAwards(awardRes.data || []);
    setUserId((current) => current || settingsRes.data?.active_user_id || (userRes.data && userRes.data[0] && userRes.data[0].id) || "");
  }, [supabase]);

  useEffect(() => {
    load();
  }, [load]);

  if (!supabase) {
    return (
      <main className="wrap">
        <p className="kicker">TV DESK</p>
        <h1>Connect Supabase</h1>
        <p className="lede">Add the project URL and anon key in web/.env.local, then restart.</p>
      </main>
    );
  }

  async function run(action) {
    const { error: writeError } = await action();
    if (writeError) {
      setError(writeError.message);
      return;
    }
    await load();
  }

  function nextOrder(rows) {
    return rows.reduce((max, row) => Math.max(max, row.sort_order || 0), 0) + 1;
  }

  function payload(row) {
    return {
      label: row.label,
      detail: row.detail || "",
      icon: row.icon || "star",
      cadence: row.cadence,
      weekdays: row.cadence === "weekly" ? row.weekdays || [] : [],
      every_n_days: row.cadence === "periodic" ? Number(row.every_n_days) || 1 : null,
      anchor_date: row.cadence === "periodic" && row.anchor_date ? row.anchor_date : null,
      on_date: row.cadence === "once" && row.on_date ? row.on_date : null,
      goal_count: Number(row.goal_count) || 1,
    };
  }

  return (
    <main className="wrap">
      <p className="kicker">TV DESK</p>
      <h1>Edit the TV board</h1>
      <p className="lede">
        One board, four looks on the TV: Cards, Classic, Agenda, and Night. The television also has a Theme button.
        Daily tasks show every day. Weekly tasks use the checked days. Every N days counts from the start date.
      </p>
      {error ? <p className="error">{error}</p> : null}

      <section className="card tasks">
        <h2>Users</h2>
        <p className="sub">Pick a person, then edit only their tasks and schedule. Add the other names here.</p>
        <div className="inline">
          {users.map((user) => (
            <button
              key={user.id}
              type="button"
              className={user.id === userId ? undefined : "ghost"}
              onClick={() => {
                setUserId(user.id);
                run(() => supabase.from("tvdesk_settings").update({ active_user_id: user.id }).eq("id", 1));
              }}
            >
              {user.name}
            </button>
          ))}
        </div>
        <form
          className="task-add"
          onSubmit={(event) => {
            event.preventDefault();
            const name = userDraft.trim();
            if (!name) return;
            setUserDraft("");
            run(() => supabase.from("tvdesk_users").insert({ name, sort_order: nextOrder(users) }));
          }}
        >
          <input type="text" placeholder="New user" aria-label="New user" value={userDraft} onChange={(event) => setUserDraft(event.target.value)} />
          <button type="submit">Add user</button>
        </form>
      </section>

      {settings ? (
        <section className="card tasks">
          <h2>Screen</h2>
          <div className="fields">
            <div className="inline">
              <input
                type="text"
                aria-label="City"
                defaultValue={settings.city}
                key={`city-${settings.city}`}
                onBlur={(event) =>
                  run(() => supabase.from("tvdesk_settings").update({ city: event.target.value.trim() || "Bothell" }).eq("id", 1))
                }
              />
              <input
                type="text"
                aria-label="Countdown label"
                defaultValue={settings.countdown_label}
                key={`label-${settings.countdown_label}`}
                onBlur={(event) =>
                  run(() => supabase.from("tvdesk_settings").update({ countdown_label: event.target.value.trim() || "SAT" }).eq("id", 1))
                }
              />
              <input
                type="date"
                aria-label="Countdown date"
                defaultValue={settings.countdown_date || ""}
                key={`date-${settings.countdown_date || ""}`}
                onChange={(event) =>
                  run(() => supabase.from("tvdesk_settings").update({ countdown_date: event.target.value || null }).eq("id", 1))
                }
              />
              <select
                aria-label="Theme"
                value={settings.theme}
                onChange={(event) => run(() => supabase.from("tvdesk_settings").update({ theme: event.target.value }).eq("id", 1))}
              >
                {THEMES.map(([value, label]) => (
                  <option key={value} value={value}>{label}</option>
                ))}
              </select>
            </div>
          </div>
        </section>
      ) : null}

      <section className="card tasks">
        <h2>Tasks</h2>
        <p className="sub">These become the buttons or cards for the selected user. Goal is how many times it must be marked to count as done today.</p>
        {tasks.filter((task) => task.user_id === userId).map((task) => (
          <TaskEditor
            key={task.id}
            task={task}
            onSave={(patch) => run(() => supabase.from("tvdesk_tasks").update(payload({ ...task, ...patch })).eq("id", task.id))}
            onDelete={() => run(() => supabase.from("tvdesk_tasks").delete().eq("id", task.id))}
          />
        ))}
        <TaskEditor
          task={taskDraft}
          draft
          onChange={setTaskDraft}
          onSave={() => {
            if (!taskDraft.label.trim() || !userId) return;
            const draft = taskDraft;
            setTaskDraft(blankTask);
            const mine = tasks.filter((task) => task.user_id === userId);
            run(() => supabase.from("tvdesk_tasks").insert({ ...payload(draft), user_id: userId, label: draft.label.trim(), sort_order: nextOrder(mine) }));
          }}
        />
      </section>

      <section className="card tasks">
        <h2>Schedule</h2>
        <p className="sub">The TV places each line on today or tomorrow from its repeat rule. A one-date line uses that date.</p>
        {items.filter((item) => item.user_id === userId).map((item) => (
          <ItemEditor
            key={item.id}
            item={item}
            tasks={tasks.filter((task) => task.user_id === userId)}
            onSave={(patch) =>
              run(() =>
                supabase.from("tvdesk_schedule_items").update(itemPayload({ ...item, ...patch })).eq("id", item.id)
              )
            }
            onDelete={() => run(() => supabase.from("tvdesk_schedule_items").delete().eq("id", item.id))}
          />
        ))}
        <ItemEditor
          item={itemDraft}
          tasks={tasks.filter((task) => task.user_id === userId)}
          draft
          onChange={setItemDraft}
          onSave={() => {
            if (!itemDraft.title.trim() || !userId) return;
            const draft = itemDraft;
            setItemDraft(blankItem);
            const mine = items.filter((item) => item.user_id === userId);
            run(() =>
              supabase.from("tvdesk_schedule_items").insert({
                ...itemPayload(draft),
                user_id: userId,
                title: draft.title.trim(),
                sort_order: nextOrder(mine),
              })
            );
          }}
        />
      </section>

      <section className="card tasks">
        <h2>Awards</h2>
        <p className="sub">
          When every task due today is done, the TV hides the task cards and shows these awards.
          Leave the person blank to give the award to everyone.
        </p>
        {awards.map((award) => (
          <div className="block" key={award.id}>
            <p>{award.title ? `${award.title} — ` : ""}{award.message}</p>
            <p className="sub">{award.user_id ? (users.find((user) => user.id === award.user_id) || {}).name : "Everyone"} · {award.show_when}{award.minutes ? ` · ${award.minutes} min` : ""}</p>
            <button className="ghost danger" type="button" onClick={() => run(() => supabase.from("tvdesk_awards").delete().eq("id", award.id))}>Remove</button>
          </div>
        ))}
        <form
          className="fields"
          onSubmit={(event) => {
            event.preventDefault();
            const message = awardDraft.message.trim();
            if (!message) return;
            const draft = awardDraft;
            setAwardDraft({ title: "TV time", message: "", minutes: 60, show_when: "all_done" });
            run(() => supabase.from("tvdesk_awards").insert({
              title: draft.title.trim(),
              message,
              minutes: Number(draft.minutes) || null,
              show_when: draft.show_when,
              user_id: userId || null,
              sort_order: nextOrder(awards),
            }));
          }}
        >
          <input type="text" aria-label="Award title" placeholder="Title" value={awardDraft.title} onChange={(event) => setAwardDraft({ ...awardDraft, title: event.target.value })} />
          <input type="text" aria-label="Award message" placeholder="You have been awarded with 60 mins TV time today" value={awardDraft.message} onChange={(event) => setAwardDraft({ ...awardDraft, message: event.target.value })} />
          <div className="inline">
            <input type="number" min="1" aria-label="Minutes" value={awardDraft.minutes} onChange={(event) => setAwardDraft({ ...awardDraft, minutes: event.target.value })} style={{ width: 100 }} />
            <select aria-label="When" value={awardDraft.show_when} onChange={(event) => setAwardDraft({ ...awardDraft, show_when: event.target.value })}>
              <option value="all_done">When all tasks are done</option>
              <option value="always">Whenever the award panel is open</option>
            </select>
            <button type="submit">Add award for selected user</button>
          </div>
        </form>
      </section>
    </main>
  );
}

function itemPayload(row) {
  return {
    title: row.title,
    time_label: row.time_label || "",
    cadence: row.cadence,
    weekdays: row.cadence === "weekly" ? row.weekdays || [] : [],
    every_n_days: row.cadence === "periodic" ? Number(row.every_n_days) || 1 : null,
    anchor_date: row.cadence === "periodic" && row.anchor_date ? row.anchor_date : null,
    on_date: row.cadence === "once" && row.on_date ? row.on_date : null,
    task_id: row.task_id || null,
  };
}

function CadenceFields({ row, onChange }) {
  function toggleDay(day) {
    const current = row.weekdays || [];
    const weekdays = current.includes(day) ? current.filter((value) => value !== day) : current.concat(day).sort();
    onChange({ weekdays });
  }
  return (
    <div className="fields">
      <select aria-label="Repeat" value={row.cadence} onChange={(event) => onChange({ cadence: event.target.value })}>
        {CADENCES.map(([value, label]) => (
          <option key={value} value={value}>{label}</option>
        ))}
      </select>
      {row.cadence === "weekly" ? (
        <div className="days">
          {DAYS.map((label, index) => (
            <label key={label}>
              <input type="checkbox" checked={(row.weekdays || []).includes(index)} onChange={() => toggleDay(index)} /> {label}
            </label>
          ))}
        </div>
      ) : null}
      {row.cadence === "periodic" ? (
        <div className="inline">
          <input type="number" min="1" aria-label="Every N days" value={row.every_n_days || 1} onChange={(event) => onChange({ every_n_days: event.target.value })} />
          <input type="date" aria-label="Start date" value={row.anchor_date || ""} onChange={(event) => onChange({ anchor_date: event.target.value })} />
        </div>
      ) : null}
      {row.cadence === "once" ? (
        <input type="date" aria-label="Date" value={row.on_date || ""} onChange={(event) => onChange({ on_date: event.target.value })} />
      ) : null}
    </div>
  );
}

function TaskEditor({ task, draft, onSave, onDelete, onChange }) {
  function update(patch) {
    if (draft) onChange({ ...task, ...patch });
    else onSave(patch);
  }
  return (
    <div className="block">
      <div className="inline">
        <input type="text" aria-label="Task name" placeholder="Task name" value={draft ? task.label : undefined} defaultValue={draft ? undefined : task.label} onChange={draft ? (event) => update({ label: event.target.value }) : undefined} onBlur={draft ? undefined : (event) => update({ label: event.target.value.trim() })} />
        <input type="text" aria-label="Detail" placeholder="Detail" value={draft ? task.detail : undefined} defaultValue={draft ? undefined : task.detail} onChange={draft ? (event) => update({ detail: event.target.value }) : undefined} onBlur={draft ? undefined : (event) => update({ detail: event.target.value })} />
        <select aria-label="Icon" value={task.icon || "star"} onChange={(event) => update({ icon: event.target.value })}>
          {ICONS.map((icon) => (
            <option key={icon} value={icon}>{icon}</option>
          ))}
        </select>
        <input type="number" min="1" aria-label="Goal" value={task.goal_count || 1} onChange={(event) => update({ goal_count: event.target.value })} style={{ width: 80 }} />
      </div>
      <CadenceFields row={task} onChange={update} />
      <div className="inline">
        {draft ? <button type="button" onClick={onSave}>Add task</button> : <button className="ghost danger" type="button" onClick={onDelete}>Remove</button>}
      </div>
    </div>
  );
}

function ItemEditor({ item, tasks, draft, onSave, onDelete, onChange }) {
  function update(patch) {
    if (draft) onChange({ ...item, ...patch });
    else onSave(patch);
  }
  return (
    <div className="block">
      <div className="inline">
        <input type="text" aria-label="Time" placeholder="9:00 AM" value={draft ? item.time_label : undefined} defaultValue={draft ? undefined : item.time_label} onChange={draft ? (event) => update({ time_label: event.target.value }) : undefined} onBlur={draft ? undefined : (event) => update({ time_label: event.target.value })} />
        <input type="text" aria-label="Title" placeholder="What happens" value={draft ? item.title : undefined} defaultValue={draft ? undefined : item.title} onChange={draft ? (event) => update({ title: event.target.value }) : undefined} onBlur={draft ? undefined : (event) => update({ title: event.target.value.trim() })} />
        <select aria-label="Tied to task" value={item.task_id || ""} onChange={(event) => update({ task_id: event.target.value })}>
          <option value="">Not tied to a task</option>
          {tasks.map((task) => (
            <option key={task.id} value={task.id}>{task.label}</option>
          ))}
        </select>
      </div>
      <CadenceFields row={item} onChange={update} />
      <div className="inline">
        {draft ? <button type="button" onClick={onSave}>Add to schedule</button> : <button className="ghost danger" type="button" onClick={onDelete}>Remove</button>}
      </div>
    </div>
  );
}
