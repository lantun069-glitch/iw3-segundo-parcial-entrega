package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Contrato de entrada para la recepcion de una orden (creacion en estado
 * 1). estado, id interno, fechaRecepcion y el actor del historial NO
 * se toman de este DTO: el servidor los genera.
 *
 * fechaCargaPrevista se asume en hora local America/Argentina/Cordoba,
 * sin offset explicito (ISO-8601 local, ej. "2026-11-03T08:30:00"). No
 * se interpreta ni convierte ninguna otra zona.
 *
 * presetKg y capacidadKg: DECIMAL(15,3) en base de datos. @Digits valida
 * que no lleguen mas de 3 decimales ni mas de 12 digitos enteros, para no
 * redondear en silencio un valor que no entra en esa precision.
 */
@Getter
@Setter
public class OrdenRecepcionRequest {

	@NotNull
	private Integer orderNumber;

	@NotNull
	@Valid
	private CamionDTO camion;

	@NotNull
	@Valid
	private ChoferDTO chofer;

	@NotNull
	@Valid
	private ClienteDTO cliente;

	@NotNull
	@Valid
	private ProductoDTO producto;

	@NotNull
	private LocalDateTime fechaCargaPrevista;

	@NotNull
	@Positive
	@Digits(integer = 12, fraction = 3)
	private BigDecimal presetKg;

	@Getter
	@Setter
	public static class CamionDTO {
		@NotBlank
		@Size(max = 100)
		private String externalCode;
		@NotBlank
		@Size(max = 20)
		private String patente;
		@Size(max = 255)
		private String descripcion;
		@NotNull
		@Positive
		@Digits(integer = 12, fraction = 3)
		private BigDecimal capacidadKg;
	}

	@Getter
	@Setter
	public static class ChoferDTO {
		@NotBlank
		@Size(max = 100)
		private String externalCode;
		@NotBlank
		@Size(max = 100)
		private String nombre;
		@NotBlank
		@Size(max = 100)
		private String apellido;
		@Size(max = 50)
		private String documento;
	}

	@Getter
	@Setter
	public static class ClienteDTO {
		@NotBlank
		@Size(max = 100)
		private String externalCode;
		@NotBlank
		@Size(max = 150)
		private String razonSocial;
		@Size(max = 150)
		private String contacto;
	}

	@Getter
	@Setter
	public static class ProductoDTO {
		@NotBlank
		@Size(max = 100)
		private String externalCode;
		@NotBlank
		@Size(max = 150)
		private String nombre;
		@Size(max = 255)
		private String descripcion;
	}
}
