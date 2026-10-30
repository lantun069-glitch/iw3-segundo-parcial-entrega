package ar.edu.iua.iw3.ordenes.model;

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
 * Producto transportado en una orden de carga (ej. GLP). Se sincroniza
 * por {@code externalCode} al recibir la orden (ver docs/api.md): si
 * no existe se crea, si existe se actualiza.
 */
@Entity
@Table(name = "productos_orden")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Producto {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false, length = 100)
	private String externalCode;

	@Column(nullable = false, length = 150)
	private String nombre;

	@Column(length = 255)
	private String descripcion;
}
