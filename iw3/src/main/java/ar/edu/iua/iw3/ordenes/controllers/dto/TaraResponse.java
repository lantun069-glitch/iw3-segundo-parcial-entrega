package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta de la tara exitosa. claveActivacion se devuelve unicamente
 * aqui (no en la respuesta de activacion ni en ningun log) porque es la
 * unica forma de comunicarla al instrumento simulado.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaraResponse {
	private Integer orderNumber;
	private Integer estado;
	private BigDecimal taraKg;
	private LocalDateTime fechaTara;
	private Integer claveActivacion;
}
