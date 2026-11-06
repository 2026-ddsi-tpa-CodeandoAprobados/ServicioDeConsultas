package ar.edu.utn.dds.k3003.consultas.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Métricas del Servicio de Consultas (Micrometer; se exportan a Datadog si {@code DD_ENABLED=true}). Todas
 * llevan además la etiqueta común {@code application:consultas} (ver application.properties).
 *
 * <ul>
 *   <li>{@code consultas.eventos.recibidos{tipo}}: cada evento que llega a la cola.
 *   <li>{@code consultas.eventos.procesados{tipo}}: los que modificaron el modelo de lectura.
 *   <li>{@code consultas.eventos.ignorados{tipo, motivo}}: repetido, obsoleto, no_mapeado, catalogo,
 *       invalido.
 *   <li>{@code consultas.eventos.errores{tipo}}: excepciones al procesar.
 *   <li>{@code consultas.requests{endpoint, metodo}}: pedidos a cada endpoint HTTP.
 * </ul>
 *
 * <p>La etiqueta {@code tipo} toma solo los tipos que publican los módulos; cualquier otro se informa como
 * {@value #OTRO}, para que un productor con tipos arbitrarios no multiplique las series en Datadog.
 */
@Component
public class ConsultasMetrics {

  public static final String RECIBIDOS = "consultas.eventos.recibidos";
  public static final String PROCESADOS = "consultas.eventos.procesados";
  public static final String IGNORADOS = "consultas.eventos.ignorados";
  public static final String ERRORES = "consultas.eventos.errores";
  public static final String REQUESTS = "consultas.requests";
  public static final String OTRO = "OTRO";

  private static final Set<String> TIPOS_CONOCIDOS = Set.of(
      // Donaciones
      "DONACION_CREADA", "DONACION_ESTADO_MODIFICADO", "DONACION_ELIMINADA",
      // Donadores y Entidades
      "DONADOR_CREADO", "DONADOR_ESTADO_MODIFICADO", "DONADOR_CATEGORIA_MODIFICADA", "DONADOR_ELIMINADO",
      "DONADORES_TODOS_ELIMINADOS", "ENTIDAD_CREADA", "ENTIDAD_RAZON_MODIFICADA", "ENTIDAD_ELIMINADA",
      "ENTIDADES_TODAS_ELIMINADAS", "NECESIDAD_REGISTRADA", "NECESIDAD_MODIFICADA", "NECESIDAD_SATISFECHA",
      "NECESIDAD_ELIMINADA", "QUEJA_REGISTRADA",
      // Logística
      "DEPOSITO_CREADO", "DEPOSITO_MODIFICADO", "DEPOSITO_ELIMINADO", "DEPOSITO_ALGORITMO_CONFIGURADO",
      "DEPOSITO_STOCK_ACTUALIZADO", "ASIGNACION_CREADA", "ENTREGA_COMPLETADA",
      // Incentivos
      "DONADOR_ACTUALIZADO", "INSIGNIA_CREADA", "INSIGNIA_ELIMINADA", "MISION_CREADA", "MISION_MODIFICADA",
      "MISION_ELIMINADA",
      // Logística e Incentivos
      "BASE_LIMPIADA");

  private final MeterRegistry registry;

  public ConsultasMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  /** Métricas que no se exportan a ningún lado: para usar el servicio sin Spring (tests). */
  public static ConsultasMetrics enMemoria() {
    return new ConsultasMetrics(new SimpleMeterRegistry());
  }

  public void eventoRecibido(String tipo) {
    registry.counter(RECIBIDOS, "tipo", etiqueta(tipo)).increment();
  }

  public void eventoProcesado(String tipo) {
    registry.counter(PROCESADOS, "tipo", etiqueta(tipo)).increment();
  }

  public void eventoIgnorado(String tipo, String motivo) {
    registry.counter(IGNORADOS, "tipo", etiqueta(tipo), "motivo", motivo).increment();
  }

  public void errorDeProcesamiento(String tipo) {
    registry.counter(ERRORES, "tipo", etiqueta(tipo)).increment();
  }

  public void consulta(String endpoint, String metodo) {
    registry.counter(REQUESTS, "endpoint", endpoint, "metodo", metodo).increment();
  }

  static String etiqueta(String tipo) {
    if (tipo == null) return OTRO;
    String mayusculas = tipo.toUpperCase();
    return TIPOS_CONOCIDOS.contains(mayusculas) ? mayusculas : OTRO;
  }
}
