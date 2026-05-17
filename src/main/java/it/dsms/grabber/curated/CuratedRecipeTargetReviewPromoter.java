package it.dsms.grabber.curated;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CuratedRecipeTargetReviewPromoter {

    private static final Pattern TARGET_ID_PATTERN = Pattern.compile("\\b[APSCN][0-9]{2,3}\\b");

    public ReviewPlan buildPlan(List<CuratedRecipeTarget> targets, File validatorLog) throws IOException {
        Set<String> warningTargetIds = readWarningTargetIds(validatorLog);
        Set<String> knownTargetIds = new LinkedHashSet<>();
        Map<String, Integer> currentStatusCounts = new LinkedHashMap<>();
        Map<String, Integer> plannedStatusCounts = new LinkedHashMap<>();
        Map<String, String> statusChanges = new LinkedHashMap<>();

        for (CuratedRecipeTarget target : targets) {
            knownTargetIds.add(target.targetId);
            String currentStatus = normalizedStatus(target.reviewStatus);
            increment(currentStatusCounts, currentStatus);

            String plannedStatus = plannedStatus(target.targetId, currentStatus, warningTargetIds);
            increment(plannedStatusCounts, plannedStatus);

            if (!plannedStatus.equals(currentStatus)) {
                statusChanges.put(target.targetId, plannedStatus);
            }
        }

        List<String> unknownWarningTargetIds = warningTargetIds.stream()
                .filter(id -> !knownTargetIds.contains(id))
                .toList();

        return new ReviewPlan(
                validatorLog,
                targets.size(),
                warningTargetIds,
                unknownWarningTargetIds,
                currentStatusCounts,
                plannedStatusCounts,
                statusChanges
        );
    }

    private Set<String> readWarningTargetIds(File validatorLog) throws IOException {
        if (!validatorLog.exists()) {
            throw new IOException("Log validator non trovato: " + validatorLog.getAbsolutePath());
        }

        Set<String> result = new LinkedHashSet<>();
        for (String line : Files.readAllLines(validatorLog.toPath(), StandardCharsets.UTF_8)) {
            if (!line.startsWith("WARN recipe-targets:")) continue;

            Matcher matcher = TARGET_ID_PATTERN.matcher(line);
            while (matcher.find()) {
                result.add(matcher.group());
            }
        }
        return result;
    }

    private String plannedStatus(String targetId, String currentStatus, Set<String> warningTargetIds) {
        if ("rejected".equals(currentStatus)) return currentStatus;
        if (warningTargetIds.contains(targetId)) return "needs_review";
        if ("pending".equals(currentStatus)) return "approved";
        return currentStatus;
    }

    private String normalizedStatus(String status) {
        if (status == null || status.isBlank()) return "pending";
        return status.trim().toLowerCase(Locale.ROOT);
    }

    private void increment(Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    public static class ReviewPlan {
        public final File validatorLog;
        public final int totalTargets;
        public final Set<String> warningTargetIds;
        public final List<String> unknownWarningTargetIds;
        public final Map<String, Integer> currentStatusCounts;
        public final Map<String, Integer> plannedStatusCounts;
        public final Map<String, String> statusChanges;

        ReviewPlan(
                File validatorLog,
                int totalTargets,
                Set<String> warningTargetIds,
                List<String> unknownWarningTargetIds,
                Map<String, Integer> currentStatusCounts,
                Map<String, Integer> plannedStatusCounts,
                Map<String, String> statusChanges
        ) {
            this.validatorLog = validatorLog;
            this.totalTargets = totalTargets;
            this.warningTargetIds = warningTargetIds;
            this.unknownWarningTargetIds = unknownWarningTargetIds;
            this.currentStatusCounts = currentStatusCounts;
            this.plannedStatusCounts = plannedStatusCounts;
            this.statusChanges = statusChanges;
        }

        public List<String> firstWarningTargetIds(int limit) {
            List<String> result = new ArrayList<>(warningTargetIds);
            return result.subList(0, Math.min(limit, result.size()));
        }
    }
}
