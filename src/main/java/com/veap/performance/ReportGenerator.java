package com.veap.performance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ReportGenerator {
    private ReportGenerator() {}

    public static void generate(Path template, Path metricsPath, Path chart, Path output) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> metrics = mapper.readValue(
                metricsPath.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {});

        try (InputStream input = Files.newInputStream(template);
             XMLSlideShow show = new XMLSlideShow(input)) {
            if (show.getSlides().isEmpty()) {
                throw new IllegalArgumentException("La plantilla no contiene slides");
            }
            XSLFSlide slide = show.getSlides().get(0);
            updateText(slide, metrics);
            replaceChart(show, slide, chart);
            Files.createDirectories(output.toAbsolutePath().getParent());
            try (OutputStream stream = Files.newOutputStream(output)) {
                show.write(stream);
            }
        }
        System.out.println("Reporte generado: " + output);
    }

    private static void updateText(XSLFSlide slide, Map<String, Object> metrics) {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("Hora de inicio", string(metrics, "start_display"));
        replacements.put("Hora de finalización", string(metrics, "end_display"));
        replacements.put("Duración", string(metrics, "duration_display"));
        replacements.put("Cantidad total de transacciones", string(metrics, "transactions_total"));
        replacements.put("Transacciones exitosas y fallidas",
                string(metrics, "transactions_successful") + " exitosas y "
                        + string(metrics, "transactions_failed") + " fallidas");
        replacements.put("TPS promedio", String.format("%.2f TPS", number(metrics, "tps_average")));
        replacements.put("TPS máximo", string(metrics, "tps_maximum") + " TPS");

        List<String> found = new ArrayList<>();
        for (XSLFShape shape : slide.getShapes()) {
            if (!(shape instanceof XSLFTextShape)) continue;
            XSLFTextShape textShape = (XSLFTextShape) shape;
            for (XSLFTextParagraph paragraph : textShape.getTextParagraphs()) {
                String normalized = paragraph.getText().strip();
                for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                    if (normalized.startsWith(replacement.getKey())) {
                        replaceValue(paragraph, replacement.getValue());
                        found.add(replacement.getKey());
                        break;
                    }
                }
                if (normalized.startsWith("Gráfica de usuarios activos durante el ramp-up")) {
                    setParagraphText(paragraph, "Gráfica de usuarios activos y TPS durante la prueba:");
                }
            }
        }

        List<String> missing = replacements.keySet().stream()
                .filter(key -> !found.contains(key)).collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron campos en la plantilla: " + missing);
        }
    }

    private static void replaceValue(XSLFTextParagraph paragraph, String value) {
        List<XSLFTextRun> runs = paragraph.getTextRuns();
        if (runs.isEmpty()) {
            paragraph.addNewTextRun().setText(value);
            return;
        }
        String label = runs.get(0).getRawText().stripTrailing();
        while (label.endsWith(":")) label = label.substring(0, label.length() - 1);
        runs.get(0).setText(label + ":");
        if (runs.size() == 1) {
            paragraph.addNewTextRun().setText(" " + value);
        } else {
            runs.get(1).setText(" " + value);
            for (int index = 2; index < runs.size(); index++) runs.get(index).setText("");
        }
    }

    private static void setParagraphText(XSLFTextParagraph paragraph, String value) {
        List<XSLFTextRun> runs = paragraph.getTextRuns();
        if (runs.isEmpty()) {
            paragraph.addNewTextRun().setText(value);
            return;
        }
        boolean written = false;
        for (XSLFTextRun run : runs) {
            try {
                run.setText(written ? "" : value);
                written = true;
            } catch (IllegalStateException ignored) {
                // Apache POI representa los saltos de línea como runs inmutables.
            }
        }
        if (!written) paragraph.addNewTextRun().setText(value);
    }

    private static void replaceChart(XMLSlideShow show, XSLFSlide slide, Path chart) throws IOException {
        XSLFPictureShape picture = slide.getShapes().stream()
                .filter(XSLFPictureShape.class::isInstance)
                .map(XSLFPictureShape.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "La plantilla no contiene una imagen que pueda reemplazarse por la gráfica"));
        Rectangle2D anchor = picture.getAnchor();
        if (anchor.getWidth() < 7 * 72) {
            anchor = new Rectangle2D.Double(1.75 * 72, 5.05 * 72, 9.75 * 72, 2.05 * 72);
        }
        slide.removeShape(picture);
        XSLFPictureData pictureData = show.addPicture(Files.readAllBytes(chart), PictureData.PictureType.PNG);
        XSLFPictureShape replacement = slide.createPicture(pictureData);
        replacement.setAnchor(anchor);
    }

    private static String string(Map<String, Object> metrics, String name) {
        Object value = metrics.get(name);
        if (value == null) throw new IllegalArgumentException("Falta la métrica: " + name);
        return value.toString();
    }

    private static double number(Map<String, Object> metrics, String name) {
        Object value = metrics.get(name);
        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(string(metrics, name));
    }
}
