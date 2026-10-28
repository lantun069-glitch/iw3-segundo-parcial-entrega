package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Contrato de entrada para una lectura de telemetria. Los cuatro
 * campos son obligatorios, pero deliberadamente SIN @Positive/@DecimalMin:
 * caudal/masa no positivos son un caso de negocio esperado (se
 * descartan en OrdenBusiness.registrarLectura, no se rechazan con 400,
 * ver docs/api.md) y densidad/temperatura no tienen un rango confirmado
 * por la fuente (ver docs/fuentes.md). No acepta timestamp: el servidor
 * lo genera.
 */
@Getter
@Setter
public class LecturaRequest {

	@NotNull
	@Digits(integer = 12, fraction = 3)
	private BigDecimal masaAcumuladaKg;

	@NotNull
	@Digits(integer = 6, fraction = 4)
	private BigDecimal densidad;

	@NotNull
	@Digits(integer = 4, fraction = 2)
	private BigDecimal temperaturaC;

	@NotNull
	@Digits(integer = 12, fraction = 3)
	private BigDecimal caudalKgH;
}
