package com.example.waypoints;

/**
 * Saved waypoint. The two Boolean visibility fields are nullable only for JSON
 * backwards compatibility: saves created before 1.2.0 did not contain them,
 * and a missing value is treated as enabled.
 */
public record Waypoint(
        String name,
        int x,
        int y,
        int z,
        int color,
        String dimension,
        long expiresAt,
        String worldId,
        String markerText,
        Boolean labelEnabled,
        Boolean beamEnabled
) {
    /** Backwards-compatible constructor for callers and older integrations. */
    public Waypoint(String name, int x, int y, int z, int color, String dimension,
                    long expiresAt, String worldId, String markerText) {
        this(name, x, y, z, color, dimension, expiresAt, worldId, markerText, true, true);
    }

    public boolean isLabelEnabled() {
        return labelEnabled == null || labelEnabled;
    }

    public boolean isBeamEnabled() {
        return beamEnabled == null || beamEnabled;
    }

    public String safeMarkerText() {
        if (markerText == null || markerText.isBlank()) return "W";
        String value = markerText.trim();
        return value.length() > 2 ? value.substring(0, 2).toUpperCase(java.util.Locale.ROOT) : value.toUpperCase(java.util.Locale.ROOT);
    }
}
