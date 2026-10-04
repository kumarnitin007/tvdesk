export const MONDAY_FIRST = [
  [1, "M", "Monday"],
  [2, "T", "Tuesday"],
  [3, "W", "Wednesday"],
  [4, "T", "Thursday"],
  [5, "F", "Friday"],
  [6, "S", "Saturday"],
  [0, "S", "Sunday"],
];

export const BADGES = [
  ["star", "☆", "Star"],
  ["fire", "♨", "Fire"],
  ["heart", "♡", "Heart"],
  ["none", "", "None"],
];

export function frequencyOf(task) {
  if (task.cadence === "daily") return "daily";
  const days = task.weekdays || [];
  if (task.cadence === "weekly" && days.length === 5 && [1, 2, 3, 4, 5].every((day) => days.includes(day))) {
    return "weekdays";
  }
  if (task.cadence === "weekly") return "weekly";
  return "custom";
}

export function applyFrequency(draft, frequency, today) {
  if (frequency === "daily") {
    return { ...draft, cadence: "daily", weekdays: [], every_n_days: null, anchor_date: null, on_date: null };
  }
  if (frequency === "weekdays") {
    return { ...draft, cadence: "weekly", weekdays: [1, 2, 3, 4, 5], every_n_days: null, anchor_date: null, on_date: null };
  }
  if (frequency === "weekly") {
    return { ...draft, cadence: "weekly", weekdays: draft.weekdays?.length ? draft.weekdays : [new Date(`${today}T12:00:00`).getDay()], every_n_days: null, anchor_date: null, on_date: null };
  }
  return { ...draft, cadence: draft.cadence === "periodic" || draft.cadence === "once" ? draft.cadence : "weekly" };
}

export function taskPayload(draft, userId, sortOrder) {
  return {
    user_id: userId,
    label: draft.label.trim(),
    detail: (draft.detail || "").trim(),
    icon: draft.icon || "star",
    sort_order: sortOrder,
    cadence: draft.cadence,
    weekdays: draft.cadence === "weekly" ? draft.weekdays || [] : [],
    every_n_days: draft.cadence === "periodic" ? Math.max(Number(draft.every_n_days) || 1, 1) : null,
    anchor_date: draft.cadence === "periodic" ? draft.anchor_date || null : null,
    on_date: draft.cadence === "once" ? draft.on_date || null : null,
    goal_count: Math.max(Number(draft.goal_count) || 1, 1),
    active: true,
  };
}

export function expiresOn(postedOn, duration) {
  if (duration === "until_removed") return null;
  const date = new Date(`${postedOn}T12:00:00`);
  if (duration === "3_days") date.setDate(date.getDate() + 2);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function postPayload(draft, postedOn, sortOrder) {
  return {
    user_id: draft.user_id || null,
    message: draft.message.trim(),
    badge: draft.badge,
    duration: draft.duration,
    bonus_stars: Math.max(Number(draft.bonus_stars) || 0, 0),
    posted_on: postedOn,
    expires_on: expiresOn(postedOn, draft.duration),
    sort_order: sortOrder,
    active: true,
  };
}

export function activeFor(post, userId, today) {
  if (!post.active || post.removed_at) return false;
  if (post.user_id && post.user_id !== userId) return false;
  return !post.expires_on || post.expires_on >= today;
}

function dayKey(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function streakEnding(days, today) {
  const done = new Set(days);
  const cursor = new Date(`${today}T12:00:00`);
  const yesterday = new Date(cursor);
  yesterday.setDate(cursor.getDate() - 1);
  const start = done.has(dayKey(cursor)) ? cursor : done.has(dayKey(yesterday)) ? yesterday : null;
  if (!start) return 0;
  let streak = 0;
  while (done.has(dayKey(start))) {
    streak += 1;
    start.setDate(start.getDate() - 1);
  }
  return streak;
}

export function syncTaskStar(rows, event) {
  const rest = rows.filter((row) => !(row.reason === "task_complete" && row.userId === event.userId && row.taskId === event.taskId && row.day === event.day));
  if (!event.done) return rest;
  return [...rest, { userId: event.userId, taskId: event.taskId, day: event.day, reason: "task_complete", stars: 1 }];
}

export function starsToward(rows) {
  return Math.max(rows.reduce((sum, row) => sum + row.stars, 0), 0);
}
