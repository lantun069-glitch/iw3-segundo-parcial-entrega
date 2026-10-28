package ar.edu.iua.iw3.ordenes.business;

import ar.edu.iua.iw3.ordenes.model.Orden;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Resultado interno de registrar una lectura: no es un DTO de
 * controller (vive en la capa de negocio) ni una excepcion (una
 * lectura descartada no es un error, ver docs/api.md).
 */
@Getter
@AllArgsConstructor
public class LecturaResultado {
	private final Orden orden;
	private final boolean aceptada;
	private final String motivoDescarte;
}
