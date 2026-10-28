package ar.edu.iua.iw3.ordenes.business.exception;

import lombok.Builder;
import lombok.NoArgsConstructor;

/**
 * La clave de activacion recibida no coincide con la generada para la
 * orden. El mensaje nunca debe incluir el valor de ninguna de las dos
 * claves (ni la recibida ni la correcta). Se mapea a 400 en el
 * controller: es un valor sintacticamente valido pero rechazado por una
 * regla de negocio, mismo criterio que el resto de los 400 ya usados en
 * OrdenRestController.
 */
@NoArgsConstructor
public class InvalidActivationKeyException extends Exception {

	private static final long serialVersionUID = 1L;

	@Builder
	public InvalidActivationKeyException(String message) {
		super(message);
	}
}
