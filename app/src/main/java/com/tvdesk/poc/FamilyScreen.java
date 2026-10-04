package com.tvdesk.poc;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class FamilyScreen {
    interface Actions {
        void pick(String userId);
        void toggle(Board.Task task);
        void watch();
    }

    private FamilyScreen() {}

    static void paint(Activity activity, FrameLayout root, Board board, Board.User user, List<Board.Task> due,
                      int done, String screen, String today, Actions actions) {
        String post = "";
        String achievement = "";
        Board.Post update = user == null ? null : board.postFor(user.id, today);
        Board.Achievement badge = user == null ? null : board.achievementFor(user.id);
        if (update != null) {
            post = update.message;
        }
        if (badge != null) {
            achievement = badge.title;
        }
        String key = screen + "|" + (user == null ? "" : user.id) + "|" + done + "|" + due.size()
                + "|" + (user == null ? 0 : user.starsTowardReward) + "|" + post + "|" + achievement;
        if (key.equals(root.getTag())) {
            return;
        }
        View focused = activity.getCurrentFocus();
        String focusId = focused != null && focused.getTag() instanceof String ? (String) focused.getTag() : null;
        root.removeAllViews();
        root.setTag(key);
        View content = "profiles".equals(screen)
                ? profiles(activity, board, actions)
                : "done".equals(screen)
                ? celebration(activity, user, board, due, done, today, actions)
                : board(activity, user, board, due, done, today, actions);
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        View restore = focusId == null ? null : root.findViewWithTag(focusId);
        if (restore != null) {
            restore.requestFocus();
        } else {
            View first = firstFocus(root);
            if (first != null) {
                first.requestFocus();
            }
        }
    }

    private static View profiles(Activity activity, Board board, final Actions actions) {
        LinearLayout page = column(activity, 0xFFE4D3B0, 48);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title = text(activity, "Who's watching?", 42, 0xFF3A2E22, true);
        title.setGravity(Gravity.CENTER);
        TextView hint = text(activity, "Choose a profile", 18, 0xFF8A7260, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(activity, 6), 0, dp(activity, 28));
        page.addView(title);
        page.addView(hint);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < board.users.size(); i++) {
            final Board.User person = board.users.get(i);
            int complete = board.completed(board.dueFor(person.id, todayKey(activity)));
            int total = board.dueFor(person.id, todayKey(activity)).size();
            LinearLayout card = column(activity, 0xFFFFF8EE, 28);
            card.setTag("profile:" + person.id);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 280), dp(activity, 320));
            if (i > 0) {
                params.setMarginStart(dp(activity, 28));
            }
            card.setLayoutParams(params);
            card.setGravity(Gravity.CENTER);
            card.setFocusable(true);
            card.setClickable(true);
            card.setBackground(cardBackground(0xFFFFF8EE, false));
            TextView avatar = text(activity, initials(person.name), 36, 0xFF3A2E22, true);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(circle(i == 0 ? 0xFFF2C14E : 0xFF8ECAE6));
            LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(activity, 96), dp(activity, 96));
            avatarParams.bottomMargin = dp(activity, 18);
            avatar.setLayoutParams(avatarParams);
            TextView name = text(activity, person.name, 28, 0xFF3A2E22, true);
            name.setGravity(Gravity.CENTER);
            TextView progress = text(activity, complete + " of " + total + " done", 16, 0xFF8A7260, false);
            progress.setGravity(Gravity.CENTER);
            card.addView(avatar);
            card.addView(name);
            card.addView(progress);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    actions.pick(person.id);
                }
            });
            card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    v.setBackground(cardBackground(hasFocus ? 0xFFF6D34D : 0xFFFFF8EE, hasFocus));
                }
            });
            row.addView(card);
        }
        if (board.users.isEmpty()) {
            page.addView(text(activity, "Add a person on the web, then this screen will show their profile.", 20, 0xFF3A2E22, false));
        }
        page.addView(row);
        return page;
    }

    private static View board(Activity activity, Board.User user, Board board, List<Board.Task> due, int done,
                              String today, final Actions actions) {
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setBackgroundColor(0xFFE7D7B8);
        LinearLayout main = column(activity, 0x00000000, 28);
        main.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.7f));
        String name = user == null ? "there" : user.name;
        TextView greeting = text(activity, greeting(activity) + ", " + name, 34, 0xFF3A2E22, true);
        TextView clock = text(activity, clockText(activity), 22, 0xFF8A7260, false);
        clock.setTag("family-clock");
        clock.setGravity(Gravity.END);
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        greeting.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(greeting);
        header.addView(clock);
        TextView progressLabel = text(activity, done + " of " + due.size() + " done", 16, 0xFF8A7260, false);
        progressLabel.setPadding(0, dp(activity, 8), 0, dp(activity, 8));
        main.addView(header);
        main.addView(progressLabel);
        main.addView(progressBar(activity, due.size() == 0 ? 0 : done / (float) due.size()));
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(activity, 16);
        scroll.setLayoutParams(scrollParams);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        String prompt = "Press OK to mark done";
        boolean prompted = false;
        for (int i = 0; i < due.size(); i++) {
            final Board.Task task = due.get(i);
            boolean complete = board.isDone(task);
            boolean showPrompt = !complete && !prompted;
            if (showPrompt) {
                prompted = true;
            }
            list.addView(taskRow(activity, task, complete, showPrompt ? prompt : "", actions));
        }
        if (due.isEmpty()) {
            list.addView(text(activity, "No tasks today.", 22, 0xFF3A2E22, false));
        }
        scroll.addView(list);
        main.addView(scroll);
        page.addView(main);
        page.addView(sidebar(activity, user, board, due.size() - done, today));
        return page;
    }

    private static View taskRow(Activity activity, final Board.Task task, boolean complete, String prompt, final Actions actions) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setTag("task:" + task.id);
        row.setFocusable(true);
        row.setClickable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(activity, 10);
        row.setLayoutParams(params);
        row.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        row.setBackground(cardBackground(complete ? 0xFFE7F6EA : 0xFFFFF8EE, false));
        TextView mark = text(activity, complete ? "✓" : "", 18, complete ? 0xFFFFFFFF : 0xFF3A2E22, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(circle(complete ? 0xFF1E8A5A : 0xFFFFFFFF));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36));
        markParams.setMarginEnd(dp(activity, 14));
        mark.setLayoutParams(markParams);
        LinearLayout copy = new LinearLayout(activity);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView title = text(activity, task.label, 22, 0xFF3A2E22, true);
        if (complete) {
            title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }
        copy.addView(title);
        if (prompt.length() > 0) {
            copy.addView(text(activity, prompt, 14, 0xFF8A7260, false));
        }
        row.addView(mark);
        row.addView(copy);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.toggle(task);
            }
        });
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(cardBackground(hasFocus ? 0xFFF6D34D : (complete ? 0xFFE7F6EA : 0xFFFFF8EE), hasFocus));
            }
        });
        return row;
    }

    private static View sidebar(Activity activity, Board.User user, Board board, int remaining, String today) {
        LinearLayout side = column(activity, 0xFFF3E6CF, 22);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 320), LinearLayout.LayoutParams.MATCH_PARENT);
        params.setMarginStart(dp(activity, 8));
        side.setLayoutParams(params);
        TextView bear = text(activity, "🐻", 54, 0xFF3A2E22, false);
        bear.setGravity(Gravity.CENTER);
        String reward = remaining <= 0 ? "TV time is unlocked" : remaining + " more to unlock TV time";
        TextView rewardLine = text(activity, reward, 20, 0xFF3A2E22, true);
        rewardLine.setGravity(Gravity.CENTER);
        rewardLine.setPadding(0, dp(activity, 8), 0, dp(activity, 18));
        side.addView(bear);
        side.addView(rewardLine);
        Board.Achievement achievement = user == null ? null : board.achievementFor(user.id);
        if (achievement != null) {
            side.addView(infoCard(activity, "Achievement", symbol(achievement.icon) + " " + achievement.title, achievement.subtitle));
        }
        Board.Post post = user == null ? null : board.postFor(user.id, today);
        if (post != null) {
            side.addView(infoCard(activity, "Family update", symbol(post.badge) + " " + post.message, ""));
        }
        return side;
    }

    private static View celebration(Activity activity, Board.User user, Board board, List<Board.Task> due, int done,
                                    String today, final Actions actions) {
        LinearLayout page = column(activity, 0xFF1B6B3A, 48);
        page.setGravity(Gravity.CENTER);
        String name = user == null ? "" : user.name;
        TextView title = text(activity, "All done, " + name + "!", 40, 0xFFFFFFFF, true);
        title.setGravity(Gravity.CENTER);
        TextView line = text(activity, "TV time is unlocked", 22, 0xFFD7F5E2, false);
        line.setGravity(Gravity.CENTER);
        line.setPadding(0, dp(activity, 8), 0, dp(activity, 28));
        int stars = board.starsToday(user == null ? "" : user.id, today, due);
        LinearLayout stats = new LinearLayout(activity);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER);
        stats.addView(stat(activity, "+" + stars, "stars today"));
        stats.addView(stat(activity, String.valueOf(user == null ? 0 : user.streakCurrent), "day streak"));
        int target = user == null ? 0 : user.rewardTarget;
        int toward = user == null ? 0 : user.starsTowardReward;
        String rewardName = user == null ? "reward" : user.rewardTitle;
        stats.addView(stat(activity, toward + "/" + target, rewardName));
        TextView button = text(activity, "Start watching", 22, 0xFF1A1404, true);
        button.setTag("start-watching");
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setClickable(true);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(activity, 280), dp(activity, 64));
        buttonParams.topMargin = dp(activity, 28);
        button.setLayoutParams(buttonParams);
        button.setBackground(cardBackground(0xFFF6D34D, false));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actions.watch();
            }
        });
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackground(cardBackground(hasFocus ? 0xFFFFE58A : 0xFFF6D34D, hasFocus));
            }
        });
        page.addView(title);
        page.addView(line);
        page.addView(stats);
        page.addView(button);
        return page;
    }

    private static View stat(Activity activity, String value, String label) {
        LinearLayout card = column(activity, 0x332F8A4E, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(activity, 180), LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(activity, 12));
        card.setLayoutParams(params);
        card.setGravity(Gravity.CENTER);
        TextView amount = text(activity, value, 28, 0xFFFFFFFF, true);
        amount.setGravity(Gravity.CENTER);
        TextView caption = text(activity, label, 14, 0xFFD7F5E2, false);
        caption.setGravity(Gravity.CENTER);
        card.addView(amount);
        card.addView(caption);
        return card;
    }

    private static View infoCard(Activity activity, String label, String title, String subtitle) {
        LinearLayout card = column(activity, 0xFFFFF8EE, 14);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(activity, 12);
        card.setLayoutParams(params);
        card.addView(text(activity, label, 13, 0xFF8A7260, false));
        card.addView(text(activity, title, 18, 0xFF3A2E22, true));
        if (subtitle != null && subtitle.length() > 0) {
            card.addView(text(activity, subtitle, 14, 0xFF8A7260, false));
        }
        return card;
    }

    private static View progressBar(Activity activity, float fraction) {
        LinearLayout track = new LinearLayout(activity);
        track.setBackground(cardBackground(0xFFD7C6A4, false));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 10));
        track.setLayoutParams(trackParams);
        View fill = new View(activity);
        fill.setBackgroundColor(0xFF1E8A5A);
        track.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(fraction, 0.02f)));
        if (fraction < 1f) {
            track.addView(new View(activity), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1f - fraction, 0.02f)));
        }
        return track;
    }

    private static LinearLayout column(Activity activity, int color, int padding) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(activity, padding), dp(activity, padding), dp(activity, padding), dp(activity, padding));
        if ((color >>> 24) != 0) {
            layout.setBackgroundColor(color);
        }
        return layout;
    }

    private static TextView text(Activity activity, String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        if (Build.VERSION.SDK_INT >= 26) {
            view.setDefaultFocusHighlightEnabled(false);
        }
        return view;
    }

    private static GradientDrawable cardBackground(int color, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(28);
        if (focused) {
            drawable.setStroke(4, 0xFF3A2E22);
        }
        return drawable;
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private static View firstFocus(View root) {
        if (root.isFocusable()) {
            return root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = firstFocus(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        String value = "";
        for (int i = 0; i < parts.length && value.length() < 2; i++) {
            if (parts[i].length() > 0) {
                value += parts[i].substring(0, 1).toUpperCase(Locale.US);
            }
        }
        return value.length() == 0 ? "?" : value;
    }

    private static String symbol(String badge) {
        if ("fire".equals(badge)) {
            return "🔥";
        }
        if ("heart".equals(badge)) {
            return "♡";
        }
        if ("star".equals(badge)) {
            return "★";
        }
        return "";
    }

    private static String greeting(Activity activity) {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return "Good morning";
        }
        if (hour < 17) {
            return "Good afternoon";
        }
        return "Good evening";
    }

    private static String clockText(Activity activity) {
        boolean hour24 = DateFormat.is24HourFormat(activity);
        return new SimpleDateFormat(hour24 ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new Date());
    }

    private static String todayKey(Activity activity) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
