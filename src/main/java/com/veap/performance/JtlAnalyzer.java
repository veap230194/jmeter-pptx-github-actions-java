package com.veap.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.awt.BasicStroke;
import java.awt.Color;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class JtlAnalyzer {
    private JtlAnalyzer() {}

    public static void analyze(Path jtl, Path metricsPath, Path chartPath, ZoneId zone) throws IOException {
        List<Sample> samples = readSamples(jtl);
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("El JTL no contiene muestras");
        }

        long startMs = samples.stream().mapToLong(Sample::timestamp).min().orElseThrow();
        long endMs = samples.stream().mapToLong(sample -> sample.timestamp() + sample.elapsed()).max().orElseThrow();
        double durationSeconds = Math.max((endMs - startMs) / 1000.0, 0.001);
        long successful = samples.stream().filter(Sample::successful).count();
        long failed = samples.size() - successful;

        Map<Long, Integer> samplesBySecond = new TreeMap<>();
        Map<Long, Integer> activeBySecond = new TreeMap<>();
        Map<String, Integer> responseCodes = new LinkedHashMap<>();
        List<Long> elapsed = new ArrayList<>();

        for (Sample sample : samples) {
            long second = Math.max(0, (sample.timestamp() - startMs) / 1000);
            samplesBySecond.merge(second, 1, Integer::sum);
            activeBySecond.merge(second, sample.activeThreads(), Math::max);
            responseCodes.merge(sample.responseCode(), 1, Integer::sum);
            elapsed.add(sample.elapsed());
        }

        LinkedHashMap<String, Integer> sortedCodes = new LinkedHashMap<>();
        responseCodes.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .forEach(entry -> sortedCodes.put(entry.getKey(), entry.getValue()));

        long lastSecond = samplesBySecond.keySet().stream().mapToLong(Long::longValue).max().orElse(0);
        int maximumTps = samplesBySecond.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int maximumUsers = activeBySecond.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double averageElapsed = elapsed.stream().mapToLong(Long::longValue).average().orElse(0);

        DateTimeFormatter display = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm:ss a", Locale.ENGLISH);
        ZonedDateTime start = Instant.ofEpochMilli(startMs).atZone(zone);
        ZonedDateTime end = Instant.ofEpochMilli(endMs).atZone(zone);

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("start_iso", start.toString());
        metrics.put("end_iso", end.toString());
        metrics.put("start_display", display.format(start).toLowerCase(Locale.ROOT));
        metrics.put("end_display", display.format(end).toLowerCase(Locale.ROOT));
        metrics.put("duration_seconds", round(durationSeconds, 3));
        metrics.put("duration_display", durationText(durationSeconds));
        metrics.put("transactions_total", samples.size());
        metrics.put("transactions_successful", successful);
        metrics.put("transactions_failed", failed);
        metrics.put("success_rate_percent", round(successful * 100.0 / samples.size(), 2));
        metrics.put("error_rate_percent", round(failed * 100.0 / samples.size(), 2));
        metrics.put("tps_average", round(samples.size() / durationSeconds, 2));
        metrics.put("tps_maximum", maximumTps);
        metrics.put("elapsed_average_ms", round(averageElapsed, 2));
        metrics.put("elapsed_p90_ms", round(percentile(elapsed, 0.90), 2));
        metrics.put("elapsed_p95_ms", round(percentile(elapsed, 0.95), 2));
        metrics.put("active_users_maximum", maximumUsers);
        metrics.put("response_codes", sortedCodes);

        Files.createDirectories(metricsPath.toAbsolutePath().getParent());
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(metricsPath.toFile(), metrics);
        createChart(samplesBySecond, activeBySecond, lastSecond, chartPath);
        System.out.println(mapper.writeValueAsString(metrics));
    }

    static List<Sample> readSamples(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .build();
            List<Sample> samples = new ArrayList<>();
            for (CSVRecord row : format.parse(reader)) {
                requireColumns(row, "timeStamp", "elapsed", "success");
                String threads = value(row, "allThreads", value(row, "grpThreads", "0"));
                samples.add(new Sample(
                        Long.parseLong(row.get("timeStamp")),
                        Math.round(Double.parseDouble(row.get("elapsed"))),
                        Boolean.parseBoolean(row.get("success")),
                        value(row, "responseCode", "sin código"),
                        parseInt(threads)));
            }
            return samples;
        }
    }

    private static void requireColumns(CSVRecord row, String... columns) {
        for (String column : columns) {
            if (!row.isMapped(column)) {
                throw new IllegalArgumentException("El JTL no contiene la columna obligatoria: " + column);
            }
        }
    }

    private static String value(CSVRecord row, String column, String fallback) {
        if (!row.isMapped(column)) {
            return fallback;
        }
        String value = row.get(column);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int parseInt(String value) {
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static double percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;
        List<Long> ordered = values.stream().sorted().collect(Collectors.toList());
        double position = (ordered.size() - 1) * percentile;
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        if (lower == upper) return ordered.get(lower);
        return ordered.get(lower) + (ordered.get(upper) - ordered.get(lower)) * (position - lower);
    }

    private static String durationText(double durationSeconds) {
        long total = Math.max(0, Math.round(durationSeconds));
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        List<String> parts = new ArrayList<>();
        if (hours > 0) parts.add(hours + " h");
        if (minutes > 0) parts.add(minutes + " min");
        if (seconds > 0 || parts.isEmpty()) parts.add(seconds + " s");
        return String.join(" ", parts);
    }

    private static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private static void createChart(
            Map<Long, Integer> tpsBySecond,
            Map<Long, Integer> usersBySecond,
            long lastSecond,
            Path output) throws IOException {
        XYSeries users = new XYSeries("Usuarios activos");
        XYSeries tps = new XYSeries("TPS");
        for (long second = 0; second <= lastSecond; second++) {
            users.add(second, usersBySecond.getOrDefault(second, 0));
            tps.add(second, tpsBySecond.getOrDefault(second, 0));
        }

        XYSeriesCollection usersDataset = new XYSeriesCollection(users);
        JFreeChart chart = ChartFactory.createXYLineChart(
                null, "Tiempo transcurrido (s)", "Usuarios activos", usersDataset);
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setRangeGridlinePaint(new Color(209, 213, 219));
        plot.setDomainGridlinePaint(new Color(229, 231, 235));

        XYLineAndShapeRenderer usersRenderer = new XYLineAndShapeRenderer(true, false);
        usersRenderer.setSeriesPaint(0, new Color(37, 99, 235));
        usersRenderer.setSeriesStroke(0, new BasicStroke(2.4f));
        plot.setRenderer(0, usersRenderer);

        NumberAxis tpsAxis = new NumberAxis("Transacciones por segundo");
        tpsAxis.setAutoRangeIncludesZero(true);
        plot.setRangeAxis(1, tpsAxis);
        plot.setDataset(1, new XYSeriesCollection(tps));
        plot.mapDatasetToRangeAxis(1, 1);
        XYLineAndShapeRenderer tpsRenderer = new XYLineAndShapeRenderer(true, false);
        tpsRenderer.setSeriesPaint(0, new Color(249, 115, 22));
        tpsRenderer.setSeriesStroke(0, new BasicStroke(2.0f));
        plot.setRenderer(1, tpsRenderer);

        Files.createDirectories(output.toAbsolutePath().getParent());
        ChartUtils.saveChartAsPNG(output.toFile(), chart, 1920, 864);
    }

    static final class Sample {
        private final long timestamp;
        private final long elapsed;
        private final boolean successful;
        private final String responseCode;
        private final int activeThreads;

        Sample(long timestamp, long elapsed, boolean successful, String responseCode, int activeThreads) {
            this.timestamp = timestamp;
            this.elapsed = elapsed;
            this.successful = successful;
            this.responseCode = responseCode;
            this.activeThreads = activeThreads;
        }

        long timestamp() { return timestamp; }
        long elapsed() { return elapsed; }
        boolean successful() { return successful; }
        String responseCode() { return responseCode; }
        int activeThreads() { return activeThreads; }
    }
}
