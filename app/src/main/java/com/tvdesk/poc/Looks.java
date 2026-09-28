package com.tvdesk.poc;

public final class Looks {

    public static final String[] IDS = new String[]{"navy", "paper", "daylight", "meadow", "sunset"};

    public final String id;
    public final String label;
    public final int bgTop;
    public final int bgBottom;
    public final int panel;
    public final int pill;
    public final int text;
    public final int muted;
    public final int idle;

    private Looks(String id, String label, int bgTop, int bgBottom, int panel, int pill, int text, int muted, int idle) {
        this.id = id;
        this.label = label;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.panel = panel;
        this.pill = pill;
        this.text = text;
        this.muted = muted;
        this.idle = idle;
    }

    public static String canonical(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) {
                return id;
            }
        }
        return "navy";
    }

    public static Looks of(String id) {
        String key = canonical(id);
        if ("paper".equals(key)) {
            return new Looks(key, "Paper", 0xFFF6EAD7, 0xFFE7D3B5, 0xFFFFF8EE, 0xFFF3E2C8, 0xFF3A2E22, 0xFF8A7260, 0xFFFFFDF8);
        }
        if ("daylight".equals(key)) {
            return new Looks(key, "Daylight", 0xFFE7F2FB, 0xFFD5E6F5, 0xFFF7FBFF, 0xFFD7E8F6, 0xFF1C3148, 0xFF5E7388, 0xFFFFFFFF);
        }
        if ("meadow".equals(key)) {
            return new Looks(key, "Meadow", 0xFFE5F6D8, 0xFFC9E8B0, 0xFFF4FBEA, 0xFFD7EEC4, 0xFF234024, 0xFF5C7350, 0xFFF8FFF2);
        }
        if ("sunset".equals(key)) {
            return new Looks(key, "Sunset", 0xFFFFD7C2, 0xFFF3B6C8, 0xFFFFF4EC, 0xFFFFE0D0, 0xFF4A2430, 0xFF8A5A62, 0xFFFFF9F4);
        }
        return new Looks("navy", "Navy", 0xFF17233C, 0xFF070D18, 0xFF17233A, 0xFF1B2740, 0xFFF4F7FB, 0xFF8B98A8, 0xFF17233A);
    }
}
