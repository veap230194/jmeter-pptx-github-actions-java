# JMeter Performance Report en Java

Versión Java del proyecto de pruebas de rendimiento. GitHub Actions instala JMeter, ejecuta el
plan, analiza el JTL y completa la plantilla PowerPoint utilizando una aplicación Java 17.

## Componentes

- JMeter 5.6.3 ejecuta `jmeter/performance-test.jmx`.
- Java y Apache Commons CSV calculan las métricas.
- JFreeChart genera la gráfica de usuarios activos y TPS.
- Apache POI completa y valida `template/Ejemplo.pptx`.
- Jackson genera `output/metrics.json`.
- Maven compila un JAR ejecutable en `target/performance-report.jar`.

## Configuración de GitHub

Crear el secreto `RAPIDAPI_KEY` en **Settings > Secrets and variables > Actions**.

## Ejecución

1. Abrir **Actions**.
2. Elegir **JMeter performance test and Java PPTX report**.
3. Pulsar **Run workflow**.
4. Configurar usuarios, ramp-up, duración, iteraciones y usuario objetivo.
5. Dejar `scheduled_start` vacío para comenzar inmediatamente o ingresar
   `AAAA-MM-DD HH:MM` en horario `America/Lima`, hasta cuatro horas en el futuro.
6. Descargar `performance-report-java-<número>` desde **Artifacts**.

Para una prueba controlada por tiempo, usar `loops=-1`. La duración detendrá el grupo de hilos.
La prueba también se detiene si pasan 180 segundos sin respuestas exitosas. En ese caso se crea
`output/fail-fast.txt`, se publican los resultados parciales y la ejecución termina en rojo.

## Prueba local

```bash
mvn verify

java -jar target/performance-report.jar analyze \
  --jtl tests/results.jtl \
  --metrics output/metrics.json \
  --chart output/load-chart.png \
  --timezone America/Lima

java -jar target/performance-report.jar generate \
  --template template/Ejemplo.pptx \
  --metrics output/metrics.json \
  --chart output/load-chart.png \
  --output output/Reporte_Performance_demo.pptx

java -jar target/performance-report.jar validate \
  --report output/Reporte_Performance_demo.pptx
```

