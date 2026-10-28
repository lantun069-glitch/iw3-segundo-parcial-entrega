package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta de conciliacion, devuelta tanto por el pesaje final como por
 * su consulta posterior (mismo calculo, ver
 * OrdenBusiness.calcularConciliacion). ultimaTemperaturaC/ultimaDensidad/
 * ultimoCaudalKgH son los ULTIMOS VALORES del snapshot de telemetria,
 * deliberadamente NO llamados "promedio*": los promedios quedan fuera
 * de esta version, ver docs/api.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConciliacionResponse {
	private Integer orderNumber;
	private BigDecimal taraKg;
	private BigDecimal pesoBrutoKg;
	private BigDecimal netoKg;
	private BigDecimal productoCargadoKg;
	private BigDecimal diferenciaKg;
	private BigDecimal ultimaTemperaturaC;
	private BigDecimal ultimaDensidad;
	private BigDecimal ultimoCaudalKgH;
}
