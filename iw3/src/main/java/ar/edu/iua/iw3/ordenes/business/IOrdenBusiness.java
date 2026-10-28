package ar.edu.iua.iw3.ordenes.business;

import java.math.BigDecimal;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.FoundException;
import ar.edu.iua.iw3.business.execption.NotFoundException;
import ar.edu.iua.iw3.ordenes.business.exception.InvalidActivationKeyException;
import ar.edu.iua.iw3.ordenes.business.exception.InvalidStateException;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest;
import ar.edu.iua.iw3.ordenes.model.Orden;

public interface IOrdenBusiness {
	/**
	 * Sincroniza camion/chofer/cliente/producto por externalCode, crea la
	 * orden en estado 1 y registra su HistorialEstado, en una unica
	 * transaccion. actor viene del contexto de seguridad, nunca del JSON.
	 */
	public Orden recibirOrden(OrdenRecepcionRequest request, String actor) throws FoundException, BusinessException;

	/**
	 * Registra la tara (pesaje inicial), genera la clave de activacion de
	 * 5 digitos y transiciona la orden de estado 1 a estado 2, registrando
	 * esa transicion en HistorialEstado. Requiere que la orden exista y
	 * este en estado 1; bloquea la fila de la orden (PESSIMISTIC_WRITE)
	 * dentro de la transaccion para serializar taras concurrentes sobre la
	 * misma orderNumber.
	 */
	public Orden registrarTara(Integer orderNumber, BigDecimal taraKg, String actor)
			throws NotFoundException, InvalidStateException, BusinessException;

	/**
	 * Valida la clave de activacion contra la orden identificada por
	 * orderNumber (no busca por clave) y, si coincide, devuelve el preset.
	 * Requiere que la orden exista y este en estado 2. Idempotente: si ya
	 * estaba activada, vuelve a validar la clave igual, pero no sobre-
	 * escribe fechaActivacion. No modifica el estado ni agrega una fila en
	 * HistorialEstado. Misma proteccion de concurrencia que registrarTara.
	 */
	public Orden activar(Integer orderNumber, Integer clave)
			throws NotFoundException, InvalidStateException, InvalidActivationKeyException, BusinessException;

	/**
	 * Procesa una lectura de telemetria. Requiere que la orden exista,
	 * este en estado 2 y ya tenga fechaActivacion (decision del equipo,
	 * no texto literal de la consigna: ver docs/api.md). Si
	 * caudalKgH&lt;=0, masaAcumuladaKg&lt;=0
	 * o masaAcumuladaKg es estrictamente menor que la ultima masa
	 * aceptada (si existe antecedente), la lectura se descarta: no
	 * modifica snapshot ni genera DetalleCarga, y se devuelve el motivo
	 * (prioridad caudal_no_positivo &gt; masa_no_positiva &gt;
	 * masa_decreciente). Si es aceptada, actualiza el snapshot de Orden,
	 * fija fechaInicioCarga la primera vez (sin sobreescribirla despues)
	 * y agrega una fila en DetalleCarga, todo con la misma fecha/hora.
	 * Misma proteccion de concurrencia (PESSIMISTIC_WRITE) que
	 * registrarTara/activar.
	 */
	public LecturaResultado registrarLectura(Integer orderNumber, BigDecimal masaAcumuladaKg, BigDecimal densidad,
			BigDecimal temperaturaC, BigDecimal caudalKgH)
			throws NotFoundException, InvalidStateException, BusinessException;

	/**
	 * Cierre explicito de carga, transicion 2-&gt;3. Requiere que la
	 * orden exista, este en estado 2, ya tenga fechaActivacion y al menos
	 * una lectura aceptada (fechaUltimaLectura no nula) - las dos ultimas
	 * son precondiciones adicionales de decision del equipo, no texto
	 * literal de la consigna (ver docs/api.md). Un cierre repetido se
	 * rechaza por el mismo chequeo de estado (la orden ya quedo en 3).
	 * fechaFinCarga se fija igual a fechaUltimaLectura, no al instante de
	 * esta llamada; ese instante real se audita por separado como fechaHora del
	 * HistorialEstado. Misma proteccion de concurrencia
	 * (PESSIMISTIC_WRITE) que registrarTara/activar/registrarLectura: no
	 * bloquea nuevas lecturas por si sola, el cierre si cambia el estado
	 * de forma que registrarLectura las rechaza despues.
	 */
	public Orden cerrarCarga(Integer orderNumber, String actor)
			throws NotFoundException, InvalidStateException, BusinessException;

	/**
	 * Pesaje final (bruto), transicion 3-&gt;4. Requiere que la
	 * orden exista y este en estado 3; un pesaje final repetido se
	 * rechaza por el mismo chequeo de estado (la orden ya quedo en 4).
	 * Guarda pesoBrutoKg y fechaPesajeFinal (instante real de esta
	 * llamada, no derivado de otra fecha), transiciona a 4 y audita la
	 * transicion en HistorialEstado (actor, misma fecha/hora). Devuelve
	 * la conciliacion calculada en la misma operacion, con el mismo
	 * calculo que consultarConciliacion (ver OrdenBusiness.calcularConciliacion).
	 * Misma proteccion de concurrencia (PESSIMISTIC_WRITE) que
	 * registrarTara/activar/registrarLectura/cerrarCarga.
	 */
	public Conciliacion registrarPesajeFinal(Integer orderNumber, BigDecimal pesoBrutoKg, String actor)
			throws NotFoundException, InvalidStateException, BusinessException;

	/**
	 * Consulta de la conciliacion, sin escrituras. Requiere que la
	 * orden exista y este en estado 4; se puede llamar repetidas veces,
	 * siempre devuelve el mismo calculo a partir de los valores ya
	 * persistidos (tara, pesoBrutoKg, ultima masa acumulada/temperatura/
	 * densidad/caudal del snapshot). No usa bloqueo pesimista: es una
	 * lectura simple, no participa de ninguna transicion de estado.
	 */
	public Conciliacion consultarConciliacion(Integer orderNumber)
			throws NotFoundException, InvalidStateException, BusinessException;
}
