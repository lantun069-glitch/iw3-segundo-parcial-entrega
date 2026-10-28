package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta de una lectura de telemetria, aceptada o descartada. Los
 * campos de snapshot siempre reflejan el estado ACTUAL de la orden
 * (el de esta lectura si fue aceptada, o el previo sin cambios si fue
 * descartada). Son null mientras la orden no tenga ninguna lectura
 * aceptada todavia: no se inicializan en cero.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LecturaResponse {
	private Integer orderNumber;
	private boolean aceptada;
	private String motivoDescarte;
	private BigDecimal masaAcumuladaKg;
	private BigDecimal densidad;
	private BigDecimal temperaturaC;
	private BigDecimal caudalKgH;
	private LocalDateTime fechaUltimaLectura;
	private LocalDateTime fechaInicioCarga;
}
