package ar.edu.utn.dds.k3003.consultas.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.datadog.DatadogMeterRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.datadog.DatadogMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/** La configuración de métricas de application.properties, tal cual está en el repo. */
class MetricasDatadogConfigTest {

  private final ApplicationContextRunner contexto = new ApplicationContextRunner()
      .withInitializer(ctx -> {
        try {
          ctx.getEnvironment().getPropertySources()
              .addLast(new ResourcePropertySource("classpath:application.properties"));
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      })
      .withConfiguration(AutoConfigurations.of(
          MetricsAutoConfiguration.class,
          CompositeMeterRegistryAutoConfiguration.class,
          SimpleMetricsExportAutoConfiguration.class,
          DatadogMetricsExportAutoConfiguration.class))
      .withBean(ConsultasMetrics.class);

  @Test
  void sinVariablesDeEntornoNoSeExportaADatadogYLasMetricasLlevanLaEtiquetaDeLaApp() {
    contexto.run(ctx -> {
      assertThat(ctx).hasNotFailed();
      assertThat(ctx).doesNotHaveBean(DatadogMeterRegistry.class);

      ctx.getBean(ConsultasMetrics.class).eventoRecibido("DONACION_CREADA");
      MeterRegistry registry = ctx.getBean(MeterRegistry.class);
      assertThat(registry.get(ConsultasMetrics.RECIBIDOS).tag("application", "consultas")
          .tag("tipo", "DONACION_CREADA").counter().count()).isEqualTo(1.0);
    });
  }

  @Test
  void conDdEnabledYLaClaveSeExportaADatadog() {
    contexto
        .withPropertyValues("DD_ENABLED=true", "DD_API_KEY=clave-de-prueba", "DD_URI=http://localhost:9")
        .run(ctx -> {
          assertThat(ctx).hasNotFailed();
          assertThat(ctx).hasSingleBean(DatadogMeterRegistry.class);
        });
  }

  @Test
  void conDdEnabledYSinClaveLaAppArrancaPeroLaClaveQuedaVacia() {
    contexto.withPropertyValues("DD_ENABLED=true", "DD_URI=http://localhost:9").run(ctx -> {
      assertThat(ctx).hasNotFailed();
      assertThat(ctx).hasSingleBean(DatadogMeterRegistry.class);
      assertThat(ctx.getEnvironment().getProperty("management.datadog.metrics.export.api-key")).isEmpty();
    });
  }

  @Test
  void laClaveDeDatadogNoEstaEscritaEnElRepo() throws IOException {
    String properties = new ClassPathResource("application.properties").getContentAsString(StandardCharsets.UTF_8);
    assertThat(properties).contains("management.datadog.metrics.export.api-key=${DD_API_KEY:}");
    assertThat(properties).doesNotContainPattern("api-key=(?!\\$\\{DD_API_KEY:})");
  }
}
