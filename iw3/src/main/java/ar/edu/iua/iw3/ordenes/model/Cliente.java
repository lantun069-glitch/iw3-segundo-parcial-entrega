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
 * contacto es texto libre: no se exige formato de email, porque la
 * fuente solo pide un dato de contacto opcional, sin especificar su
 * formato.
 */
@Entity
@Table(name = "clientes")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Cliente {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false, length = 100)
	private String externalCode;

	@Column(nullable = false, length = 150)
	private String razonSocial;

	@Column(length = 150)
	private String contacto;
}
