package ar.edu.iua.iw3.ordenes.controllers.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Respuesta del cierre explicito 2->3. fechaFinCarga es la ultima
 * lectura VALIDA recibida (fechaUltimaLectura al momento del cierre), no
 * el instante de esta llamada - decision del equipo, ver docs/api.md.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CierreResponse {
	private Integer orderNumber;
	private Integer estado;
	private LocalDateTime fechaFinCarga;
}
