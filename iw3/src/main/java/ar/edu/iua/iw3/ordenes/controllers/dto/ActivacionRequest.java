package ar.edu.iua.iw3.ordenes.controllers.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Contrato de entrada para la activacion. El rango [10000,99999] se
 * valida aqui (400 por validacion); que la clave coincida con la de la
 * orden es una regla de negocio distinta (400 por InvalidActivationKeyException).
 */
@Getter
@Setter
public class ActivacionRequest {

	@NotNull
	@Min(10000)
	@Max(99999)
	private Integer clave;
}
