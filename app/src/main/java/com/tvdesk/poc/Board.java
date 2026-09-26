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

        public Item(String id, String userId, String title, String timeLabel, int sortOrder, String taskId, String cadence,
                    int[] weekdays, int everyNDays, String anchorDate, String onDate) {
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
        }
    }

    public static final class User {
        public final String id;
        public final String name;
        public final int sortOrder;

        public User(String id, String name, int sortOrder) {
            this.id = id;
            this.name = name == null || name.length() == 0 ? "User" : name;
            this.sortOrder = sortOrder;
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

        public Settings(String activeUserId, String theme, String city, String countdownLabel, String countdownDate) {
            this.activeUserId = activeUserId;
            this.theme = theme == null || theme.length() == 0 ? "cards" : theme;
            this.city = city == null || city.length() == 0 ? "Bothell" : city;
            this.countdownLabel = countdownLabel == null || countdownLabel.length() == 0 ? "SAT" : countdownLabel;
            this.countdownDate = countdownDate;
        }
    }

    public final Settings settings;
    public final List<User> users;
    public final List<Task> tasks;
    public final List<Item> items;
    public final List<Award> awards;
    public final Map<String, Integer> doneCounts;

    public Board(Settings settings, List<User> users, List<Task> tasks, List<Item> items, List<Award> awards,
                 Map<String, Integer> doneCounts) {
        this.settings = settings;
        this.users = users == null ? new ArrayList<User>() : users;
        this.tasks = tasks;
        this.items = items;
        this.awards = awards == null ? new ArrayList<Award>() : awards;
        this.doneCounts = doneCounts == null ? new HashMap<String, Integer>() : doneCounts;
    }

    public static Board empty() {
        return new Board(new Settings(null, "cards", null, null, null),
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

    public int countFor(String taskId) {
        Integer count = doneCounts.get(taskId);
        return count == null ? 0 : count.intValue();
    }

    public boolean isDone(Task task) {
        return countFor(task.id) >= task.goalCount;
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
