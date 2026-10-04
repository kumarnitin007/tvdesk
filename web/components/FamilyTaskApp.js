"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { getSupabase } from "../lib/supabase";
import { cadenceText, dayKey } from "../lib/board";
import { BADGES, MONDAY_FIRST, activeFor, applyFrequency, frequencyOf, postPayload, taskPayload } from "../lib/family.mjs";
import Sheet from "./Sheet";

const EMPTY_TASK = {
  label: "",
  detail: "",
  icon: "star",
  cadence: "daily",
  weekdays: [1, 2, 3, 4, 5],
  every_n_days: 2,
  anchor_date: "",
  on_date: "",
  goal_count: 1,
};

const EMPTY_POST = { user_id: "", message: "", badge: "star", bonus_stars: 3, duration: "today" };

const GROUPS = [
  ["daily", "Daily"],
  ["weekdays", "Weekdays"],
  ["weekly", "Weekly"],
  ["custom", "Custom"],
];

function initials(name) {
  return String(name || "?").split(/\s+/).map((word) => word[0]).join("").slice(0, 2).toUpperCase();
}

function Field({ label, children }) {
  return <label className="family-field"><span>{label}</span>{children}</label>;
}

export default function FamilyTaskApp() {
  const supabase = useMemo(() => getSupabase(), []);
  const [board, setBoard] = useState({ users: [], tasks: [], logs: [], posts: [], achievements: [], settings: null });
  const [userId, setUserId] = useState("");
  const [sheet, setSheet] = useState("");
  const [taskDraft, setTaskDraft] = useState(EMPTY_TASK);
  const [taskId, setTaskId] = useState("");
  const [frequency, setFrequency] = useState("daily");
  const [postDraft, setPostDraft] = useState(EMPTY_POST);
  const [achievement, setAchievement] = useState({ title: "", subtitle: "", icon: "star" });
  const [newUser, setNewUser] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [saving, setSaving] = useState(false);
  const [extrasReady, setExtrasReady] = useState(true);
  const today = dayKey(new Date());

  const load = useCallback(async () => {
    if (!supabase) return;
    const [usersRes, tasksRes, logsRes, settingsRes, postsRes, achievementsRes] = await Promise.all([
      supabase.from("tvdesk_users").select("*").eq("active", true).order("sort_order"),
      supabase.from("tvdesk_tasks").select("*").eq("active", true).order("sort_order"),
      supabase.from("tvdesk_task_logs").select("task_id,count").eq("day", today),
      supabase.from("tvdesk_settings").select("*").eq("id", 1).maybeSingle(),
      supabase.from("tvdesk_posts_active").select("*").order("sort_order"),
      supabase.from("tvdesk_user_achievements").select("*").eq("active", true).order("sort_order"),
    ]);
    const baseError = usersRes.error || tasksRes.error || logsRes.error || settingsRes.error;
    if (baseError) {
      setError(`${baseError.message} — run supabase/schema.sql for a new database.`);
      return;
    }
    const extrasMissing = Boolean(postsRes.error || achievementsRes.error);
    setExtrasReady(!extrasMissing);
    setBoard({
      users: usersRes.data || [],
      tasks: tasksRes.data || [],
      logs: logsRes.data || [],
      settings: settingsRes.data,
      posts: postsRes.data || [],
      achievements: achievementsRes.data || [],
    });
    setUserId((current) => current || settingsRes.data?.active_user_id || usersRes.data?.[0]?.id || "");
    setError("");
  }, [supabase, today]);

  useEffect(() => { load(); }, [load]);

  const user = board.users.find((row) => row.id === userId) || board.users[0] || null;
  const tasks = board.tasks.filter((row) => row.user_id === user?.id);
  const done = new Map(board.logs.map((row) => [row.task_id, row.count]));
  const activePosts = board.posts.filter((row) => activeFor(row, user?.id, today));

  function openTask(task = null) {
    const next = task ? { ...EMPTY_TASK, ...task } : { ...EMPTY_TASK, anchor_date: today, on_date: today };
    setTaskDraft(next);
    setTaskId(task?.id || "");
    setFrequency(frequencyOf(next));
    setSheet("task");
    setError("");
  }

  async function saveTask() {
    if (!user || !taskDraft.label.trim()) return setError("Enter a task name.");
    setSaving(true);
    const payload = taskPayload(taskDraft, user.id, taskId ? taskDraft.sort_order : tasks.length + 1);
    const result = taskId
      ? await supabase.from("tvdesk_tasks").update(payload).eq("id", taskId)
      : await supabase.from("tvdesk_tasks").insert(payload);
    setSaving(false);
    if (result.error) return setError(result.error.message);
    setSheet("");
    setNotice(taskId ? "Task saved." : "Task added.");
    await load();
  }

  async function deleteTask() {
    if (!taskId || !window.confirm("Delete this task and its completion history?")) return;
    const { error: writeError } = await supabase.from("tvdesk_tasks").delete().eq("id", taskId);
    if (writeError) return setError(writeError.message);
    setSheet("");
    await load();
  }

  async function savePost() {
    if (!extrasReady) return setError("Run supabase/schema.sql in the Supabase SQL editor first.");
    if (!postDraft.message.trim()) return setError("Enter a message.");
    setSaving(true);
    const payload = postPayload(postDraft, today, board.posts.length + 1);
    const { error: writeError } = await supabase.from("tvdesk_posts").insert(payload);
    setSaving(false);
    if (writeError) return setError(writeError.message);
    setPostDraft(EMPTY_POST);
    setSheet("");
    setNotice("Posted to the TV.");
    await load();
  }

  async function saveAchievement() {
    if (!user || !achievement.title.trim()) return setError("Enter an achievement.");
    const mine = board.achievements.filter((row) => row.user_id === user.id);
    for (const row of mine) await supabase.from("tvdesk_user_achievements").update({ active: false }).eq("id", row.id);
    const { error: writeError } = await supabase.from("tvdesk_user_achievements").insert({
      user_id: user.id,
      title: achievement.title.trim(),
      subtitle: achievement.subtitle.trim(),
      icon: achievement.icon,
      sort_order: mine.length + 1,
    });
    if (writeError) return setError(writeError.message);
    setNotice("Achievement saved.");
    await load();
  }

  async function saveReward() {
    if (!user) return;
    const { error: writeError } = await supabase.from("tvdesk_users").update({
      reward_title: user.reward_title || "",
      reward_target: Math.max(Number(user.reward_target) || 0, 0),
    }).eq("id", user.id);
    if (writeError) return setError(writeError.message);
    setNotice("Reward saved.");
    await load();
  }

  async function redeemReward() {
    if (!user || !window.confirm(`Redeem ${user.reward_title}?`)) return;
    const { error: writeError } = await supabase.rpc("tvdesk_redeem_reward", { p_user_id: user.id });
    if (writeError) return setError(writeError.message);
    setNotice("Reward redeemed. Star progress restarted.");
    await load();
  }

  async function addUser() {
    const name = newUser.trim();
    if (!name) return;
    const { data, error: writeError } = await supabase.from("tvdesk_users").insert({
      name,
      sort_order: board.users.length + 1,
    }).select("*").single();
    if (writeError) return setError(writeError.message);
    setNewUser("");
    setSheet("");
    await load();
    setUserId(data.id);
  }

  async function removeUser(id) {
    if (board.users.length < 2) return setError("Keep at least one person.");
    if (!window.confirm("Delete this person and all of their tasks, posts, achievements, and history?")) return;
    const { error: writeError } = await supabase.from("tvdesk_users").delete().eq("id", id);
    if (writeError) return setError(writeError.message);
    setSheet("");
    setUserId("");
    await load();
  }

  function updateUser(part) {
    setBoard((current) => ({
      ...current,
      users: current.users.map((row) => row.id === user.id ? { ...row, ...part } : row),
    }));
  }

  if (!supabase) return <main className="family-page"><h1>Connect Supabase</h1><p>Add the public Supabase URL and anon key.</p></main>;

  const dateLabel = new Date().toLocaleDateString(undefined, { weekday: "long", month: "short", day: "numeric" });

  return (
    <main className="family-page">
      <header className="family-header">
        <div><p className="family-eyebrow">TV DESK</p><h1>Family tasks</h1><p>{dateLabel}</p></div>
        <div className="header-actions">
          <button className="outline-button" type="button" onClick={() => { setPostDraft({ ...EMPTY_POST, user_id: user?.id || "" }); setSheet("post"); }}>Post to TV</button>
          <button className="round-button" type="button" onClick={() => {
            const current = board.achievements.filter((row) => row.user_id === user?.id).slice(-1)[0];
            setAchievement(current ? { title: current.title, subtitle: current.subtitle || "", icon: current.icon || "star" } : { title: "", subtitle: "", icon: "star" });
            setSheet("settings");
          }} aria-label="Settings">⌘</button>
        </div>
      </header>

      <nav className="family-users" aria-label="Choose person">
        {board.users.map((row) => (
          <button key={row.id} className={row.id === user?.id ? "person-pill selected" : "person-pill"} type="button" onClick={() => setUserId(row.id)}>
            {row.name}
          </button>
        ))}
        <button className="person-pill add" type="button" onClick={() => setSheet("user")} aria-label="Add person">+</button>
      </nav>

      {!extrasReady ? <p className="migration-banner">Tasks work now. Run <code>supabase/schema.sql</code> once in the Supabase SQL editor to reset the board for posts, stars, achievements, and rewards.</p> : null}
      {error ? <p className="family-error">{error}</p> : null}
      {notice ? <p className="family-notice">{notice}</p> : null}

      <section className="task-groups">
        {GROUPS.map(([key, label]) => {
          const rows = tasks.filter((task) => frequencyOf(task) === key);
          if (!rows.length) return null;
          return (
            <div className="task-group" key={key}>
              <p className="group-label">{label}</p>
              {rows.map((task, index) => {
                const complete = (done.get(task.id) || 0) >= (task.goal_count || 1);
                return (
                  <button type="button" className="family-task-row" key={task.id} onClick={() => openTask(task)}>
                    <span className={`task-dot dot-${index % 4}`} />
                    <span className={complete ? "task-name completed" : "task-name"}>{task.label}</span>
                    <span className="task-cadence">{cadenceText(task)}</span>
                  </button>
                );
              })}
            </div>
          );
        })}
        {!tasks.length ? <div className="empty-family"><p>No tasks for {user?.name || "this person"} yet.</p></div> : null}
      </section>

      <button className="primary-button add-task-button" type="button" onClick={() => openTask()}>New task for {user?.name || "person"}</button>

      {activePosts.length ? (
        <section className="active-posts">
          <p className="group-label">On the TV now</p>
          {activePosts.map((post) => <p key={post.id}>{BADGES.find(([key]) => key === post.badge)?.[1]} {post.message}</p>)}
        </section>
      ) : null}

      {sheet === "task" ? (
        <Sheet title={`${taskId ? "Edit" : "New"} task for ${user?.name || "person"}`} onClose={() => setSheet("")}>
          <div className="sheet-body">
            <Field label="Task name"><input autoFocus value={taskDraft.label} onChange={(event) => setTaskDraft({ ...taskDraft, label: event.target.value })} placeholder="Read for 20 minutes" /></Field>
            <Field label="Detail (optional)"><input value={taskDraft.detail || ""} onChange={(event) => setTaskDraft({ ...taskDraft, detail: event.target.value })} placeholder="Chapter 6" /></Field>
            <Field label="How often">
              <div className="segment-row">
                {["daily", "weekdays", "weekly", "custom"].map((value) => (
                  <button key={value} type="button" className={frequency === value ? "segment selected" : "segment"} onClick={() => {
                    setFrequency(value);
                    setTaskDraft(applyFrequency(taskDraft, value, today));
                  }}>{value[0].toUpperCase() + value.slice(1)}</button>
                ))}
              </div>
            </Field>
            {(frequency === "weekly" || (frequency === "custom" && taskDraft.cadence === "weekly")) ? (
              <Field label="On these days">
                <div className="day-row">
                  {MONDAY_FIRST.map(([value, short, full]) => (
                    <button key={full} type="button" aria-label={full} className={(taskDraft.weekdays || []).includes(value) ? "day-chip selected" : "day-chip"} onClick={() => {
                      const days = taskDraft.weekdays || [];
                      setTaskDraft({ ...taskDraft, weekdays: days.includes(value) ? days.filter((day) => day !== value) : [...days, value].sort() });
                    }}>{short}</button>
                  ))}
                </div>
              </Field>
            ) : null}
            {frequency === "custom" ? (
              <>
                <Field label="Custom rule">
                  <div className="segment-row">
                    <button type="button" className={taskDraft.cadence === "weekly" ? "segment selected" : "segment"} onClick={() => setTaskDraft({ ...taskDraft, cadence: "weekly", weekdays: taskDraft.weekdays?.length ? taskDraft.weekdays : [1] })}>Days</button>
                    <button type="button" className={taskDraft.cadence === "periodic" ? "segment selected" : "segment"} onClick={() => setTaskDraft({ ...taskDraft, cadence: "periodic", anchor_date: taskDraft.anchor_date || today })}>Every N days</button>
                    <button type="button" className={taskDraft.cadence === "once" ? "segment selected" : "segment"} onClick={() => setTaskDraft({ ...taskDraft, cadence: "once", on_date: taskDraft.on_date || today })}>One date</button>
                  </div>
                </Field>
                {taskDraft.cadence === "periodic" ? <div className="two-fields"><Field label="Every"><input type="number" min="1" value={taskDraft.every_n_days || 1} onChange={(event) => setTaskDraft({ ...taskDraft, every_n_days: event.target.value })} /></Field><Field label="Starting"><input type="date" value={taskDraft.anchor_date || today} onChange={(event) => setTaskDraft({ ...taskDraft, anchor_date: event.target.value })} /></Field></div> : null}
                {taskDraft.cadence === "once" ? <Field label="On"><input type="date" value={taskDraft.on_date || today} onChange={(event) => setTaskDraft({ ...taskDraft, on_date: event.target.value })} /></Field> : null}
              </>
            ) : null}
            <button className="primary-button" type="button" onClick={saveTask} disabled={saving}>{saving ? "Saving…" : taskId ? "Save task" : "Add task"}</button>
            {taskId ? <button className="danger-button" type="button" onClick={deleteTask}>Delete task</button> : null}
          </div>
        </Sheet>
      ) : null}

      {sheet === "post" ? (
        <Sheet title="Post to the TV" onClose={() => setSheet("")}>
          <div className="sheet-body">
            <Field label="Who is it for">
              <div className="segment-row">
                {board.users.map((row) => <button key={row.id} type="button" className={postDraft.user_id === row.id ? "segment selected" : "segment"} onClick={() => setPostDraft({ ...postDraft, user_id: row.id })}>{row.name}</button>)}
                <button type="button" className={!postDraft.user_id ? "segment selected" : "segment"} onClick={() => setPostDraft({ ...postDraft, user_id: "" })}>Everyone</button>
              </div>
            </Field>
            <Field label="Message"><textarea rows="3" value={postDraft.message} onChange={(event) => setPostDraft({ ...postDraft, message: event.target.value })} placeholder="Great job finishing Maths early this week!" /></Field>
            <Field label="Badge"><div className="badge-row">{BADGES.map(([value, symbol, label]) => <button key={value} type="button" className={postDraft.badge === value ? "badge-choice selected" : "badge-choice"} onClick={() => setPostDraft({ ...postDraft, badge: value })}><b>{symbol}</b><span>{label}</span></button>)}</div></Field>
            <Field label="Bonus stars"><div className="stepper"><button type="button" onClick={() => setPostDraft({ ...postDraft, bonus_stars: Math.max(postDraft.bonus_stars - 1, 0) })}>−</button><b>{postDraft.bonus_stars}</b><button type="button" onClick={() => setPostDraft({ ...postDraft, bonus_stars: postDraft.bonus_stars + 1 })}>+</button></div></Field>
            <Field label="Show on TV for"><div className="segment-row">{[["today", "Today"], ["3_days", "3 days"], ["until_removed", "Until removed"]].map(([value, label]) => <button key={value} type="button" className={postDraft.duration === value ? "segment selected" : "segment"} onClick={() => setPostDraft({ ...postDraft, duration: value })}>{label}</button>)}</div></Field>
            <div className="post-preview"><span>{BADGES.find(([value]) => value === postDraft.badge)?.[1]}</span><div><small>Preview on {postDraft.user_id ? board.users.find((row) => row.id === postDraft.user_id)?.name : "everyone's"} TV</small><strong>{postDraft.message || "Your message will appear here"}</strong></div></div>
            <button className="primary-button" type="button" onClick={savePost} disabled={saving}>{saving ? "Posting…" : "Post to TV"}</button>
          </div>
        </Sheet>
      ) : null}

      {sheet === "user" ? <Sheet title="Add a person" onClose={() => setSheet("")}><div className="sheet-body"><Field label="Name"><input autoFocus value={newUser} onChange={(event) => setNewUser(event.target.value)} placeholder="Kid 3" /></Field><button className="primary-button" type="button" onClick={addUser}>Add person</button></div></Sheet> : null}

      {sheet === "settings" ? (
        <Sheet title={`Manage ${user?.name || "family"}`} onClose={() => setSheet("")} wide>
          <div className="sheet-body management-grid">
            <section>
              <h3>Achievement</h3>
              <Field label="Achievement"><input value={achievement.title} onChange={(event) => setAchievement({ ...achievement, title: event.target.value })} placeholder="5-day Duolingo streak" /></Field>
              <Field label="Small note"><input value={achievement.subtitle} onChange={(event) => setAchievement({ ...achievement, subtitle: event.target.value })} placeholder="Great consistency" /></Field>
              <Field label="Badge"><div className="badge-row">{BADGES.map(([value, symbol, label]) => <button key={value} type="button" className={achievement.icon === value ? "badge-choice selected" : "badge-choice"} onClick={() => setAchievement({ ...achievement, icon: value })}><b>{symbol}</b><span>{label}</span></button>)}</div></Field>
              <button className="primary-button" type="button" onClick={saveAchievement}>Save achievement</button>
            </section>
            <section>
              <h3>Next reward</h3>
              <Field label="Reward"><input value={user?.reward_title || ""} onChange={(event) => updateUser({ reward_title: event.target.value })} placeholder="Movie night" /></Field>
              <Field label="Stars needed"><input type="number" min="0" value={user?.reward_target || 0} onChange={(event) => updateUser({ reward_target: event.target.value })} /></Field>
              <p>{user?.stars_toward_reward || 0} of {user?.reward_target || 0} stars</p>
              <button className="primary-button" type="button" onClick={saveReward}>Save reward</button>
              <button className="outline-button" type="button" disabled={!user?.reward_target || user?.stars_toward_reward < user?.reward_target} onClick={redeemReward}>Redeem reward</button>
            </section>
            <section>
              <h3>People</h3>
              {board.users.map((row) => <div className="manage-person" key={row.id}><span className="avatar-small">{initials(row.name)}</span><b>{row.name}</b><button type="button" className="danger-button" onClick={() => removeUser(row.id)}>Delete</button></div>)}
            </section>
          </div>
        </Sheet>
      ) : null}
    </main>
  );
}
