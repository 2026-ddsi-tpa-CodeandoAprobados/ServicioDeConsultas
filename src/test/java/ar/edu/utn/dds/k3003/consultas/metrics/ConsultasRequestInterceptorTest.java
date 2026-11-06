package ar.edu.utn.dds.k3003.consultas.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class ConsultasRequestInterceptorTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final ConsultasRequestInterceptor interceptor =
      new ConsultasRequestInterceptor(new ConsultasMetrics(registry));

  private void pedir(String metodo, String url, String patron) {
    MockHttpServletRequest request = new MockHttpServletRequest(metodo, url);
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, patron);
    assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
  }

  @Test
  void cadaEndpointSeCuentaPorSuPatronNoPorLaUrl() {
    pedir("GET", "/consultas/donador/12", "/consultas/donador/{donadorId}");
    pedir("GET", "/consultas/donador/15", "/consultas/donador/{donadorId}");
    pedir("GET", "/consultas/calidad-donaciones", "/consultas/calidad-donaciones");

    assertEquals(2, registry.get(ConsultasMetrics.REQUESTS)
        .tags("endpoint", "/consultas/donador/{donadorId}", "metodo", "GET").counter().count());
    assertEquals(1, registry.get(ConsultasMetrics.REQUESTS)
        .tags("endpoint", "/consultas/calidad-donaciones").counter().count());
  }

  @Test
  void sinPatronNoSeCuentaNada() {
    assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/x"), new MockHttpServletResponse(), new Object()));
    assertEquals(0, registry.find(ConsultasMetrics.REQUESTS).counters().size());
  }
}
