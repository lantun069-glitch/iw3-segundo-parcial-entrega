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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Orden de carga a lo largo de todo su ciclo de vida (estados 1 a 4):
 * sus referencias (camion/chofer/cliente/producto), el snapshot de
 * telemetria y los datos de cierre y pesaje final.
 *
 * uk_orden_order_number tiene un nombre explicito (no autogenerado) para
 * poder distinguir, en OrdenBusiness, esta restriccion de integridad de
 * cualquier otra ante una colision concurrente de orderNumber.
 *
 * Precision de los campos de telemetria (DECISION DEL EQUIPO):
 * masaAcumuladaKg y caudalKgH reutilizan DECIMAL(15,3), igual que
 * taraKg/presetKg. densidad usa DECIMAL(10,4): precision deliberadamente
 * generosa porque la fuente es ambigua entre "valor entre 0 y 1" y
 * "kg/m3" (ambiguedad sin resolver, ver docs/fuentes.md) - esta escala
 * no toma partido en esa ambiguedad, solo evita que un valor realista
 * de cualquiera de las dos lecturas posibles se trunque en silencio.
 * temperaturaC usa DECIMAL(6,2), generosa para admitir valores
 * negativos sin restringir un rango fisico no pedido por la fuente.
 */
@Entity
@Table(name = "ordenes", uniqueConstraints = @UniqueConstraint(name = "uk_orden_order_number", columnNames = "order_number"))
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Orden {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_number", nullable = false)
	private Integer orderNumber;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Camion camion;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Chofer chofer;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Cliente cliente;

	@ManyToOne(optional = false)
	@JoinColumn(nullable = false)
	private Producto producto;

	/**
	 * Se asume recibida en hora local America/Argentina/Cordoba, sin
	 * offset explicito (ISO-8601 local, ej. 2026-11-03T08:30:00). No se
	 * convierte ninguna zona: ver docs/api.md.
	 */
	@Column(nullable = false)
	private LocalDateTime fechaCargaPrevista;

	@Column(nullable = false, precision = 15, scale = 3)
	private BigDecimal presetKg;

	@Column(nullable = false)
	private Integer estado;

	@Column(nullable = false)
	private LocalDateTime fechaRecepcion;

	/** Pesaje inicial (camion vacio), registrado en la transicion 1->2. */
	@Column(precision = 15, scale = 3)
	private BigDecimal taraKg;

	@Column
	private LocalDateTime fechaTara;

	/**
	 * Numero entero de 5 digitos [10000,99999], generado al registrar la
	 * tara. No necesita ser unico entre ordenes: la activacion se valida
	 * contra la orden identificada por orderNumber, no se busca por clave.
	 * Nunca se expone en logs ni en observaciones de HistorialEstado.
	 */
	@Column
	private Integer claveActivacion;

	/**
	 * Instante en que la API acepto la clave de activacion correcta. No se
	 * equipara automaticamente al inicio fisico real de la carga: esa
	 * relacion con la primera telemetria valida se resuelve en la proxima
	 * unidad. La activacion es idempotente: este valor se fija una sola
	 * vez y no se sobreescribe en activaciones posteriores de la misma
	 * orden.
	 */
	@Column
	private LocalDateTime fechaActivacion;

	/**
	 * Snapshot de telemetria: ultimo valor VALIDO recibido de cada
	 * atributo (ver OrdenBusiness.registrarLectura para las reglas de
	 * descarte). Null hasta que exista al menos una lectura aceptada; no
	 * se inicializan en cero. Una lectura descartada no modifica ninguno
	 * de estos campos ni fechaUltimaLectura.
	 */
	@Column(precision = 15, scale = 3)
	private BigDecimal masaAcumuladaKg;

	@Column(precision = 10, scale = 4)
	private BigDecimal densidad;

	@Column(precision = 6, scale = 2)
	private BigDecimal temperaturaC;

	@Column(precision = 15, scale = 3)
	private BigDecimal caudalKgH;

	@Column
	private LocalDateTime fechaUltimaLectura;

	/**
	 * Instante de procesamiento de la primera lectura aceptada para esta
	 * orden (generado por el servidor, no por el instrumento). Es una
	 * interpretacion del equipo de "el momento que empece a recibir la
	 * carga" de la consigna, no una definicion literal inequivoca de
	 * "primera lectura valida" (ver docs/api.md). Se fija una sola vez;
	 * lecturas aceptadas posteriores no lo modifican. No se equipara a
	 * fechaActivacion.
	 */
	@Column
	private LocalDateTime fechaInicioCarga;

	/**
	 * Fin de carga: fijado por el cierre explicito 2->3 a partir de
	 * fechaUltimaLectura (la ultima lectura VALIDA recibida), no del
	 * instante en que se invoca el endpoint de cierre - DECISION DEL
	 * EQUIPO, ver docs/api.md. El momento real de esa invocacion queda
	 * igualmente auditado como fechaHora del HistorialEstado de la
	 * transicion a 3. Null hasta el cierre.
	 */
	@Column
	private LocalDateTime fechaFinCarga;

	/**
	 * Pesaje final (camion cargado), registrado en la transicion 3->4.
	 * Misma precision DECIMAL(15,3) que taraKg/presetKg.
	 */
	@Column(precision = 15, scale = 3)
	private BigDecimal pesoBrutoKg;

	@Column
	private LocalDateTime fechaPesajeFinal;
}
