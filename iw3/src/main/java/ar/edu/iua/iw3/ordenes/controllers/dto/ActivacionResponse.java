package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta de la activacion exitosa. Idempotente mientras la orden siga
 * en estado 2: una activacion repetida con clave correcta devuelve el
 * mismo presetKg y la misma fechaActivacion ya fijada.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivacionResponse {
	private Integer orderNumber;
	private BigDecimal presetKg;
	private LocalDateTime fechaActivacion;
}
