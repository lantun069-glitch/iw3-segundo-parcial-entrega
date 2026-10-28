package ar.edu.iua.iw3.ordenes.model;

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
 * Auditoria de transiciones de estado de una Orden: una fila por cambio
 * de estado, con fecha/hora, usuario o proceso que lo realizo, y una
 * observacion opcional. Auditar tambien la CREACION (transicion a
 * estado 1) es una decision del equipo: la consigna exige auditar "cada
 * cambio de estado", y la creacion es el primero del ciclo de vida de
 * la orden (ver docs/decisiones.md, seccion Auditoria).
 */
@Entity
@Table(name = "historial_estados")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class HistorialEstado {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Orden orden;

	@Column(nullable = false)
	private Integer estadoNuevo;

	@Column(nullable = false)
	private LocalDateTime fechaHora;

	@Column(nullable = false, length = 150)
	private String usuarioOProceso;

	@Column(length = 255)
	private String observacion;
}
