package ar.edu.utn.dds.k3003.consultas.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.edu.utn.dds.k3003.consultas.repositories.DepositoDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonacionDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.DonadorDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.repositories.NecesidadDocumentRepository;
import ar.edu.utn.dds.k3003.consultas.service.ConsultaService;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

  @Mock private ConsultaService consultaService;
  @Mock private DonadorDocumentRepository donadorRepository;
  @Mock private NecesidadDocumentRepository necesidadRepository;
  @Mock private DepositoDocumentRepository depositoRepository;
  @Mock private DonacionDocumentRepository donacionRepository;

  private AdminController controller(boolean habilitado) {
    return new AdminController(consultaService, donadorRepository, necesidadRepository, depositoRepository,
        donacionRepository, habilitado);
  }

  @Test
  void apagadoElResetResponde403YNoBorraNada() {
    var respuesta = controller(false).resetDatabase();

    assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode());
    verifyNoInteractions(consultaService);
  }

  @Test
  void apagadoElSeedResponde403YNoCargaNada() {
    var respuesta = controller(false).seedDemoData();

    assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode());
    verifyNoInteractions(donadorRepository, necesidadRepository, depositoRepository, donacionRepository);
  }

  @Test
  void encendidoElResetBorraElModeloDeLectura() {
    var respuesta = controller(true).resetDatabase();

    assertEquals(HttpStatus.OK, respuesta.getStatusCode());
    verify(consultaService).borrarTodo();
  }

  @Test
  void encendidoElSeedCargaLosDatosDeDemoConDonacionesParaLaConsulta4() {
    var respuesta = controller(true).seedDemoData();

    assertEquals(HttpStatus.OK, respuesta.getStatusCode());
    assertEquals(25, respuesta.getBody().get("donacionesCreadas"));
    verify(donacionRepository).saveAll(anyIterable());
  }

  @Test
  void porDefectoLasOperacionesDeAdministracionEstanApagadas() throws IOException {
    StandardEnvironment entorno = new StandardEnvironment();
    entorno.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    entorno.getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));

    assertThat(entorno.getProperty("app.admin.enabled", Boolean.class)).isFalse();
  }
}
