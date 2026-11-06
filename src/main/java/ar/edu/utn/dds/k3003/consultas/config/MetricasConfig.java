package ar.edu.utn.dds.k3003.consultas.config;

import ar.edu.utn.dds.k3003.consultas.metrics.ConsultasRequestInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MetricasConfig implements WebMvcConfigurer {

  private final ConsultasRequestInterceptor interceptor;

  public MetricasConfig(ConsultasRequestInterceptor interceptor) {
    this.interceptor = interceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(interceptor).addPathPatterns("/consultas/**");
  }
}
