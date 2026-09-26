export const ICONS = [
  ["document", "Document"],
  ["math", "Maths"],
  ["game", "Game"],
  ["art", "Art"],
  ["book", "Book"],
  ["star", "Star"],
];

export const CADENCES = [
  ["daily", "Every day"],
  ["weekly", "Certain weekdays"],
  ["periodic", "Every N days"],
  ["once", "One date only"],
];

export const DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

export const THEMES = [
  ["cards", "Cards"],
  ["classic", "Classic"],
  ["agenda", "Agenda"],
  ["night", "Night"],
];

export const SHOW_WHEN = [
  ["all_done", "When every task due today is done"],
  ["min_done", "Only if at least this many tasks are done"],
  ["always", "Whenever the award screen opens"],
];

export function parseKey(key) {
  const parts = String(key).split("-").map(Number);
  return new Date(parts[0], parts[1] - 1, parts[2], 12, 0, 0, 0);
}

export function dayKey(date) {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

export function addDays(key, days) {
  const date = parseKey(key);
  date.setDate(date.getDate() + days);
  return dayKey(date);
}

export function dayNumber(key) {
  return Math.floor(parseKey(key).getTime() / 86400000);
}

export function occurs(row, key) {
  if (row.cadence === "daily") return true;
  if (row.cadence === "once") return row.on_date === key;
  if (row.cadence === "weekly") return (row.weekdays || []).includes(parseKey(key).getDay());
  if (row.cadence === "periodic") {
    const every = Number(row.every_n_days) || 0;
    if (every < 1 || !row.anchor_date) return false;
    const diff = dayNumber(key) - dayNumber(row.anchor_date);
    return diff >= 0 && diff % every === 0;
  }
  return false;
}

export function greeting(date) {
  const hour = date.getHours();
  if (hour < 12) return "Good morning";
  if (hour < 17) return "Good afternoon";
  return "Good evening";
}

export function nextSaturday(key) {
  const date = parseKey(key);
  date.setDate(date.getDate() + ((6 - date.getDay() + 7) % 7));
  return dayKey(date);
}

export function countdownDays(target, today) {
  return Math.max(dayNumber(target) - dayNumber(today), 0);
}

export function longDate(date) {
  return date.toLocaleDateString(undefined, { weekday: "long", month: "long", day: "numeric" });
}

export function shortDate(key) {
  if (!key) return "";
  return parseKey(key).toLocaleDateString(undefined, { month: "short", day: "numeric" });
}

export function cadenceText(row) {
  if (row.cadence === "daily") return "Every day";
  if (row.cadence === "weekly") {
    const picked = (row.weekdays || []).map((day) => DAYS[day]).join(", ");
    return picked ? `Every ${picked}` : "Weekly, no days picked yet";
  }
  if (row.cadence === "periodic") {
    const every = Number(row.every_n_days) || 0;
    if (every < 1 || !row.anchor_date) return "Every N days, needs a number and a start date";
    return `Every ${every} days from ${shortDate(row.anchor_date)}`;
  }
  if (row.cadence === "once") return row.on_date ? `Only on ${shortDate(row.on_date)}` : "One date, not picked yet";
  return "";
}

export function earnedAwards(awards, userId, done, total) {
  if (total < 1 || done < total) return [];
  return awards.filter((award) => {
    if (award.user_id && award.user_id !== userId) return false;
    if (award.show_when === "min_done" && done < (Number(award.min_done) || 0)) return false;
    return true;
  });
}
