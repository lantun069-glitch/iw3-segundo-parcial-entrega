package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Contrato de entrada para registrar la tara (pesaje inicial, transicion
 * 1->2). No acepta estado, clave, fechaTara ni fechaRecepcion: el
 * servidor los genera. Misma precision que presetKg/capacidadKg
 * (DECIMAL(15,3) en base de datos).
 */
@Getter
@Setter
public class TaraRequest {

	@NotNull
	@Positive
	@Digits(integer = 12, fraction = 3)
	private BigDecimal taraKg;
}
