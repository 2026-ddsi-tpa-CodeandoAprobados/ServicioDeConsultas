package ar.edu.utn.dds.k3003.consultas.support;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.util.SerializationUtils;

/**
 * Repositorios de Spring Data respaldados por un mapa, para probar secuencias de eventos sin MongoDB.
 *
 * <p>Implementa lo que usa el procesamiento de eventos: save, saveAll, findById, existsById, findAll,
 * deleteById, delete, deleteAll, count y los finders simples {@code findByCampo} y {@code findByCampoIn}.
 * Como en Mongo, lo que se guarda y lo que se lee son copias: modificar un documento sin guardarlo no cambia
 * lo guardado.
 */
public final class MongoEnMemoria {

  private MongoEnMemoria() {}

  public static <R> R repositorio(Class<R> tipo) {
    Map<String, Object> datos = new LinkedHashMap<>();
    return Mockito.mock(tipo, invocacion -> responder(datos, invocacion));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Object responder(Map<String, Object> datos, InvocationOnMock invocacion) throws Exception {
    String metodo = invocacion.getMethod().getName();
    Object[] args = invocacion.getArguments();
    switch (metodo) {
      case "save" -> {
        datos.put(idDe(args[0]), copia(args[0]));
        return args[0];
      }
      case "saveAll" -> {
        List<Object> guardados = new ArrayList<>();
        for (Object o : (Iterable) args[0]) {
          datos.put(idDe(o), copia(o));
          guardados.add(o);
        }
        return guardados;
      }
      case "findById" -> {
        return Optional.ofNullable(copia(datos.get(args[0])));
      }
      case "existsById" -> {
        return datos.containsKey(args[0]);
      }
      case "deleteById" -> {
        datos.remove(args[0]);
        return null;
      }
      case "delete" -> {
        datos.remove(idDe(args[0]));
        return null;
      }
      case "count" -> {
        return (long) datos.size();
      }
      case "toString" -> {
        return "repositorio en memoria";
      }
      default -> { }
    }
    if (metodo.equals("findAll") && args.length == 0) {
      return datos.values().stream().map(MongoEnMemoria::copia).toList();
    }
    if (metodo.equals("deleteAll") && args.length == 0) {
      datos.clear();
      return null;
    }
    if (metodo.startsWith("findBy")) {
      return buscar(datos, metodo.substring("findBy".length()), args[0]);
    }
    throw new UnsupportedOperationException("El repositorio en memoria no implementa " + metodo);
  }

  private static List<Object> buscar(Map<String, Object> datos, String criterio, Object valor) throws Exception {
    boolean enLista = criterio.endsWith("In");
    String campo = Character.toLowerCase(criterio.charAt(0))
        + criterio.substring(1, enLista ? criterio.length() - 2 : criterio.length());
    if (!campo.chars().allMatch(Character::isLetterOrDigit)) {
      throw new UnsupportedOperationException("Finder no soportado: findBy" + criterio);
    }
    List<Object> resultado = new ArrayList<>();
    for (Object documento : datos.values()) {
      Object actual = valorDe(documento, campo);
      boolean coincide = enLista ? ((Collection<?>) valor).contains(actual) : Objects.equals(actual, valor);
      if (coincide) resultado.add(copia(documento));
    }
    return resultado;
  }

  private static String idDe(Object documento) throws Exception {
    return String.valueOf(valorDe(documento, "id"));
  }

  private static Object valorDe(Object documento, String campo) throws Exception {
    Field f = documento.getClass().getDeclaredField(campo);
    f.setAccessible(true);
    return f.get(documento);
  }

  @SuppressWarnings("unchecked")
  private static <T> T copia(T documento) {
    return documento == null ? null : (T) SerializationUtils.clone((Serializable) documento);
  }
}
