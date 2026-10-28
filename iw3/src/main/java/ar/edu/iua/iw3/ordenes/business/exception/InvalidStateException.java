package ar.edu.iua.iw3.ordenes.business.exception;

import lombok.Builder;
import lombok.NoArgsConstructor;

/**
 * La orden existe pero no esta en el estado requerido para la operacion
 * solicitada (p. ej. tara fuera de estado 1, activacion fuera de estado
 * 2). Especifica del dominio de ordenes, no de business/execption
 * docente: no se modifica esa taxonomia heredada para este caso nuevo.
 * Se mapea a 409 en el controller.
 */
@NoArgsConstructor
public class InvalidStateException extends Exception {

	private static final long serialVersionUID = 1L;

	@Builder
	public InvalidStateException(String message, Throwable ex) {
		super(message, ex);
	}

	@Builder
	public InvalidStateException(String message) {
		super(message);
	}
}
