package com.tvdesk.poc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DeskStore {

    private final String baseUrl;
    private final String apiKey;
    private final String weatherKey;

    public DeskStore(String supabaseUrl, String anonKey, String weatherKey) {
        String url = supabaseUrl == null ? "" : supabaseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;
        this.apiKey = anonKey == null ? "" : anonKey.trim();
        this.weatherKey = weatherKey == null ? "" : weatherKey.trim();
    }

    public boolean isConfigured() {
        return baseUrl.startsWith("https://") && apiKey.length() > 20;
    }

    public Board fetch(String today) throws Exception {
        JSONArray settingsRows = getArray("/rest/v1/tvdesk_settings?id=eq.1&select=*");
        JSONArray userRows = optionalArray("/rest/v1/tvdesk_users?active=eq.true&select=id,name,sort_order,reward_title,reward_target,stars_toward_reward,streak_current,streak_best&order=sort_order.asc");
        if (userRows.length() == 0) {
            JSONArray basicUsers = getArray("/rest/v1/tvdesk_users?active=eq.true&select=id,name,sort_order&order=sort_order.asc");
            if (basicUsers.length() > 0) {
                userRows = basicUsers;
            }
        }
        JSONArray taskRows = getArray("/rest/v1/tvdesk_tasks?active=eq.true&select=*&order=sort_order.asc");
        JSONArray itemRows = getArray("/rest/v1/tvdesk_schedule_items?active=eq.true&select=*&order=sort_order.asc");
        JSONArray logRows = getArray("/rest/v1/tvdesk_task_logs?day=eq." + today + "&select=task_id,count");
        JSONArray awardRows = getArray("/rest/v1/tvdesk_awards?active=eq.true&select=*&order=sort_order.asc");
        JSONArray postRows = optionalArray("/rest/v1/tvdesk_posts?active=eq.true&removed_at=is.null&select=id,user_id,message,badge,bonus_stars,posted_on,expires_on&order=sort_order.asc");
        JSONArray achievementRows = optionalArray("/rest/v1/tvdesk_user_achievements?active=eq.true&select=id,user_id,title,subtitle,icon&order=sort_order.asc");

        Board.Settings settings = settingsRows.length() == 0
                ? new Board.Settings(null, "cards", null, null, null, "navy")
                : settingsOf(settingsRows.getJSONObject(0));

        List<Board.User> users = new ArrayList<Board.User>();
        for (int i = 0; i < userRows.length(); i++) {
            JSONObject row = userRows.getJSONObject(i);
            users.add(new Board.User(
                    row.getString("id"),
                    row.optString("name", ""),
                    row.optInt("sort_order", 0),
                    row.optString("reward_title", "Movie night"),
                    row.optInt("reward_target", 50),
                    row.optInt("stars_toward_reward", 0),
                    row.optInt("streak_current", 0),
                    row.optInt("streak_best", 0)));
        }

        List<Board.Task> tasks = new ArrayList<Board.Task>();
        for (int i = 0; i < taskRows.length(); i++) {
            tasks.add(taskOf(taskRows.getJSONObject(i)));
        }
        Collections.sort(tasks, new Comparator<Board.Task>() {
            @Override
            public int compare(Board.Task a, Board.Task b) {
                return a.sortOrder - b.sortOrder;
            }
        });

        List<Board.Item> items = new ArrayList<Board.Item>();
        for (int i = 0; i < itemRows.length(); i++) {
            items.add(itemOf(itemRows.getJSONObject(i)));
        }

        List<Board.Award> awards = new ArrayList<Board.Award>();
        for (int i = 0; i < awardRows.length(); i++) {
            awards.add(awardOf(awardRows.getJSONObject(i)));
        }

        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (int i = 0; i < logRows.length(); i++) {
            JSONObject row = logRows.getJSONObject(i);
            counts.put(row.getString("task_id"), row.optInt("count", 0));
        }

        List<Board.Post> posts = new ArrayList<Board.Post>();
        for (int i = 0; i < postRows.length(); i++) {
            JSONObject row = postRows.getJSONObject(i);
            posts.add(new Board.Post(
                    row.getString("id"),
                    textOrNull(row, "user_id"),
                    row.optString("message", ""),
                    row.optString("badge", "none"),
                    row.optInt("bonus_stars", 0),
                    textOrNull(row, "posted_on"),
                    textOrNull(row, "expires_on")));
        }

        List<Board.Achievement> achievements = new ArrayList<Board.Achievement>();
        for (int i = 0; i < achievementRows.length(); i++) {
            JSONObject row = achievementRows.getJSONObject(i);
            achievements.add(new Board.Achievement(
                    row.getString("id"),
                    textOrNull(row, "user_id"),
                    row.optString("title", ""),
                    row.optString("subtitle", ""),
                    row.optString("icon", "star")));
        }
        return new Board(settings, users, tasks, items, awards, counts, posts, achievements);
    }

    private JSONArray optionalArray(String path) {
        try {
            return getArray(path);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    public void setDone(String taskId, String day, int count) throws Exception {
        if (count <= 0) {
            send("DELETE", "/rest/v1/tvdesk_task_logs?task_id=eq." + taskId + "&day=eq." + day, null, null);
            return;
        }
        JSONObject body = new JSONObject();
        body.put("task_id", taskId);
        body.put("day", day);
        body.put("count", count);
        send("POST", "/rest/v1/tvdesk_task_logs?on_conflict=task_id,day", body.toString(), "resolution=merge-duplicates,return=minimal");
    }

    public void setLook(String look) throws Exception {
        JSONObject current = new JSONObject();
        JSONArray rows = getArray("/rest/v1/tvdesk_settings?id=eq.1&select=meta");
        if (rows.length() > 0) {
            current = metaObject(rows.getJSONObject(0));
        }
        current.put("look", Looks.canonical(look));
        JSONObject body = new JSONObject();
        body.put("meta", current);
        body.put("theme", "cards");
        send("PATCH", "/rest/v1/tvdesk_settings?id=eq.1", body.toString(), "return=minimal");
    }

    public void setItemMarked(String itemId, String metaJson, String day, boolean mark) throws Exception {
        JSONObject meta = new JSONObject(Board.markItem(metaJson, day, mark));
        JSONObject body = new JSONObject();
        body.put("meta", meta);
        send("PATCH", "/rest/v1/tvdesk_schedule_items?id=eq." + itemId, body.toString(), "return=minimal");
    }

    public void setActiveUser(String userId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("active_user_id", userId);
        send("PATCH", "/rest/v1/tvdesk_settings?id=eq.1", body.toString(), "return=minimal");
    }

    public String fetchTemperature(String city) throws Exception {
        if (weatherKey.length() == 0 || city == null || city.length() == 0) {
            return "";
        }
        String query = URLEncoder.encode(city, "UTF-8");
        String endpoint = "https://api.openweathermap.org/data/2.5/weather?q=" + query
                + "&units=imperial&appid=" + weatherKey;
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestMethod("GET");
        int code = connection.getResponseCode();
        String body = read(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new Exception("Weather HTTP " + code);
        }
        JSONObject json = new JSONObject(body);
        int temp = (int) Math.round(json.getJSONObject("main").getDouble("temp"));
        return temp + "°";
    }

    private Board.Settings settingsOf(JSONObject row) {
        JSONObject meta = metaObject(row);
        return new Board.Settings(
                textOrNull(row, "active_user_id"),
                "cards",
                row.optString("city", ""),
                row.optString("countdown_label", ""),
                textOrNull(row, "countdown_date"),
                meta.optString("look", "navy"),
                meta.optString("tv_experience", "family"));
    }

    private Board.Task taskOf(JSONObject row) throws Exception {
        return new Board.Task(
                row.getString("id"),
                textOrNull(row, "user_id"),
                row.optString("label", ""),
                row.optString("detail", ""),
                row.optString("icon", "star"),
                row.optInt("sort_order", 0),
                row.optString("cadence", "daily"),
                ints(row, "weekdays"),
                row.isNull("every_n_days") ? 0 : row.optInt("every_n_days", 0),
                textOrNull(row, "anchor_date"),
                textOrNull(row, "on_date"),
                row.optInt("goal_count", 1));
    }

    private Board.Item itemOf(JSONObject row) throws Exception {
        return new Board.Item(
                row.getString("id"),
                textOrNull(row, "user_id"),
                row.optString("title", ""),
                row.optString("time_label", ""),
                row.optInt("sort_order", 0),
                textOrNull(row, "task_id"),
                row.optString("cadence", "once"),
                ints(row, "weekdays"),
                row.isNull("every_n_days") ? 0 : row.optInt("every_n_days", 0),
                textOrNull(row, "anchor_date"),
                textOrNull(row, "on_date"),
                metaText(row));
    }

    private Board.Award awardOf(JSONObject row) throws Exception {
        return new Board.Award(
                row.getString("id"),
                textOrNull(row, "user_id"),
                row.optString("title", ""),
                row.optString("message", ""),
                row.isNull("minutes") ? 0 : row.optInt("minutes", 0),
                row.optString("icon", "star"),
                row.optString("show_when", "all_done"),
                row.isNull("min_done") ? 0 : row.optInt("min_done", 0),
                row.optInt("sort_order", 0));
    }

    private JSONArray getArray(String path) throws Exception {
        String body = send("GET", path, null, null);
        if (body.length() == 0) {
            return new JSONArray();
        }
        return new JSONArray(body);
    }

    private String send(String method, String path, String jsonBody, String prefer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("apikey", apiKey);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setRequestProperty("Accept", "application/json");
        if (prefer != null) {
            connection.setRequestProperty("Prefer", prefer);
        }
        if (jsonBody != null) {
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            OutputStream out = connection.getOutputStream();
            out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            out.close();
        }
        int code = connection.getResponseCode();
        if (code == 204 || code == 205) {
            connection.disconnect();
            return "";
        }
        String body = read(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new Exception("Supabase HTTP " + code);
        }
        return body == null ? "" : body;
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            text.append(line);
        }
        reader.close();
        return text.toString();
    }

    private static JSONObject metaObject(JSONObject row) {
        if (!row.has("meta") || row.isNull("meta")) {
            return new JSONObject();
        }
        Object raw = row.opt("meta");
        if (raw instanceof JSONObject) {
            return (JSONObject) raw;
        }
        if (raw instanceof String && ((String) raw).length() > 0) {
            try {
                return new JSONObject((String) raw);
            } catch (Exception ignored) {
                return new JSONObject();
            }
        }
        return new JSONObject();
    }

    private static String metaText(JSONObject row) {
        return metaObject(row).toString();
    }

    private static String textOrNull(JSONObject row, String name) {
        if (!row.has(name) || row.isNull(name)) {
            return null;
        }
        String value = row.optString(name, "");
        return value.length() == 0 ? null : value;
    }

    private static int[] ints(JSONObject row, String name) {
        if (!row.has(name) || row.isNull(name)) {
            return new int[0];
        }
        JSONArray array = row.optJSONArray(name);
        if (array == null) {
            return new int[0];
        }
        int[] values = new int[array.length()];
        for (int i = 0; i < array.length(); i++) {
            values[i] = array.optInt(i, -1);
        }
        return values;
    }
}
