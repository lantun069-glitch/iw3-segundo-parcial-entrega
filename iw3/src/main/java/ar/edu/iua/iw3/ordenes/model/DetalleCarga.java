package ar.edu.iua.iw3.ordenes.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Historico de telemetria: una fila por lectura ACEPTADA (ver
 * OrdenBusiness.registrarLectura). Una lectura descartada no genera
 * fila aqui. En esta version se guarda cada lectura aceptada, sin
 * frecuencia configurable ni deduplicacion (ver docs/api.md, snapshot
 * vs. historico). Mismas precisiones que el snapshot de Orden (ver
 * Orden.java).
 */
@Entity
@Table(name = "detalles_carga")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DetalleCarga {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Orden orden;

	@Column(nullable = false, precision = 15, scale = 3)
	private BigDecimal masaAcumuladaKg;

	@Column(nullable = false, precision = 10, scale = 4)
	private BigDecimal densidad;

	@Column(nullable = false, precision = 6, scale = 2)
	private BigDecimal temperaturaC;

	@Column(nullable = false, precision = 15, scale = 3)
	private BigDecimal caudalKgH;

	@Column(nullable = false)
	private LocalDateTime fechaLectura;
}
