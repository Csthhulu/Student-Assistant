package com.studentassistant.habits;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class HabitService {

    private static final double EMA_ALPHA = 0.25;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock = new ReentrantLock();

    public HabitService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void initSchema() {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".student-assistant");
            Files.createDirectories(dir);
        } catch (Exception ignored) {
            // directory may already exist
        }
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    assignment_id TEXT NOT NULL,
                    course_id INTEGER,
                    event TEXT NOT NULL,
                    at TEXT NOT NULL,
                    metadata TEXT
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS course_habits (
                    course_id INTEGER PRIMARY KEY,
                    avg_days_before_due REAL,
                    n_samples INTEGER NOT NULL DEFAULT 0,
                    snooze_count INTEGER NOT NULL DEFAULT 0,
                    updated_at TEXT
                )
                """);
    }

    public void recordEvent(
            String assignmentId,
            String event,
            Long courseId,
            Map<String, Object> metadata,
            OffsetDateTime dueAt
    ) {
        String now = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String metaJson = "{}";
        if (metadata != null && !metadata.isEmpty()) {
            try {
                metaJson = objectMapper.writeValueAsString(metadata);
            } catch (JsonProcessingException e) {
                metaJson = "{}";
            }
        }
        lock.lock();
        try {
            jdbc.update(
                    "INSERT INTO events (assignment_id, course_id, event, at, metadata) VALUES (?,?,?,?,?)",
                    assignmentId,
                    courseId,
                    event,
                    now,
                    metaJson
            );
            if ("submitted".equals(event) && courseId != null && dueAt != null) {
                updateCourseHabitOnSubmit(courseId, dueAt, OffsetDateTime.now(ZoneOffset.UTC));
            }
            if ("snoozed".equals(event) && courseId != null) {
                int p = 0;
                try {
                    p = jdbc.queryForObject(
                            "SELECT snooze_count FROM course_habits WHERE course_id = ?",
                            Integer.class,
                            courseId
                    );
                } catch (EmptyResultDataAccessException ignored) {
                    p = 0;
                }
                jdbc.update("""
                                INSERT INTO course_habits (course_id, snooze_count, updated_at)
                                VALUES (?, ?, ?)
                                ON CONFLICT(course_id) DO UPDATE SET
                                    snooze_count = excluded.snooze_count,
                                    updated_at = excluded.updated_at
                                """,
                        courseId,
                        p + 1,
                        now
                );
            }
        } finally {
            lock.unlock();
        }
    }

    private void updateCourseHabitOnSubmit(long courseId, OffsetDateTime dueAt, OffsetDateTime submittedAt) {
        double daysBefore = (dueAt.toEpochSecond() - submittedAt.toEpochSecond()) / 86400.0;
        daysBefore = Math.max(0.0, daysBefore);
        String now = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        List<CourseHabitRow> rows = jdbc.query(
                "SELECT avg_days_before_due, n_samples FROM course_habits WHERE course_id = ?",
                ps -> ps.setLong(1, courseId),
                (rs, rowNum) -> new CourseHabitRow(
                        rs.getObject("avg_days_before_due") != null ? rs.getDouble("avg_days_before_due") : null,
                        rs.getInt("n_samples")
                )
        );

        if (rows.isEmpty() || rows.getFirst().avgDaysBeforeDue == null) {
            jdbc.update("""
                            INSERT INTO course_habits (course_id, avg_days_before_due, n_samples, updated_at)
                            VALUES (?, ?, 1, ?)
                            ON CONFLICT(course_id) DO UPDATE SET
                                avg_days_before_due = excluded.avg_days_before_due,
                                n_samples = course_habits.n_samples + 1,
                                updated_at = excluded.updated_at
                            """,
                    courseId,
                    daysBefore,
                    now
            );
            return;
        }
        CourseHabitRow row = rows.getFirst();
        double old = row.avgDaysBeforeDue;
        int n = row.nSamples;
        double neu = old * (1 - EMA_ALPHA) + daysBefore * EMA_ALPHA;
        jdbc.update(
                "UPDATE course_habits SET avg_days_before_due = ?, n_samples = ?, updated_at = ? WHERE course_id = ?",
                neu,
                n + 1,
                now,
                courseId
        );
    }

    public CourseHabit getCourseHabit(long courseId) {
        List<CourseHabit> list = jdbc.query(
                "SELECT course_id, avg_days_before_due, n_samples, snooze_count FROM course_habits WHERE course_id = ?",
                ps -> ps.setLong(1, courseId),
                (rs, rowNum) -> new CourseHabit(
                        rs.getLong("course_id"),
                        rs.getObject("avg_days_before_due") != null ? rs.getDouble("avg_days_before_due") : null,
                        rs.getInt("n_samples"),
                        rs.getInt("snooze_count")
                )
        );
        return list.isEmpty() ? null : list.getFirst();
    }

    public Double globalAvgDaysBeforeDue() {
        return jdbc.query(
                "SELECT AVG(avg_days_before_due) AS a FROM course_habits WHERE avg_days_before_due IS NOT NULL",
                rs -> {
                    if (!rs.next() || rs.getObject("a") == null) {
                        return null;
                    }
                    return rs.getDouble("a");
                }
        );
    }

    public double habitUrgencyScore(Long courseId, OffsetDateTime dueAt) {
        return habitUrgencyScore(courseId, dueAt, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public double habitUrgencyScore(Long courseId, OffsetDateTime dueAt, OffsetDateTime now) {
        if (dueAt == null) {
            return 0.35;
        }
        double hoursLeft = Math.max(0.0, (dueAt.toEpochSecond() - now.toEpochSecond()) / 3600.0);
        double baselineHours = 72.0;
        double timePressure = Math.max(0.0, Math.min(1.0, 1.0 - hoursLeft / baselineHours));

        Double habitDays = null;
        if (courseId != null) {
            CourseHabit h = getCourseHabit(courseId);
            if (h != null && h.avgDaysBeforeDue() != null && h.nSamples() >= 2) {
                habitDays = h.avgDaysBeforeDue();
            }
        }
        if (habitDays == null) {
            habitDays = globalAvgDaysBeforeDue();
        }
        double procrastinationBoost = 0.0;
        if (habitDays != null && hoursLeft > 0) {
            double typical = habitDays * 24.0;
            if (hoursLeft < typical) {
                procrastinationBoost = Math.min(0.45, (typical - hoursLeft) / typical * 0.45);
            }
        }
        double snooze = 0.0;
        if (courseId != null) {
            CourseHabit h = getCourseHabit(courseId);
            if (h != null) {
                snooze = Math.min(0.15, h.snoozeCount() * 0.02);
            }
        }
        return Math.max(0.0, Math.min(1.0, 0.45 * timePressure + procrastinationBoost + snooze));
    }

    public Map<String, Object> summary() {
        List<Map<String, Object>> courses = jdbc.query(
                "SELECT * FROM course_habits",
                (rs, rowNum) -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("course_id", rs.getLong("course_id"));
                    m.put("avg_days_before_due", rs.getObject("avg_days_before_due"));
                    m.put("n_samples", rs.getInt("n_samples"));
                    m.put("snooze_count", rs.getInt("snooze_count"));
                    m.put("updated_at", rs.getString("updated_at"));
                    return m;
                }
        );
        int totalEvents = jdbc.query(
                "SELECT COUNT(*) AS c FROM events",
                rs -> rs.next() ? rs.getInt("c") : 0
        );
        Map<String, Object> out = new HashMap<>();
        out.put("tracked_courses", new ArrayList<>(courses));
        out.put("total_events", totalEvents);
        out.put("hint", "Submitting work updates how early you usually finish per course; snoozing nudges urgency up slightly.");
        return out;
    }

    private record CourseHabitRow(Double avgDaysBeforeDue, int nSamples) {
    }

    public record CourseHabit(
            long courseId,
            Double avgDaysBeforeDue,
            int nSamples,
            int snoozeCount
    ) {
    }
}
