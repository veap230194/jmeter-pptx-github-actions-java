package com.veap.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineTest {
    @TempDir
    Path temp;

    @Test
    void createsMetricsChartAndPowerPoint() throws Exception {
        Path metrics = temp.resolve("metrics.json");
        Path chart = temp.resolve("chart.png");
        Path report = temp.resolve("report.pptx");

        JtlAnalyzer.analyze(Path.of("tests/results.jtl"), metrics, chart, ZoneId.of("America/Lima"));
        ReportGenerator.generate(Path.of("template/Ejemplo.pptx"), metrics, chart, report);
        ReportValidator.validate(report);

        assertTrue(Files.size(metrics) > 0);
        assertTrue(Files.size(chart) > 0);
        assertTrue(Files.size(report) > 0);
    }
}

