package com.tvdesk.poc;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Board {

    public static final class Task {
        public final String id;
        public final String userId;
        public final String label;
        public final String detail;
        public final String icon;
        public final int sortOrder;
        public final String cadence;
        public final int[] weekdays;
        public final int everyNDays;
        public final String anchorDate;
        public final String onDate;
        public final int goalCount;

        public Task(String id, String userId, String label, String detail, String icon, int sortOrder, String cadence,
                    int[] weekdays, int everyNDays, String anchorDate, String onDate, int goalCount) {
            this.id = id;
            this.userId = userId;
            this.label = label;
            this.detail = detail;
            this.icon = icon;
            this.sortOrder = sortOrder;
            this.cadence = cadence;
            this.weekdays = weekdays;
            this.everyNDays = everyNDays;
            this.anchorDate = anchorDate;
            this.onDate = onDate;
            this.goalCount = goalCount < 1 ? 1 : goalCount;
        }
    }

    public static final class Item {
        public final String id;
        public final String userId;
        public final String title;
        public final String timeLabel;
        public final int sortOrder;
        public final String taskId;
        public final String cadence;
        public final int[] weekdays;
        public final int everyNDays;
        public final String anchorDate;
        public final String onDate;
        public final String metaJson;

        public Item(String id, String userId, String title, String timeLabel, int sortOrder, String taskId, String cadence,
                    int[] weekdays, int everyNDays, String anchorDate, String onDate, String metaJson) {
            this.id = id;
            this.userId = userId;
            this.title = title;
            this.timeLabel = timeLabel;
            this.sortOrder = sortOrder;
            this.taskId = taskId;
            this.cadence = cadence;
            this.weekdays = weekdays;
            this.everyNDays = everyNDays;
            this.anchorDate = anchorDate;
            this.onDate = onDate;
            this.metaJson = metaJson == null || metaJson.length() == 0 ? "{}" : metaJson;
        }
    }

    public static final class User {
        public final String id;
        public final String name;
        public final int sortOrder;
        public final String rewardTitle;
        public final int rewardTarget;
        public final int starsTowardReward;
        public final int streakCurrent;
        public final int streakBest;

        public User(String id, String name, int sortOrder) {
            this(id, name, sortOrder, "Movie night", 50, 0, 0, 0);
        }

        public User(String id, String name, int sortOrder, String rewardTitle, int rewardTarget,
                    int starsTowardReward, int streakCurrent, int streakBest) {
            this.id = id;
            this.name = name == null || name.length() == 0 ? "User" : name;
            this.sortOrder = sortOrder;
            this.rewardTitle = rewardTitle == null || rewardTitle.length() == 0 ? "Movie night" : rewardTitle;
            this.rewardTarget = Math.max(rewardTarget, 0);
            this.starsTowardReward = Math.max(starsTowardReward, 0);
            this.streakCurrent = Math.max(streakCurrent, 0);
            this.streakBest = Math.max(streakBest, 0);
        }
    }

    public static final class Post {
        public final String id;
        public final String userId;
        public final String message;
        public final String badge;
        public final int bonusStars;
        public final String postedOn;
        public final String expiresOn;

        public Post(String id, String userId, String message, String badge, int bonusStars, String postedOn, String expiresOn) {
            this.id = id;
            this.userId = userId;
            this.message = message == null ? "" : message;
            this.badge = badge == null || badge.length() == 0 ? "none" : badge;
            this.bonusStars = Math.max(bonusStars, 0);
            this.postedOn = postedOn;
            this.expiresOn = expiresOn;
        }
    }

    public static final class Achievement {
        public final String id;
        public final String userId;
        public final String title;
        public final String subtitle;
        public final String icon;

        public Achievement(String id, String userId, String title, String subtitle, String icon) {
            this.id = id;
            this.userId = userId;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.icon = icon == null || icon.length() == 0 ? "star" : icon;
        }
    }

    public static final class Award {
        public final String id;
        public final String userId;
        public final String title;
        public final String message;
        public final int minutes;
        public final String icon;
        public final String showWhen;
        public final int minDone;
        public final int sortOrder;

        public Award(String id, String userId, String title, String message, int minutes, String icon,
                     String showWhen, int minDone, int sortOrder) {
            this.id = id;
            this.userId = userId;
            this.title = title == null ? "" : title;
            this.message = message == null ? "" : message;
            this.minutes = minutes;
            this.icon = icon == null || icon.length() == 0 ? "star" : icon;
            this.showWhen = showWhen == null || showWhen.length() == 0 ? "all_done" : showWhen;
            this.minDone = minDone;
            this.sortOrder = sortOrder;
        }
    }

    public static final class Settings {
        public final String activeUserId;
        public final String theme;
        public final String city;
        public final String countdownLabel;
        public final String countdownDate;
        public final String look;
        public final String experience;

        public Settings(String activeUserId, String theme, String city, String countdownLabel, String countdownDate, String look) {
            this(activeUserId, theme, city, countdownLabel, countdownDate, look, "family");
        }

        public Settings(String activeUserId, String theme, String city, String countdownLabel, String countdownDate, String look, String experience) {
            this.activeUserId = activeUserId;
            this.theme = "cards";
            this.city = city == null || city.length() == 0 ? "Bothell" : city;
            this.countdownLabel = countdownLabel == null || countdownLabel.length() == 0 ? "Weekend" : countdownLabel;
            this.countdownDate = countdownDate;
            this.look = Looks.canonical(look);
            this.experience = "cards".equals(experience) ? "cards" : "family";
        }
    }

    public final Settings settings;
    public final List<User> users;
    public final List<Task> tasks;
    public final List<Item> items;
    public final List<Award> awards;
    public final List<Post> posts;
    public final List<Achievement> achievements;
    public final Map<String, Integer> doneCounts;

    public Board(Settings settings, List<User> users, List<Task> tasks, List<Item> items, List<Award> awards,
                 Map<String, Integer> doneCounts) {
        this(settings, users, tasks, items, awards, doneCounts, new ArrayList<Post>(), new ArrayList<Achievement>());
    }

    public Board(Settings settings, List<User> users, List<Task> tasks, List<Item> items, List<Award> awards,
                 Map<String, Integer> doneCounts, List<Post> posts, List<Achievement> achievements) {
        this.settings = settings;
        this.users = users == null ? new ArrayList<User>() : users;
        this.tasks = tasks;
        this.items = items;
        this.awards = awards == null ? new ArrayList<Award>() : awards;
        this.posts = posts == null ? new ArrayList<Post>() : posts;
        this.achievements = achievements == null ? new ArrayList<Achievement>() : achievements;
        this.doneCounts = doneCounts == null ? new HashMap<String, Integer>() : doneCounts;
    }

    public static Board empty() {
        return new Board(new Settings(null, "cards", null, null, null, "navy"),
                new ArrayList<User>(), new ArrayList<Task>(), new ArrayList<Item>(),
                new ArrayList<Award>(), new HashMap<String, Integer>());
    }

    public User userById(String id) {
        if (id == null) {
            return null;
        }
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).id.equals(id)) {
                return users.get(i);
            }
        }
        return null;
    }

    public List<Award> earned(String userId, int done, int total) {
        List<Award> earned = new ArrayList<Award>();
        if (total <= 0 || done < total) {
            return earned;
        }
        for (int i = 0; i < awards.size(); i++) {
            Award award = awards.get(i);
            if (award.userId != null && !award.userId.equals(userId)) {
                continue;
            }
            if ("min_done".equals(award.showWhen) && done < award.minDone) {
                continue;
            }
            earned.add(award);
        }
        return earned;
    }

    public boolean taskDue(Task task, String day) {
        return occurs(task.cadence, task.weekdays, task.everyNDays, task.anchorDate, task.onDate, day);
    }

    public boolean itemDue(Item item, String day) {
        return occurs(item.cadence, item.weekdays, item.everyNDays, item.anchorDate, item.onDate, day);
    }

    public static boolean itemMarked(String metaJson, String day) {
        return doneDays(metaJson).contains(day);
    }

    public static String markItem(String metaJson, String day, boolean mark) {
        org.json.JSONObject meta;
        try {
            meta = new org.json.JSONObject(metaJson == null || metaJson.length() == 0 ? "{}" : metaJson);
        } catch (org.json.JSONException error) {
            meta = new org.json.JSONObject();
        }
        org.json.JSONArray next = new org.json.JSONArray();
        org.json.JSONArray days = meta.optJSONArray("done_days");
        if (days != null) {
            for (int i = 0; i < days.length(); i++) {
                String value = days.optString(i, "");
                if (value.length() > 0 && !value.equals(day)) {
                    next.put(value);
                }
            }
        }
        if (mark) {
            next.put(day);
        }
        try {
            meta.put("done_days", next);
        } catch (org.json.JSONException ignored) {
            return metaJson == null ? "{}" : metaJson;
        }
        return meta.toString();
    }

    private static java.util.HashSet<String> doneDays(String metaJson) {
        java.util.HashSet<String> found = new java.util.HashSet<String>();
        try {
            org.json.JSONObject meta = new org.json.JSONObject(metaJson == null || metaJson.length() == 0 ? "{}" : metaJson);
            org.json.JSONArray days = meta.optJSONArray("done_days");
            if (days == null) {
                return found;
            }
            for (int i = 0; i < days.length(); i++) {
                String value = days.optString(i, "");
                if (value.length() > 0) {
                    found.add(value);
                }
            }
        } catch (org.json.JSONException ignored) {
            return found;
        }
        return found;
    }

    public int countFor(String taskId) {
        Integer count = doneCounts.get(taskId);
        return count == null ? 0 : count.intValue();
    }

    public boolean isDone(Task task) {
        return countFor(task.id) >= task.goalCount;
    }

    public List<Task> dueFor(String userId, String day) {
        List<Task> due = new ArrayList<Task>();
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            if (userId != null && userId.equals(task.userId) && taskDue(task, day)) {
                due.add(task);
            }
        }
        return due;
    }

    public int completed(List<Task> due) {
        int count = 0;
        for (int i = 0; i < due.size(); i++) {
            if (isDone(due.get(i))) {
                count++;
            }
        }
        return count;
    }

    public Post postFor(String userId, String today) {
        Post match = null;
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            if (post.userId != null && !post.userId.equals(userId)) {
                continue;
            }
            if (post.expiresOn != null && post.expiresOn.length() > 0 && post.expiresOn.compareTo(today) < 0) {
                continue;
            }
            match = post;
        }
        return match;
    }

    public Achievement achievementFor(String userId) {
        Achievement match = null;
        for (int i = 0; i < achievements.size(); i++) {
            Achievement item = achievements.get(i);
            if (userId != null && userId.equals(item.userId)) {
                match = item;
            }
        }
        return match;
    }

    public int starsToday(String userId, String today, List<Task> due) {
        int stars = completed(due);
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            if (post.bonusStars <= 0 || post.postedOn == null || !post.postedOn.equals(today)) {
                continue;
            }
            if (post.userId == null || post.userId.equals(userId)) {
                stars += post.bonusStars;
            }
        }
        return stars;
    }

    public static boolean occurs(String cadence, int[] weekdays, int everyNDays, String anchorDate, String onDate, String day) {
        if ("daily".equals(cadence)) {
            return true;
        }
        if ("once".equals(cadence)) {
            return day.equals(onDate);
        }
        if ("weekly".equals(cadence)) {
            int dow = weekday(day);
            if (weekdays == null) {
                return false;
            }
            for (int i = 0; i < weekdays.length; i++) {
                if (weekdays[i] == dow) {
                    return true;
                }
            }
            return false;
        }
        if ("periodic".equals(cadence)) {
            if (everyNDays < 1 || anchorDate == null || anchorDate.length() == 0) {
                return false;
            }
            int diff = dayNumber(day) - dayNumber(anchorDate);
            return diff >= 0 && diff % everyNDays == 0;
        }
        return false;
    }

    public static int weekday(String day) {
        Calendar calendar = calendarOf(day);
        return calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
    }

    public static int dayNumber(String day) {
        Calendar calendar = calendarOf(day);
        long millis = calendar.getTimeInMillis();
        return (int) (millis / 86400000L);
    }

    private static Calendar calendarOf(String day) {
        String[] parts = day.split("-");
        Calendar calendar = Calendar.getInstance(Locale.US);
        calendar.set(Calendar.YEAR, Integer.parseInt(parts[0]));
        calendar.set(Calendar.MONTH, Integer.parseInt(parts[1]) - 1);
        calendar.set(Calendar.DAY_OF_MONTH, Integer.parseInt(parts[2]));
        calendar.set(Calendar.HOUR_OF_DAY, 12);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }
}
