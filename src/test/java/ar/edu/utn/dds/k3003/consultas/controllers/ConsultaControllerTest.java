package ar.edu.utn.dds.k3003.consultas.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.edu.utn.dds.k3003.consultas.documents.CalidadDonacionDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DepositoDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorDocument;
import ar.edu.utn.dds.k3003.consultas.documents.DonadorQuejasResumen;
import ar.edu.utn.dds.k3003.consultas.documents.NecesidadUrgenteDocument;
import ar.edu.utn.dds.k3003.consultas.service.ConsultaService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
public class ConsultaControllerTest {

  @Mock private ConsultaService consultaService;

  private ConsultaController controller;

  @BeforeEach
  void setUp() {
    controller = new ConsultaController(consultaService);
  }

  @Test
  void testGetFichaDonadorOk() {
    DonadorDocument doc = DonadorDocument.builder().id("12").nombre("Lucia").build();
    when(consultaService.obtenerFichaIntegralDonador("12")).thenReturn(Optional.of(doc));

    ResponseEntity<DonadorDocument> res = controller.getFichaDonador("12");
    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals("Lucia", res.getBody().getNombre());
  }

  @Test
  void testGetFichaDonadorNotFound() {
    when(consultaService.obtenerFichaIntegralDonador("999")).thenReturn(Optional.empty());

    ResponseEntity<DonadorDocument> res = controller.getFichaDonador("999");
    assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
  }

  @Test
  void testGetNecesidadesUrgentes() {
    when(consultaService.obtenerNecesidadesUrgentes(null, null, null, null))
        .thenReturn(List.of(NecesidadUrgenteDocument.builder().id("n1").build()));

    ResponseEntity<List<NecesidadUrgenteDocument>> res =
        controller.getNecesidadesUrgentes(null, null, null, null);
    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals(1, res.getBody().size());
  }

  @Test
  void testGetEstadoDepositos() {
    when(consultaService.obtenerEstadoTodosDepositos())
        .thenReturn(List.of(DepositoDocument.builder().id("d1").build()));

    ResponseEntity<List<DepositoDocument>> res = controller.getEstadoDepositos();
    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals(1, res.getBody().size());
  }

  @Test
  void testGetCalidadDonaciones() {
    when(consultaService.obtenerCalidad("Alimentos", null))
        .thenReturn(List.of(CalidadDonacionDocument.builder().id("Alimentos_Pastas").build()));

    ResponseEntity<List<CalidadDonacionDocument>> res = controller.getCalidadDonaciones("Alimentos", null);
    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals(1, res.getBody().size());
  }

  @Test
  void testGetCalidadDonacionesPorCategoriaYSubcategoria() {
    when(consultaService.obtenerCalidad("Alimentos", "Pastas"))
        .thenReturn(List.of(CalidadDonacionDocument.builder().id("Alimentos_Pastas").build()));

    ResponseEntity<List<CalidadDonacionDocument>> res = controller.getCalidadDonaciones("Alimentos", "Pastas");
    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals("Alimentos_Pastas", res.getBody().get(0).getId());
  }

  @Test
  void testGetDonadoresConMasQuejasPipeline() {
    when(consultaService.obtenerDonadoresConMasQuejas(null))
        .thenReturn(List.of(new DonadorQuejasResumen("12", "Lucia", "SOSPECHOSO", "OCASIONAL", 5)));

    ResponseEntity<List<DonadorQuejasResumen>> res = controller.getDonadoresConMasQuejas(null);
    assertEquals(HttpStatus.OK, res.getStatusCode());
    assertEquals(1, res.getBody().size());
  }

  @Test
  void testGetDonadoresConMasQuejasConLimite() {
    when(consultaService.obtenerDonadoresConMasQuejas(3)).thenReturn(List.of());

    assertEquals(HttpStatus.OK, controller.getDonadoresConMasQuejas(3).getStatusCode());
    verify(consultaService).obtenerDonadoresConMasQuejas(3);
  }

  @Test
  void testGetDonadoresConMasQuejasConLimiteInvalido() {
    assertEquals(HttpStatus.BAD_REQUEST, controller.getDonadoresConMasQuejas(0).getStatusCode());
    verifyNoInteractions(consultaService);
  }
}
