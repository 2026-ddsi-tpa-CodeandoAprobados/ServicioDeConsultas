package ar.edu.utn.dds.k3003.consultas.metrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Cuenta los pedidos a cada endpoint de {@code /consultas/**}. La etiqueta {@code endpoint} es el patrón
 * de la ruta ({@code /consultas/donador/{donadorId}}), no la URL concreta, para que cada id no sea una
 * serie distinta.
 */
@Component
public class ConsultasRequestInterceptor implements HandlerInterceptor {

  private final ConsultasMetrics metricas;

  public ConsultasRequestInterceptor(ConsultasMetrics metricas) {
    this.metricas = metricas;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    Object patron = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (patron != null) {
      metricas.consulta(String.valueOf(patron), request.getMethod());
    }
    return true;
  }
}
