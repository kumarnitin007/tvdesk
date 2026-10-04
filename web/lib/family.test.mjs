import test from "node:test";
import assert from "node:assert/strict";
import { activeFor, applyFrequency, expiresOn, frequencyOf, starsToward, streakEnding, syncTaskStar, taskPayload } from "./family.mjs";

test("frequency presets map onto the existing cadence rules", () => {
  assert.equal(frequencyOf({ cadence: "daily", weekdays: [] }), "daily");
  assert.equal(frequencyOf({ cadence: "weekly", weekdays: [1, 2, 3, 4, 5] }), "weekdays");
  assert.equal(frequencyOf({ cadence: "weekly", weekdays: [1] }), "weekly");
  assert.equal(frequencyOf({ cadence: "periodic", weekdays: [] }), "custom");
  assert.equal(frequencyOf({ cadence: "once", weekdays: [] }), "custom");

  const weekdays = applyFrequency({ cadence: "daily", weekdays: [] }, "weekdays", "2026-10-03");
  assert.deepEqual(weekdays.weekdays, [1, 2, 3, 4, 5]);
  const payload = taskPayload({ ...weekdays, label: " Maths ", detail: "", icon: "star", every_n_days: 2, goal_count: 0 }, "kid", 3);
  assert.equal(payload.cadence, "weekly");
  assert.equal(payload.goal_count, 1);
  assert.equal(payload.every_n_days, null);
});

test("post expiry is inclusive for today and three days", () => {
  assert.equal(expiresOn("2026-10-03", "today"), "2026-10-03");
  assert.equal(expiresOn("2026-10-03", "3_days"), "2026-10-05");
  assert.equal(expiresOn("2026-10-03", "until_removed"), null);
});

test("a post is visible to its child and to everyone until it expires", () => {
  const post = { active: true, removed_at: null, user_id: "kid-1", expires_on: "2026-10-05" };
  assert.equal(activeFor(post, "kid-1", "2026-10-05"), true);
  assert.equal(activeFor(post, "kid-2", "2026-10-05"), false);
  assert.equal(activeFor({ ...post, user_id: null }, "kid-2", "2026-10-05"), true);
  assert.equal(activeFor(post, "kid-1", "2026-10-06"), false);
  assert.equal(activeFor({ ...post, removed_at: "2026-10-04" }, "kid-1", "2026-10-04"), false);
});

test("a completed task awards one star and repeating the completion does not add another", () => {
  const once = syncTaskStar([], { userId: "kid", taskId: "maths", day: "2026-10-03", done: true });
  const twice = syncTaskStar(once, { userId: "kid", taskId: "maths", day: "2026-10-03", done: true });
  assert.equal(twice.length, 1);
  assert.equal(starsToward(twice), 1);
  assert.deepEqual(syncTaskStar(twice, { userId: "kid", taskId: "maths", day: "2026-10-03", done: false }), []);
});

test("streak counts consecutive days through today or yesterday", () => {
  assert.equal(streakEnding(["2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02", "2026-10-03"], "2026-10-03"), 6);
  assert.equal(streakEnding(["2026-10-01", "2026-10-03"], "2026-10-03"), 1);
  assert.equal(streakEnding(["2026-10-01"], "2026-10-03"), 0);
});

test("redeeming spends the target and keeps stars above it", () => {
  const rows = [
    { stars: 42, reason: "task_complete" },
    { stars: -50, reason: "redeem" },
  ];
  assert.equal(starsToward([{ stars: 42, reason: "task_complete" }]), 42);
  assert.equal(starsToward(rows), 0);
  assert.equal(starsToward([{ stars: 62, reason: "task_complete" }, { stars: -50, reason: "redeem" }]), 12);
});
