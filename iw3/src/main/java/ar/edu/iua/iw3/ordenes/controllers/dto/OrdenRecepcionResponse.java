package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta minima de la recepcion: no devuelve el grafo completo
 * (camion/chofer/cliente/producto/historial), solo lo pedido.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrdenRecepcionResponse {
	private Long id;
	private Integer orderNumber;
	private Integer estado;
	private LocalDateTime fechaRecepcion;
}
