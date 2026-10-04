package com.tvdesk.poc;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String PREFS = "tvdesk_tasks";
    private static final String KEY_LOOK = "look";
    private static final String KEY_USER = "user";
    private static final long POLL_MS = 15000L;
    private static final long WEATHER_MS = 20L * 60L * 1000L;

    private final long openedAt = SystemClock.elapsedRealtime();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            renderClock();
            handler.postDelayed(this, 1000L);
        }
    };
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            refresh(false);
            handler.postDelayed(this, POLL_MS);
        }
    };

    private DeskStore store;
    private SharedPreferences prefs;
    private Board board = Board.empty();
    private String look = "navy";
    private String selectedUserId;
    private long userLockedUntil = 0L;
    private boolean forceTasks = false;
    private String weatherText = "";
    private long weatherAt = 0L;
    private long lookLockedUntil = 0L;
    private boolean sawBoard = false;
    private int clicks = 0;

    private TextView nameView;
    private TextView greetingView;
    private TextView dateView;
    private TextView clockView;
    private TextView metaView;
    private TextView weatherView;
    private TextView countdownView;
    private TextView doneCount;
    private TextView unlockLine;
    private TextView statusLine;
    private View progressDone;
    private View progressRest;
    private LinearLayout todayList;
    private LinearLayout tomorrowList;
    private LinearLayout taskRow;
    private LinearLayout userRow;
    private TextView sectionLabel;
    private Button btnTasks;
    private Button btnView;
    private View screen;
    private View todayPanel;
    private View tomorrowPanel;
    private TextView headingToday;
    private TextView headingTomorrow;
    private boolean familyMode = true;
    private String layoutMode = "";
    private FrameLayout familyRoot;
    private boolean profileChosen;
    private String familyScreen = "profiles";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new DeskStore(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, BuildConfig.OPENWEATHER_API_KEY);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        look = Looks.canonical(prefs.getString(KEY_LOOK, "navy"));
        selectedUserId = prefs.getString(KEY_USER, null);
        showExperience(true);
        renderCurrent();
        renderClock();
        if (statusLine != null) {
            statusLine.setText(R.string.status_loading);
        }
    }

    @Override
    public void onBackPressed() {
        if (familyMode && "done".equals(familyScreen)) {
            dismissCelebration();
            return;
        }
        if (familyMode && profileChosen) {
            profileChosen = false;
            familyScreen = "profiles";
            renderFamily();
            return;
        }
        super.onBackPressed();
    }

    private void showExperience(boolean family) {
        String mode = family ? "family" : "cards";
        if (mode.equals(layoutMode)) {
            return;
        }
        layoutMode = mode;
        familyMode = family;
        if (family) {
            setContentView(R.layout.activity_family);
            familyRoot = findViewById(R.id.familyRoot);
            taskRow = null;
            statusLine = null;
            dateView = null;
            return;
        }
        familyRoot = null;
        setContentView(R.layout.activity_cards);
        bind();
        applyLook();
    }

    private void renderCurrent() {
        if (familyMode) {
            renderFamily();
            return;
        }
        renderBoard();
    }

    private void renderFamily() {
        if (familyRoot == null) {
            return;
        }
        final String today = todayKey();
        Board.User user = board.userById(selectedUserId);
        if (user == null && !board.users.isEmpty()) {
            user = board.users.get(0);
            selectedUserId = user.id;
        }
        final List<Board.Task> due = user == null ? new ArrayList<Board.Task>() : board.dueFor(user.id, today);
        int done = board.completed(due);
        boolean allDone = !due.isEmpty() && done == due.size();
        String token = user == null ? "" : user.id + ":" + today;
        boolean hidden = token.equals(prefs.getString("celebrate", ""));
        if (!allDone && hidden) {
            prefs.edit().remove("celebrate").apply();
            hidden = false;
        }
        String screen = "profiles";
        if (profileChosen && user != null) {
            screen = allDone && !hidden ? "done" : "board";
        }
        familyScreen = screen;
        final Board.User person = user;
        FamilyScreen.paint(this, familyRoot, board, person, due, done, screen, today, new FamilyScreen.Actions() {
            @Override
            public void pick(String userId) {
                chooseFamily(userId);
            }

            @Override
            public void toggle(Board.Task task) {
                MainActivity.this.toggle(task);
            }

            @Override
            public void watch() {
                dismissCelebration();
            }
        });
    }

    private void chooseFamily(final String userId) {
        selectedUserId = userId;
        profileChosen = true;
        familyScreen = "board";
        forceTasks = false;
        userLockedUntil = SystemClock.elapsedRealtime() + 8000L;
        prefs.edit().putString(KEY_USER, userId).apply();
        if (familyRoot != null) {
            familyRoot.setTag(null);
        }
        renderFamily();
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    store.setActiveUser(userId);
                } catch (Exception ignored) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (statusLine != null) {
                                statusLine.setText(R.string.status_save_failed);
                            }
                        }
                    });
                }
            }
        });
    }

    private void dismissCelebration() {
        if (selectedUserId != null) {
            prefs.edit().putString("celebrate", selectedUserId + ":" + todayKey()).apply();
        }
        familyScreen = "board";
        if (familyRoot != null) {
            familyRoot.setTag(null);
        }
        renderFamily();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.post(tick);
        handler.removeCallbacks(poll);
        handler.post(poll);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(poll);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private void bind() {
        nameView = find(R.id.nameView);
        greetingView = find(R.id.greetingView);
        dateView = find(R.id.dateView);
        clockView = find(R.id.clockView);
        metaView = find(R.id.metaView);
        weatherView = find(R.id.weatherView);
        countdownView = find(R.id.countdownView);
        doneCount = find(R.id.doneCount);
        unlockLine = find(R.id.unlockLine);
        statusLine = find(R.id.statusLine);
        progressDone = findViewById(R.id.progressDone);
        progressRest = findViewById(R.id.progressRest);
        todayList = findViewById(R.id.todayList);
        tomorrowList = findViewById(R.id.tomorrowList);
        taskRow = findViewById(R.id.taskRow);
        userRow = findViewById(R.id.userRow);
        sectionLabel = find(R.id.sectionLabel);
        btnTasks = findViewById(R.id.btnTasks);
        btnView = findViewById(R.id.btnView);
        screen = findViewById(R.id.screen);
        todayPanel = findViewById(R.id.todayPanel);
        tomorrowPanel = findViewById(R.id.tomorrowPanel);
        headingToday = find(R.id.headingToday);
        headingTomorrow = find(R.id.headingTomorrow);
        if (btnView != null) {
            btnView.setText(Looks.of(look).label);
            btnView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cycleLook();
                }
            });
            growOnFocus(btnView);
        }
        if (btnTasks != null) {
            growOnFocus(btnTasks);
        }
        wireInfo(R.id.btnTime, new Runnable() {
            @Override
            public void run() {
                showTime();
            }
        });
        wireInfo(R.id.btnDevice, new Runnable() {
            @Override
            public void run() {
                showDevice();
            }
        });
        wireInfo(R.id.btnNote, new Runnable() {
            @Override
            public void run() {
                showNote();
            }
        });
    }

    private void refresh(final boolean announce) {
        final String today = todayKey();
        final boolean needWeather = SystemClock.elapsedRealtime() - weatherAt > WEATHER_MS;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Board next = store.fetch(today);
                    String temp = weatherText;
                    if (needWeather) {
                        try {
                            temp = store.fetchTemperature(next.settings.city);
                        } catch (Exception ignored) {
                            temp = weatherText;
                        }
                    }
                    final String temperature = temp;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (temperature != null && temperature.length() > 0) {
                                weatherText = temperature;
                                weatherAt = SystemClock.elapsedRealtime();
                            }
                            board = next;
                            sawBoard = true;
                            if (SystemClock.elapsedRealtime() > userLockedUntil && next.settings.activeUserId != null) {
                                selectedUserId = next.settings.activeUserId;
                            }
                            if (next.userById(selectedUserId) == null && !next.users.isEmpty()) {
                                selectedUserId = next.users.get(0).id;
                            }
                            if (SystemClock.elapsedRealtime() > lookLockedUntil
                                    && !look.equals(next.settings.look)) {
                                look = next.settings.look;
                                prefs.edit().putString(KEY_LOOK, look).apply();
                                if (!familyMode) {
                                    applyLook();
                                }
                            }
                            boolean family = !"cards".equals(next.settings.experience);
                            if (family != familyMode) {
                                showExperience(family);
                            }
                            renderCurrent();
                            renderClock();
                            if (statusLine != null && (announce || statusIs(R.string.status_loading) || statusIs(R.string.status_offline) || statusIs(R.string.status_board_error))) {
                                statusLine.setText(R.string.status_live);
                            }
                        }
                    });
                } catch (Exception error) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (statusLine != null) {
                                statusLine.setText(sawBoard ? R.string.status_offline : R.string.status_board_error);
                            }
                        }
                    });
                }
            }
        });
    }

    private void cycleLook() {
        int index = 0;
        for (int i = 0; i < Looks.IDS.length; i++) {
            if (Looks.IDS[i].equals(look)) {
                index = (i + 1) % Looks.IDS.length;
            }
        }
        final String next = Looks.IDS[index];
        look = next;
        lookLockedUntil = SystemClock.elapsedRealtime() + 8000L;
        prefs.edit().putString(KEY_LOOK, look).apply();
        applyLook();
        if (btnView != null) {
            btnView.setText(Looks.of(look).label);
        }
        statusLine.setText(Looks.of(next).label + " background");
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    store.setLook(next);
                } catch (Exception ignored) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusLine.setText(R.string.status_save_failed);
                        }
                    });
                }
            }
        });
    }

    private void renderBoard() {
        if (taskRow == null) {
            return;
        }
        String today = todayKey();
        String tomorrow = shiftDay(1);
        Board.User user = currentUser();
        if (user != null) {
            selectedUserId = user.id;
        }
        String focusedId = focusedTaskId();
        boolean keepCurrentFocus = focusedId == null && getCurrentFocus() != null;

        nameView.setText("TV DESK  ·  " + (user == null ? "TV" : user.name.toUpperCase(Locale.US)));
        if (greetingView != null) {
            greetingView.setText(greeting());
        }
        fillUsers(user);
        fillSchedule(todayList, today, true, user);
        fillSchedule(tomorrowList, tomorrow, false, user);
        fillTasks(today, user, focusedId, keepCurrentFocus);
        if (btnView != null) {
            btnView.setText(Looks.of(look).label);
        }
    }

    private void fillSchedule(LinearLayout list, String day, boolean canMark, Board.User user) {
        String focusedId = null;
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof String) {
            focusedId = (String) focused.getTag();
        }
        list.removeAllViews();
        View restore = null;
        for (int i = 0; i < board.items.size(); i++) {
            final Board.Item item = board.items.get(i);
            if (user != null && item.userId != null && !item.userId.equals(user.id)) {
                continue;
            }
            if (!board.itemDue(item, day)) {
                continue;
            }
            View row = getLayoutInflater().inflate(R.layout.schedule_row, list, false);
            TextView time = row.findViewById(R.id.rowTime);
            TextView title = row.findViewById(R.id.rowTitle);
            TextView status = row.findViewById(R.id.rowStatus);
            time.setText(item.timeLabel);
            title.setText(item.title);
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            final boolean done = canMark && Board.itemMarked(item.metaJson, day);
            title.setPaintFlags(done ? title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG : title.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            status.setVisibility(done ? View.VISIBLE : View.INVISIBLE);
            Looks palette = Looks.of(look);
            time.setTextColor(palette.muted);
            title.setTextColor(palette.text);
            if (canMark) {
                row.setTag(item.id);
                row.setFocusable(true);
                row.setClickable(true);
                if (Build.VERSION.SDK_INT >= 26) {
                    row.setDefaultFocusHighlightEnabled(false);
                }
                paintScheduleRow(row, done, false);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleItem(item, day);
                    }
                });
                row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        paintScheduleRow(v, Board.itemMarked(item.metaJson, day), hasFocus);
                    }
                });
                if (item.id.equals(focusedId)) {
                    restore = row;
                }
            }
            list.addView(row);
        }
        if (restore != null) {
            restore.requestFocus();
        }
    }

    private void paintScheduleRow(View row, boolean done, boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(8));
        if (focused) {
            background.setColor(0x66F5C542);
        } else if (done) {
            background.setColor(0x332E8A62);
        } else {
            background.setColor(0x00000000);
        }
        row.setBackground(background);
    }

    private void toggleItem(final Board.Item item, final String day) {
        final boolean nowDone = !Board.itemMarked(item.metaJson, day);
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    store.setItemMarked(item.id, item.metaJson, day, nowDone);
                    final Board next = store.fetch(day);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            board = next;
                            sawBoard = true;
                            renderBoard();
                            statusLine.setText(getString(nowDone ? R.string.marked_done : R.string.marked_open, item.title));
                        }
                    });
                } catch (Exception ignored) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusLine.setText(R.string.status_save_failed);
                        }
                    });
                }
            }
        });
    }

    private void fillUsers(final Board.User current) {
        if (userRow == null) {
            return;
        }
        userRow.removeAllViews();
        for (int i = 0; i < board.users.size(); i++) {
            final Board.User user = board.users.get(i);
            Button button = new Button(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1f);
            if (i < board.users.size() - 1) {
                params.setMarginEnd(dp(10));
            }
            button.setLayoutParams(params);
            button.setAllCaps(false);
            button.setFocusable(true);
            button.setText(user.name);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            paintChip(button, current != null && user.id.equals(current.id));
            if (Build.VERSION.SDK_INT >= 26) {
                button.setDefaultFocusHighlightEnabled(false);
            }
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chooseUser(user.id);
                }
            });
            growOnFocus(button);
            userRow.addView(button);
        }
    }

    private void chooseUser(final String userId) {
        if (userId.equals(selectedUserId)) {
            return;
        }
        selectedUserId = userId;
        forceTasks = false;
        userLockedUntil = SystemClock.elapsedRealtime() + 8000L;
        prefs.edit().putString(KEY_USER, userId).apply();
        renderBoard();
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    store.setActiveUser(userId);
                } catch (Exception ignored) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusLine.setText(R.string.status_save_failed);
                        }
                    });
                }
            }
        });
    }

    private Board.User currentUser() {
        Board.User found = board.userById(selectedUserId);
        if (found != null) {
            return found;
        }
        if (!board.users.isEmpty()) {
            return board.users.get(0);
        }
        return null;
    }

    private void fillTasks(String today, Board.User user, String focusedId, boolean keepCurrentFocus) {
        taskRow.removeAllViews();
        List<Board.Task> due = new ArrayList<Board.Task>();
        int done = 0;
        for (int i = 0; i < board.tasks.size(); i++) {
            Board.Task task = board.tasks.get(i);
            if (user != null && task.userId != null && !task.userId.equals(user.id)) {
                continue;
            }
            if (!board.taskDue(task, today)) {
                continue;
            }
            due.add(task);
            if (board.isDone(task)) {
                done++;
            }
        }
        boolean allDone = !due.isEmpty() && done == due.size();
        if (!allDone) {
            forceTasks = false;
        }
        List<Board.Award> awards = user == null
                ? new ArrayList<Board.Award>()
                : board.earned(user.id, done, due.size());
        boolean awardMode = allDone && !forceTasks && !awards.isEmpty();
        if (sectionLabel != null) {
            sectionLabel.setText(awardMode ? R.string.awards : R.string.mark_done);
        }
        if (btnTasks != null) {
            btnTasks.setVisibility(allDone ? View.VISIBLE : View.GONE);
            btnTasks.setText(awardMode ? R.string.show_tasks : R.string.show_awards);
            btnTasks.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    forceTasks = !forceTasks;
                    renderBoard();
                }
            });
        }
        boolean cards = true;
        View restore = null;
        View firstOpen = null;
        if (awardMode) {
            for (int i = 0; i < awards.size(); i++) {
                taskRow.addView(makeAward(awards.get(i), cards, i == awards.size() - 1));
            }
        } else {
            for (int i = 0; i < due.size(); i++) {
                Board.Task task = due.get(i);
                boolean isDone = board.isDone(task);
                View view = cards ? makeCard(task, isDone, i == due.size() - 1) : makeChip(task, isDone, i == due.size() - 1);
                taskRow.addView(view);
                if (task.id.equals(focusedId)) {
                    restore = view;
                }
                if (firstOpen == null && !isDone) {
                    firstOpen = view;
                }
            }
        }
        doneCount.setText(getString(R.string.done_count, done, due.size()));
        if (unlockLine != null) {
            if (awardMode) {
                unlockLine.setText(awards.get(0).message);
            } else if (due.isEmpty()) {
                unlockLine.setText(R.string.unlock_empty);
            } else if (allDone) {
                unlockLine.setText(R.string.unlock_done);
            } else if (due.size() - done == 1) {
                unlockLine.setText(R.string.unlock_one);
            } else {
                unlockLine.setText(getString(R.string.unlock_remaining, due.size() - done));
            }
        }
        if (progressDone != null && progressRest != null) {
            LinearLayout.LayoutParams doneParams = (LinearLayout.LayoutParams) progressDone.getLayoutParams();
            LinearLayout.LayoutParams restParams = (LinearLayout.LayoutParams) progressRest.getLayoutParams();
            doneParams.weight = done;
            restParams.weight = Math.max(due.size() - done, due.isEmpty() ? 1 : 0);
            progressDone.setLayoutParams(doneParams);
            progressRest.setLayoutParams(restParams);
        }
        if (restore != null) {
            restore.requestFocus();
        } else if (!keepCurrentFocus && !awardMode) {
            View target = firstOpen != null ? firstOpen : (taskRow.getChildCount() > 0 ? taskRow.getChildAt(0) : btnView);
            if (target != null) {
                target.requestFocus();
            }
        } else if (!keepCurrentFocus && awardMode && taskRow.getChildCount() > 0) {
            taskRow.getChildAt(0).requestFocus();
        }
    }

    private View makeAward(Board.Award award, boolean cards, boolean last) {
        if (!cards) {
            Button button = new Button(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(72), 1f);
            if (!last) {
                params.setMarginEnd(dp(12));
            }
            button.setLayoutParams(params);
            button.setAllCaps(false);
            button.setFocusable(true);
            button.setText(award.message);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            paintChip(button, true);
            if (Build.VERSION.SDK_INT >= 26) {
                button.setDefaultFocusHighlightEnabled(false);
            }
            growOnFocus(button);
            return button;
        }
        View card = getLayoutInflater().inflate(R.layout.task_card, taskRow, false);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
        if (!last) {
            params.setMarginEnd(dp(12));
        }
        card.setLayoutParams(params);
        card.setFocusable(true);
        ((TextView) card.findViewById(R.id.cardTitle)).setText(award.title.length() == 0 ? "Award" : award.title);
        ((TextView) card.findViewById(R.id.cardDetail)).setText(award.message);
        ImageView icon = card.findViewById(R.id.cardIcon);
        icon.setImageResource(iconRes(award.icon));
        card.setBackgroundResource(R.drawable.task_focus);
        int ink = getResources().getColor(R.color.ink);
        ((TextView) card.findViewById(R.id.cardTitle)).setTextColor(ink);
        ((TextView) card.findViewById(R.id.cardDetail)).setTextColor(ink);
        icon.setColorFilter(ink, PorterDuff.Mode.SRC_IN);
        if (Build.VERSION.SDK_INT >= 26) {
            card.setDefaultFocusHighlightEnabled(false);
        }
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.animate().scaleX(hasFocus ? 1.03f : 1f).scaleY(hasFocus ? 1.03f : 1f).setDuration(120).start();
            }
        });
        return card;
    }

    private View makeCard(final Board.Task task, final boolean done, boolean last) {
        final View card = getLayoutInflater().inflate(R.layout.task_card, taskRow, false);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
        if (!last) {
            params.setMarginEnd(dp(12));
        }
        card.setLayoutParams(params);
        card.setTag(new TaskRef(task.id, done));
        if (Build.VERSION.SDK_INT >= 26) {
            card.setDefaultFocusHighlightEnabled(false);
        }
        ((TextView) card.findViewById(R.id.cardTitle)).setText(task.label);
        ((TextView) card.findViewById(R.id.cardDetail)).setText(detailFor(task, done));
        ImageView icon = card.findViewById(R.id.cardIcon);
        icon.setImageResource(iconRes(task.icon));
        styleCard(card, false);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle(task);
            }
        });
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                styleCard(v, hasFocus);
                v.animate().scaleX(hasFocus ? 1.03f : 1f).scaleY(hasFocus ? 1.03f : 1f).setDuration(120).start();
            }
        });
        return card;
    }

    private void styleCard(View card, boolean focused) {
        TaskRef ref = (TaskRef) card.getTag();
        if (focused) {
            card.setBackgroundResource(R.drawable.task_focus);
        } else if (ref.done) {
            card.setBackgroundResource(R.drawable.task_done_fill);
        } else {
            GradientDrawable idle = new GradientDrawable();
            idle.setColor(Looks.of(look).idle);
            idle.setCornerRadius(dp(18));
            card.setBackground(idle);
        }
        Looks palette = Looks.of(look);
        int titleColor = palette.text;
        int detailColor = palette.muted;
        if (focused) {
            titleColor = getResources().getColor(R.color.ink);
            detailColor = titleColor;
        } else if (ref.done) {
            titleColor = getResources().getColor(R.color.text);
            detailColor = getResources().getColor(R.color.muted);
        }
        ((TextView) card.findViewById(R.id.cardTitle)).setTextColor(titleColor);
        ((TextView) card.findViewById(R.id.cardDetail)).setTextColor(detailColor);
        ImageView icon = card.findViewById(R.id.cardIcon);
        icon.setColorFilter(titleColor, PorterDuff.Mode.SRC_IN);
    }

    private Button makeChip(final Board.Task task, boolean done, boolean last) {
        Button button = new Button(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(64), 1f);
        if (!last) {
            params.setMarginEnd(dp(12));
        }
        button.setLayoutParams(params);
        button.setTag(new TaskRef(task.id, done));
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        button.setText(done ? getString(R.string.task_done_label, task.label) : task.label);
        paintChip(button, done);
        if (Build.VERSION.SDK_INT >= 26) {
            button.setDefaultFocusHighlightEnabled(false);
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggle(task);
            }
        });
        growOnFocus(button);
        return button;
    }

    private void paintChip(Button button, boolean done) {
        button.setBackgroundResource(done ? R.drawable.button_done_bg : R.drawable.button_bg);
        int colorRes = done ? R.color.button_done_text : R.color.button_text;
        ColorStateList colors = Build.VERSION.SDK_INT >= 23
                ? getColorStateList(colorRes)
                : getResources().getColorStateList(colorRes);
        button.setTextColor(colors);
    }

    private void toggle(final Board.Task task) {
        final String today = todayKey();
        final boolean nowDone = !board.isDone(task);
        final int count = nowDone ? task.goalCount : 0;
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    store.setDone(task.id, today, count);
                    final Board next = store.fetch(today);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            board = next;
                            sawBoard = true;
                            renderCurrent();
                            if (statusLine != null) {
                                statusLine.setText(getString(nowDone ? R.string.marked_done : R.string.marked_open, task.label));
                            }
                        }
                    });
                } catch (Exception error) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (statusLine != null) {
                                statusLine.setText(R.string.status_save_failed);
                            }
                        }
                    });
                }
            }
        });
    }

    private void renderClock() {
        if (familyRoot != null) {
            TextView familyClock = familyRoot.findViewWithTag("family-clock");
            if (familyClock != null) {
                boolean hour24 = DateFormat.is24HourFormat(this);
                familyClock.setText(new SimpleDateFormat(hour24 ? "HH:mm" : "h:mm a", Locale.getDefault()).format(new Date()));
            }
        }
        if (dateView == null) {
            return;
        }
        Date now = new Date();
        boolean hour24 = DateFormat.is24HourFormat(this);
        String pattern = hour24 ? "HH:mm" : "h:mm a";
        String time = new SimpleDateFormat(pattern, Locale.getDefault()).format(now);
        dateView.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now));
        if (greetingView != null) {
            greetingView.setText(greeting());
        }
        if (clockView != null) {
            if (!hour24 && time.contains(" ")) {
                clockView.setText(time.replace(" ", "\n"));
            } else {
                clockView.setText(time);
            }
        }
        if (weatherView != null) {
            String city = board.settings.city.toUpperCase(Locale.US);
            weatherView.setText((weatherText.length() == 0 ? "--" : weatherText) + "\n" + city);
        }
        if (countdownView != null) {
            countdownView.setText(countdownText());
        }
        if (metaView != null) {
            long elapsed = (SystemClock.elapsedRealtime() - openedAt) / 1000L;
            String openFor = String.format(Locale.getDefault(), "%d:%02d", elapsed / 60L, elapsed % 60L);
            String extra = "";
            if (weatherText.length() > 0) {
                extra = "   ·   " + weatherText + " " + board.settings.city;
            }
            metaView.setText("Open " + openFor + extra + "   ·   " + countdownText().replace("\n", " "));
        }
    }

    private String countdownText() {
        String today = todayKey();
        String target = board.settings.countdownDate;
        if (target == null) {
            Calendar calendar = Calendar.getInstance();
            int add = (Calendar.SATURDAY - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7;
            calendar.add(Calendar.DATE, add);
            target = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
        }
        int days = Board.dayNumber(target) - Board.dayNumber(today);
        if (days < 0) {
            days = 0;
        }
        return days + "\nDays for Weekend";
    }

    private String greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return "Good morning";
        }
        if (hour < 17) {
            return "Good afternoon";
        }
        return "Good evening";
    }

    private String detailFor(Board.Task task, boolean done) {
        if (!done) {
            return task.detail;
        }
        if (task.detail == null || task.detail.length() == 0) {
            return "Done";
        }
        return "Done · " + task.detail;
    }

    private int iconRes(String icon) {
        if ("document".equals(icon) || "doc".equals(icon)) {
            return R.drawable.ic_doc;
        }
        if ("math".equals(icon)) {
            return R.drawable.ic_math;
        }
        if ("game".equals(icon)) {
            return R.drawable.ic_game;
        }
        if ("art".equals(icon)) {
            return R.drawable.ic_art;
        }
        if ("book".equals(icon)) {
            return R.drawable.ic_book;
        }
        return R.drawable.ic_star;
    }

    private void showTime() {
        clicks++;
        if (clockView != null) {
            statusLine.setText(getString(R.string.title_time) + "  ·  " + clockView.getText().toString().replace("\n", " "));
        }
    }

    private void showDevice() {
        clicks++;
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        String maker = android.os.Build.MANUFACTURER == null ? "" : android.os.Build.MANUFACTURER;
        String model = android.os.Build.MODEL == null ? "" : android.os.Build.MODEL;
        statusLine.setText((maker + " " + model).trim() + "  ·  " + metrics.widthPixels + " × " + metrics.heightPixels);
    }

    private void showNote() {
        clicks++;
        statusLine.setText("Backgrounds: Navy, Paper, Daylight, Meadow, and Sunset.");
    }

    private void wireInfo(int id, final Runnable action) {
        Button button = findViewById(id);
        if (button == null) {
            return;
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        growOnFocus(button);
    }

    private void growOnFocus(View view) {
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.animate().scaleX(hasFocus ? 1.04f : 1f).scaleY(hasFocus ? 1.04f : 1f).setDuration(120).start();
            }
        });
    }

    private String focusedTaskId() {
        View focused = getCurrentFocus();
        if (focused != null && focused.getTag() instanceof TaskRef) {
            return ((TaskRef) focused.getTag()).id;
        }
        return null;
    }

    private boolean statusIs(int resId) {
        return statusLine != null && getString(resId).contentEquals(statusLine.getText());
    }

    private void applyLook() {
        Looks palette = Looks.of(look);
        if (screen != null) {
            GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{palette.bgTop, palette.bgBottom});
            screen.setBackground(background);
        }
        paintPanel(todayPanel, palette.panel);
        paintPanel(tomorrowPanel, palette.panel);
        paintPill(clockView, palette);
        paintPill(weatherView, palette);
        paintPill(countdownView, palette);
        if (btnView != null) {
            paintPill(btnView, palette);
            btnView.setText(palette.label);
        }
        if (nameView != null) {
            nameView.setTextColor(palette.muted);
        }
        if (greetingView != null) {
            greetingView.setTextColor(palette.text);
        }
        if (dateView != null) {
            dateView.setTextColor(palette.muted);
        }
        if (headingToday != null) {
            headingToday.setTextColor(palette.muted);
        }
        if (headingTomorrow != null) {
            headingTomorrow.setTextColor(palette.muted);
        }
        if (sectionLabel != null) {
            sectionLabel.setTextColor(palette.muted);
        }
        if (unlockLine != null) {
            unlockLine.setTextColor(palette.muted);
        }
        if (statusLine != null) {
            statusLine.setTextColor(palette.muted);
        }
    }

    private void paintPanel(View panel, int color) {
        if (panel == null) {
            return;
        }
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(18));
        panel.setBackground(background);
    }

    private void paintPill(View pill, Looks palette) {
        if (pill == null) {
            return;
        }
        GradientDrawable background = new GradientDrawable();
        background.setColor(palette.pill);
        background.setCornerRadius(dp(16));
        pill.setBackground(background);
        if (pill instanceof TextView) {
            ((TextView) pill).setTextColor(palette.text);
        }
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private String shiftDay(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, days);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView find(int id) {
        return (TextView) findViewById(id);
    }

    private static final class TaskRef {
        final String id;
        final boolean done;

        TaskRef(String id, boolean done) {
            this.id = id;
            this.done = done;
        }
    }
}
