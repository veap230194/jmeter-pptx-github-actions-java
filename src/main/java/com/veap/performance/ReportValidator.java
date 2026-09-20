package com.veap.performance;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class ReportValidator {
    private ReportValidator() {}

    public static void validate(Path report) throws IOException {
        try (InputStream input = Files.newInputStream(report);
             XMLSlideShow show = new XMLSlideShow(input)) {
            if (show.getSlides().size() != 1) {
                throw new IllegalStateException("El reporte debe conservar un slide");
            }
            List<XSLFShape> shapes = show.getSlides().get(0).getShapes();
            String text = shapes.stream()
                    .filter(XSLFTextShape.class::isInstance)
                    .map(XSLFTextShape.class::cast)
                    .map(XSLFTextShape::getText)
                    .reduce("", (left, right) -> left + "\n" + right);
            List<String> required = List.of(
                    "Hora de inicio", "Hora de finalización", "Duración",
                    "Cantidad total de transacciones", "TPS promedio", "TPS máximo");
            List<String> missing = required.stream()
                    .filter(item -> !text.contains(item)).collect(Collectors.toList());
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Faltan textos obligatorios: " + missing);
            }
            if (shapes.stream().noneMatch(XSLFPictureShape.class::isInstance)) {
                throw new IllegalStateException("El reporte no contiene la gráfica");
            }
        }
        System.out.println("Validación estructural correcta");
    }
}
