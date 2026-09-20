package com.veap.performance;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

public final class App {
    private App() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            System.exit(2);
        }

        String command = args[0];
        Map<String, String> options = parseOptions(args);
        switch (command) {
            case "analyze":
                JtlAnalyzer.analyze(
                    requiredPath(options, "jtl"),
                    requiredPath(options, "metrics"),
                    requiredPath(options, "chart"),
                    ZoneId.of(options.getOrDefault("timezone", "America/Lima")));
                break;
            case "generate":
                ReportGenerator.generate(
                    requiredPath(options, "template"),
                    requiredPath(options, "metrics"),
                    requiredPath(options, "chart"),
                    requiredPath(options, "output"));
                break;
            case "validate":
                ReportValidator.validate(requiredPath(options, "report"));
                break;
            case "wait-until":
                ScheduleWaiter.waitUntil(
                    required(options, "start"),
                    ZoneId.of(options.getOrDefault("zone", "America/Lima")),
                    Long.parseLong(options.getOrDefault("max-wait-seconds", "14400")));
                break;
            default:
                usage();
                System.exit(2);
        }
    }

    static Map<String, String> parseOptions(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 1; index < args.length; index++) {
            String key = args[index];
            if (!key.startsWith("--") || index + 1 >= args.length) {
                throw new IllegalArgumentException("Opción inválida: " + key);
            }
            values.put(key.substring(2), args[++index]);
        }
        return values;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la opción --" + name);
        }
        return value;
    }

    private static Path requiredPath(Map<String, String> options, String name) {
        return Path.of(required(options, name));
    }

    private static void usage() {
        System.err.println("Comandos: analyze, generate, validate, wait-until");
    }
}
