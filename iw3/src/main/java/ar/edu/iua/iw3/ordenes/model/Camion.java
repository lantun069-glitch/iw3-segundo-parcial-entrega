package ar.edu.iua.iw3.ordenes.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Camion = unidad de carga completa. Aclaracion docente transmitida por
 * el equipo: "cisterna" se toma como el camion completo, sin
 * compartimentos; no hay una entidad Cisterna ni una coleccion de
 * capacidades que sumar. capacidadKg es un dato directo (ver docs/api.md).
 */
@Entity
@Table(name = "camiones")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Camion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false, length = 100)
	private String externalCode;

	@Column(nullable = false, length = 20)
	private String patente;

	@Column(length = 255)
	private String descripcion;

	@Column(nullable = false, precision = 15, scale = 3)
	private BigDecimal capacidadKg;
}
