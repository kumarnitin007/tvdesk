"use client";

import { addDays, countdownDays, earnedAwards, greeting, longDate, nextSaturday, occurs } from "../lib/board";

const ICON_PATHS = {
  document: "M7 3h7l5 5v13H7zM14 3v5h5",
  math: "M5 7h6M8 4v6M14 6h6M14 10h6M5 16l5 5M10 16l-5 5M14 17h6",
  game: "M4 9h16v8H4zM8 13H6M7 12v2M17 12h.01M15 14h.01",
  art: "M12 3a9 9 0 100 18 3 3 0 000-6 3 3 0 010-6 9 9 0 000-6zM8 8h.01M7 13h.01M10 17h.01",
  book: "M5 4h9a3 3 0 013 3v13H8a3 3 0 00-3 3zM8 4v13",
  star: "M12 4l2.4 5 5.6.8-4 4 1 5.6-5-2.8-5 2.8 1-5.6-4-4 5.6-.8z",
};

function Icon({ name }) {
  const path = ICON_PATHS[name === "doc" ? "document" : name] || ICON_PATHS.star;
  return (
    <svg className="tv-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path d={path} fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function clockParts(now) {
  const time = now.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
  const match = time.match(/^(.*?)\s*(AM|PM)?$/i);
  return { time: match ? match[1] : time, suffix: match && match[2] ? match[2] : "" };
}

function ScheduleList({ rows }) {
  if (rows.length === 0) return <p className="tv-empty">Nothing on the schedule</p>;
  return (
    <ul className="tv-schedule">
      {rows.map((row) => (
        <li key={row.id}>
          <span className="tv-time">{row.time_label || "--"}</span>
          <span className="tv-title">{row.title}</span>
        </li>
      ))}
    </ul>
  );
}

export default function TvPreview({ board, userId, theme, today, now }) {
  const users = board.users || [];
  const user = users.find((row) => row.id === userId) || users[0] || null;
  const tomorrow = addDays(today, 1);

  const mine = (row) => !user || !row.user_id || row.user_id === user.id;
  const due = (board.tasks || []).filter((task) => mine(task) && occurs(task, today));
  const doneOf = (task) => (board.doneCounts[task.id] || 0) >= (Number(task.goal_count) || 1);
  const done = due.filter(doneOf).length;
  const allDone = due.length > 0 && done === due.length;

  const todayRows = (board.items || []).filter((item) => mine(item) && occurs(item, today));
  const tomorrowRows = (board.items || []).filter((item) => mine(item) && occurs(item, tomorrow));

  const awards = user ? earnedAwards(board.awards || [], user.id, done, due.length) : [];
  const awardMode = allDone && awards.length > 0;

  const settings = board.settings || {};
  const city = (settings.city || "Bothell").toUpperCase();
  const label = (settings.countdown_label || "SAT").toUpperCase();
  const days = countdownDays(settings.countdown_date || nextSaturday(today), today);
  const { time, suffix } = clockParts(now);
  const withSeconds = now.toLocaleTimeString();

  let unlock = "Add tasks on the Tasks page.";
  if (awardMode) unlock = awards[0].message;
  else if (allDone) unlock = "TV time is unlocked";
  else if (due.length - done === 1) unlock = "1 more task to unlock TV time today";
  else if (due.length > 0) unlock = `${due.length - done} more tasks to unlock TV time today`;

  const focusIndex = awardMode ? 0 : due.findIndex((task) => !doneOf(task));
  const cards = awardMode
    ? awards.map((award) => ({ key: award.id, title: award.title || "Award", detail: award.message, icon: award.icon, state: "award" }))
    : due.map((task, index) => ({
        key: task.id,
        title: task.label,
        detail: doneOf(task) ? (task.detail ? `Done · ${task.detail}` : "Done") : task.detail,
        icon: task.icon,
        state: index === focusIndex ? "focus" : doneOf(task) ? "done" : "idle",
      }));

  const heading = (
    <div className="tv-head">
      <div>
        <p className="tv-kicker">TV DESK · {(user ? user.name : "TV").toUpperCase()}</p>
        <p className="tv-greeting">{greeting(now)}</p>
        <p className="tv-date">{longDate(now)}</p>
      </div>
      <div className="tv-pills">
        <span className="tv-pill">Theme<b>{theme}</b></span>
        <span className="tv-pill">{time}<b>{suffix || "\u00a0"}</b></span>
        <span className="tv-pill">--&#176;<b>{city}</b></span>
        <span className="tv-pill">{days}<b>DAYS TO {label}</b></span>
      </div>
    </div>
  );

  const chips = users.length > 0 && (
    <div className="tv-users">
      {users.map((row) => (
        <span key={row.id} className={row.id === (user ? user.id : "") ? "tv-chip on" : "tv-chip"}>{row.name}</span>
      ))}
    </div>
  );

  const columns = (
    <div className="tv-columns">
      <div className="tv-panel">
        <p className="tv-label">Today</p>
        <ScheduleList rows={todayRows} />
      </div>
      <div className="tv-panel">
        <p className="tv-label">Tomorrow</p>
        <ScheduleList rows={tomorrowRows} />
      </div>
    </div>
  );

  const counter = (
    <div className="tv-counter">
      <p className="tv-label">{awardMode ? "Awards" : "Mark done"}</p>
      <p className="tv-count">{done} of {due.length} done</p>
    </div>
  );

  const tiles = (
    <div className="tv-cards">
      {cards.length === 0 ? <p className="tv-empty">No tasks are due today</p> : null}
      {cards.map((card) => (
        <div key={card.key} className={`tv-card ${card.state}`}>
          <Icon name={card.icon} />
          <p className="tv-card-title">{card.title}</p>
          <p className="tv-card-detail">{card.detail}</p>
        </div>
      ))}
    </div>
  );

  const bar = (
    <div className="tv-progress">
      <span style={{ width: due.length ? `${(done / due.length) * 100}%` : "0%" }} />
    </div>
  );

  const buttons = (
    <div className="tv-buttons">
      <span className="tv-chip on">Theme</span>
      <span className="tv-chip">Time</span>
      <span className="tv-chip">Device</span>
      <span className="tv-chip">Note</span>
    </div>
  );

  if (theme === "night") {
    return (
      <div className="tv-screen night">
        <p className="tv-kicker">TV DESK · {(user ? user.name : "TV").toUpperCase()}</p>
        <p className="tv-bigclock">{withSeconds}</p>
        <p className="tv-date">{longDate(now)}</p>
        <p className="tv-count">{done} of {due.length} done · {days} days to {label}</p>
        <p className="tv-unlock">{unlock}</p>
      </div>
    );
  }

  if (theme === "agenda") {
    return (
      <div className="tv-screen agenda">
        {heading}
        {chips}
        {columns}
        {counter}
        <div className="tv-chiprow">
          {cards.map((card) => (
            <span key={card.key} className={`tv-taskchip ${card.state}`}>{card.title}</span>
          ))}
        </div>
        <p className="tv-unlock">{unlock}</p>
      </div>
    );
  }

  if (theme === "classic") {
    return (
      <div className="tv-screen classic">
        <div className="tv-head">
          <div>
            <p className="tv-kicker">TV DESK · {(user ? user.name : "TV").toUpperCase()}</p>
            <p className="tv-bigclock small">{withSeconds}</p>
            <p className="tv-date">{longDate(now)}</p>
          </div>
          <p className="tv-meta">--&#176; {settings.city || "Bothell"} · {days} days to {label}</p>
        </div>
        {chips}
        {columns}
        {counter}
        <div className="tv-chiprow">
          {cards.map((card) => (
            <span key={card.key} className={`tv-taskchip ${card.state}`}>{card.title}</span>
          ))}
        </div>
        {buttons}
        <p className="tv-unlock">{unlock}</p>
      </div>
    );
  }

  return (
    <div className="tv-screen cards">
      {heading}
      {chips}
      {columns}
      {counter}
      {tiles}
      {bar}
      <p className="tv-unlock">{unlock}</p>
    </div>
  );
}
