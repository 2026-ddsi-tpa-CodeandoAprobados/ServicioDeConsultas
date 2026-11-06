package ar.edu.utn.dds.k3003.consultas.service;

import ar.edu.utn.dds.k3003.consultas.documents.AsignacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonacionDocument;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Estadísticas de un depósito a partir de sus donaciones y de las asignaciones de esas donaciones: cantidad
 * de asignaciones por estado, tiempo promedio entre el ingreso de una donación y su entrega, y entidades
 * distintas atendidas. El rango de fechas (opcional) se aplica a las entregas.
 */
public final class EstadisticasDeposito {

  private final Map<String, Integer> asignacionesPorEstado = new LinkedHashMap<>();
  private final Set<String> entidadesDistintas = new HashSet<>();
  private Double tiempoPromedioEntregaMinutos;

  private EstadisticasDeposito() {
    asignacionesPorEstado.put("ASIGNADA", 0);
    asignacionesPorEstado.put("COMPLETADA", 0);
  }

  public static EstadisticasDeposito calcular(
      List<DonacionDocument> donaciones, List<AsignacionDocument> asignaciones, Instant desde, Instant hasta) {
    Map<String, DonacionDocument> porId = new HashMap<>();
    for (DonacionDocument d : donaciones) {
      porId.putIfAbsent(d.getId(), d);
    }
    EstadisticasDeposito resultado = new EstadisticasDeposito();
    long totalMilisegundos = 0;
    int entregas = 0;
    for (AsignacionDocument a : asignaciones) {
      DonacionDocument donacion = porId.get(a.getDonacionId());
      if (donacion == null) continue; // la asignación es de una donación de otro depósito
      String estado = a.getEstado() == null ? "DESCONOCIDO" : a.getEstado().toUpperCase();
      resultado.asignacionesPorEstado.merge(estado, 1, Integer::sum);
      boolean entregada = "COMPLETADA".equals(estado) && a.getFechaEntrega() != null;
      if (entregada && enRango(a.getFechaEntrega(), desde, hasta)) {
        if (a.getEntidadId() != null) resultado.entidadesDistintas.add(a.getEntidadId());
        if (donacion.getFechaIngreso() != null) {
          totalMilisegundos += Duration.between(donacion.getFechaIngreso(), a.getFechaEntrega()).toMillis();
          entregas++;
        }
      }
    }
    if (entregas > 0) {
      resultado.tiempoPromedioEntregaMinutos = totalMilisegundos / (double) entregas / 60000.0;
    }
    return resultado;
  }

  private static boolean enRango(Instant fecha, Instant desde, Instant hasta) {
    return (desde == null || !fecha.isBefore(desde)) && (hasta == null || !fecha.isAfter(hasta));
  }

  public Map<String, Integer> getAsignacionesPorEstado() {
    return asignacionesPorEstado;
  }

  public Set<String> getEntidadesDistintas() {
    return entidadesDistintas;
  }

  public Double getTiempoPromedioEntregaMinutos() {
    return tiempoPromedioEntregaMinutos;
  }
}
