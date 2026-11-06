package ar.edu.utn.dds.k3003.consultas.documents;

/**
 * Una fila del ranking de donadores con más quejas (consulta 4, segunda parte). El pipeline proyecta solo
 * estos campos de {@code donadores}.
 */
public record DonadorQuejasResumen(
    String id, String nombre, String estado, String categoria, Integer quejasRecibidas) {}
