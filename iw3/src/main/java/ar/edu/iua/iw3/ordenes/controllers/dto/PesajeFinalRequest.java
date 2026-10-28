package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Contrato de entrada para el pesaje final (bruto, transicion 3->4).
 * No acepta estado, fechaPesajeFinal ni el actor del historial: el
 * servidor los genera. Misma precision que taraKg/presetKg
 * (DECIMAL(15,3) en base de datos).
 */
@Getter
@Setter
public class PesajeFinalRequest {

	@NotNull
	@Positive
	@Digits(integer = 12, fraction = 3)
	private BigDecimal pesoBrutoKg;
}
