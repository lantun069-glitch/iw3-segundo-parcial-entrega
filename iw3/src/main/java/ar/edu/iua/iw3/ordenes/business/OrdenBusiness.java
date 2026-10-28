package ar.edu.iua.iw3.ordenes.business;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.FoundException;
import ar.edu.iua.iw3.business.execption.NotFoundException;
import ar.edu.iua.iw3.ordenes.business.exception.InvalidActivationKeyException;
import ar.edu.iua.iw3.ordenes.business.exception.InvalidStateException;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest.CamionDTO;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest.ChoferDTO;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest.ClienteDTO;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest.ProductoDTO;
import ar.edu.iua.iw3.ordenes.model.Camion;
import ar.edu.iua.iw3.ordenes.model.Chofer;
import ar.edu.iua.iw3.ordenes.model.Cliente;
import ar.edu.iua.iw3.ordenes.model.DetalleCarga;
import ar.edu.iua.iw3.ordenes.model.HistorialEstado;
import ar.edu.iua.iw3.ordenes.model.Orden;
import ar.edu.iua.iw3.ordenes.model.Producto;
import ar.edu.iua.iw3.ordenes.model.persistence.CamionRepository;
import ar.edu.iua.iw3.ordenes.model.persistence.ChoferRepository;
import ar.edu.iua.iw3.ordenes.model.persistence.ClienteRepository;
import ar.edu.iua.iw3.ordenes.model.persistence.DetalleCargaRepository;
import ar.edu.iua.iw3.ordenes.model.persistence.HistorialEstadoRepository;
import ar.edu.iua.iw3.ordenes.model.persistence.OrdenRepository;
import ar.edu.iua.iw3.ordenes.model.persistence.ProductoRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OrdenBusiness implements IOrdenBusiness {

	/** Zona horaria fija para toda fecha que genera el servidor
	 * (America/Argentina/Cordoba): no se usa la zona por defecto de la
	 * maquina, para que el resultado no cambie segun donde se despliegue.
	 * Ver docs/api.md, seccion de fechas. */
	private static final ZoneId ZONA_RECEPCION = ZoneId.of("America/Argentina/Cordoba");

	/** Nombre explicito del unique constraint de Orden (ver Orden.java),
	 * para distinguir una colision de orderNumber de cualquier otra
	 * violacion de integridad. No se traduce cualquier error de
	 * integridad a "orden duplicada". */
	private static final String NOMBRE_CONSTRAINT_ORDER_NUMBER = "uk_orden_order_number";

	/** Generacion de la clave de activacion: entero al azar en [10000,99999]. */
	private static final SecureRandom CLAVE_RANDOM = new SecureRandom();
	private static final int CLAVE_MIN = 10000;
	private static final int CLAVE_RANGO = 90000;

	/** Motivos de descarte de una lectura, en orden de prioridad cuando
	 * varios aplican a la vez (ver registrarLectura). */
	private static final String MOTIVO_CAUDAL_NO_POSITIVO = "caudal_no_positivo";
	private static final String MOTIVO_MASA_NO_POSITIVA = "masa_no_positiva";
	private static final String MOTIVO_MASA_DECRECIENTE = "masa_decreciente";

	@Autowired
	private OrdenRepository ordenDAO;
	@Autowired
	private CamionRepository camionDAO;
	@Autowired
	private ChoferRepository choferDAO;
	@Autowired
	private ClienteRepository clienteDAO;
	@Autowired
	private ProductoRepository productoDAO;
	@Autowired
	private HistorialEstadoRepository historialDAO;
	@Autowired
	private DetalleCargaRepository detalleCargaDAO;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Orden recibirOrden(OrdenRecepcionRequest request, String actor) throws FoundException, BusinessException {
		try {
			// Chequeo temprano: evita sincronizar referencias para una orden
			// que ya sabemos que esta duplicada en el camino comun (no es la
			// unica proteccion: la restriccion unica de BD cubre la colision
			// concurrente, ver el catch de DataIntegrityViolationException).
			if (ordenDAO.existsByOrderNumber(request.getOrderNumber())) {
				throw FoundException.builder()
						.message("Ya existe una orden con orderNumber=" + request.getOrderNumber()).build();
			}

			Camion camion = sincronizarCamion(request.getCamion());
			Chofer chofer = sincronizarChofer(request.getChofer());
			Cliente cliente = sincronizarCliente(request.getCliente());
			Producto producto = sincronizarProducto(request.getProducto());

			// Una sola fecha/hora para recepcion e historial de creacion.
			LocalDateTime ahora = LocalDateTime.now(ZONA_RECEPCION);

			Orden orden = new Orden();
			orden.setOrderNumber(request.getOrderNumber());
			orden.setCamion(camion);
			orden.setChofer(chofer);
			orden.setCliente(cliente);
			orden.setProducto(producto);
			orden.setFechaCargaPrevista(request.getFechaCargaPrevista());
			orden.setPresetKg(request.getPresetKg());
			orden.setEstado(1);
			orden.setFechaRecepcion(ahora);

			try {
				orden = ordenDAO.save(orden);
			} catch (DataIntegrityViolationException e) {
				String causa = causaMasEspecifica(e);
				if (causa != null && causa.toLowerCase().contains(NOMBRE_CONSTRAINT_ORDER_NUMBER)) {
					// Colision concurrente: dos solicitudes pasaron el chequeo
					// temprano con el mismo orderNumber; la restriccion de BD
					// es la que realmente lo evita. Rollback por la anotacion
					// @Transactional al relanzar esta excepcion checked.
					throw FoundException.builder()
							.message("Ya existe una orden con orderNumber=" + request.getOrderNumber()).build();
				}
				// Cualquier otra violacion de integridad no se interpreta como
				// "orden duplicada".
				log.error(e.getMessage(), e);
				throw BusinessException.builder().ex(e).build();
			}

			HistorialEstado historial = new HistorialEstado();
			historial.setOrden(orden);
			historial.setEstadoNuevo(1);
			historial.setFechaHora(ahora);
			historial.setUsuarioOProceso(actor);
			historialDAO.save(historial);

			return orden;
		} catch (FoundException | BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Orden registrarTara(Integer orderNumber, BigDecimal taraKg, String actor)
			throws NotFoundException, InvalidStateException, BusinessException {
		try {
			// PESSIMISTIC_WRITE: serializa dos registros de tara concurrentes
			// sobre la misma orderNumber. El segundo en obtener el lock ve el
			// estado ya en 2 (escrito y confirmado por el primero) y rechaza.
			Orden orden = ordenDAO.findByOrderNumberForUpdate(orderNumber)
					.orElseThrow(() -> NotFoundException.builder()
							.message("No existe una orden con orderNumber=" + orderNumber).build());

			if (orden.getEstado() != 1) {
				throw InvalidStateException.builder().message("La orden orderNumber=" + orderNumber
						+ " no esta en estado 1 (estado actual: " + orden.getEstado() + ")").build();
			}

			LocalDateTime ahora = LocalDateTime.now(ZONA_RECEPCION);
			int clave = CLAVE_MIN + CLAVE_RANDOM.nextInt(CLAVE_RANGO);

			orden.setTaraKg(taraKg);
			orden.setFechaTara(ahora);
			orden.setClaveActivacion(clave);
			orden.setEstado(2);
			orden = ordenDAO.save(orden);

			HistorialEstado historial = new HistorialEstado();
			historial.setOrden(orden);
			historial.setEstadoNuevo(2);
			historial.setFechaHora(ahora);
			historial.setUsuarioOProceso(actor);
			historialDAO.save(historial);

			return orden;
		} catch (NotFoundException | InvalidStateException e) {
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Orden activar(Integer orderNumber, Integer clave)
			throws NotFoundException, InvalidStateException, InvalidActivationKeyException, BusinessException {
		try {
			// Mismo bloqueo que registrarTara: dos activaciones correctas
			// concurrentes se serializan; la segunda, tras esperar el lock,
			// encuentra fechaActivacion ya fijada y no la vuelve a escribir.
			Orden orden = ordenDAO.findByOrderNumberForUpdate(orderNumber)
					.orElseThrow(() -> NotFoundException.builder()
							.message("No existe una orden con orderNumber=" + orderNumber).build());

			if (orden.getEstado() != 2) {
				throw InvalidStateException.builder().message("La orden orderNumber=" + orderNumber
						+ " no esta en estado 2 (estado actual: " + orden.getEstado() + ")").build();
			}

			// Se valida siempre, incluso si la orden ya fue activada antes.
			if (!orden.getClaveActivacion().equals(clave)) {
				throw InvalidActivationKeyException.builder().message("Clave de activacion incorrecta").build();
			}

			if (orden.getFechaActivacion() == null) {
				// Truncado a microsegundos para que coincida exactamente con la
				// precision real de la columna (datetime(6)): sin esto, la
				// respuesta de esta misma llamada (con el valor en memoria, en
				// nanosegundos) no coincidia con la de una activacion repetida
				// que relee el valor ya truncado por la base.
				orden.setFechaActivacion(LocalDateTime.now(ZONA_RECEPCION).truncatedTo(ChronoUnit.MICROS));
				orden = ordenDAO.save(orden);
			}

			return orden;
		} catch (NotFoundException | InvalidStateException | InvalidActivationKeyException e) {
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public LecturaResultado registrarLectura(Integer orderNumber, BigDecimal masaAcumuladaKg, BigDecimal densidad,
			BigDecimal temperaturaC, BigDecimal caudalKgH) throws NotFoundException, InvalidStateException, BusinessException {
		try {
			// Mismo bloqueo que registrarTara/activar: serializa lecturas
			// concurrentes sobre la misma orden para que la regla de masa
			// decreciente se evalue siempre contra el ultimo valor realmente
			// confirmado, no contra uno leido antes de que otra transaccion
			// terminara de escribir. Esto ordena el PROCESAMIENTO bajo el
			// lock; no garantiza el orden de llegada por red (ver
			// docs/decisiones.md).
			Orden orden = ordenDAO.findByOrderNumberForUpdate(orderNumber)
					.orElseThrow(() -> NotFoundException.builder()
							.message("No existe una orden con orderNumber=" + orderNumber).build());

			if (orden.getEstado() != 2) {
				throw InvalidStateException.builder().message("La orden orderNumber=" + orderNumber
						+ " no esta en estado 2 (estado actual: " + orden.getEstado() + ")").build();
			}
			if (orden.getFechaActivacion() == null) {
				throw InvalidStateException.builder()
						.message("La orden orderNumber=" + orderNumber + " todavia no fue activada").build();
			}

			// Prioridad explicita cuando varios motivos aplican a la vez:
			// caudal_no_positivo > masa_no_positiva > masa_decreciente.
			String motivoDescarte = null;
			if (caudalKgH.compareTo(BigDecimal.ZERO) <= 0) {
				motivoDescarte = MOTIVO_CAUDAL_NO_POSITIVO;
			} else if (masaAcumuladaKg.compareTo(BigDecimal.ZERO) <= 0) {
				motivoDescarte = MOTIVO_MASA_NO_POSITIVA;
			} else if (orden.getMasaAcumuladaKg() != null && masaAcumuladaKg.compareTo(orden.getMasaAcumuladaKg()) < 0) {
				// Sin antecedente (snapshot todavia null) esta regla no aplica.
				// Masa igual a la anterior no se descarta, solo la estrictamente
				// menor (ver docs/api.md, reglas de descarte).
				motivoDescarte = MOTIVO_MASA_DECRECIENTE;
			}

			if (motivoDescarte != null) {
				// Descartada: no se toca snapshot, fechas ni DetalleCarga.
				return new LecturaResultado(orden, false, motivoDescarte);
			}

			// Misma fecha/hora para snapshot, detalle, y fechaInicioCarga si
			// esta es la primera lectura aceptada de esta orden. Truncada a
			// microsegundos por el mismo motivo que fechaActivacion (ver
			// activar()): que coincida con la precision real de la columna.
			LocalDateTime ahora = LocalDateTime.now(ZONA_RECEPCION).truncatedTo(ChronoUnit.MICROS);

			orden.setMasaAcumuladaKg(masaAcumuladaKg);
			orden.setDensidad(densidad);
			orden.setTemperaturaC(temperaturaC);
			orden.setCaudalKgH(caudalKgH);
			orden.setFechaUltimaLectura(ahora);
			if (orden.getFechaInicioCarga() == null) {
				orden.setFechaInicioCarga(ahora);
			}
			orden = ordenDAO.save(orden);

			DetalleCarga detalle = new DetalleCarga();
			detalle.setOrden(orden);
			detalle.setMasaAcumuladaKg(masaAcumuladaKg);
			detalle.setDensidad(densidad);
			detalle.setTemperaturaC(temperaturaC);
			detalle.setCaudalKgH(caudalKgH);
			detalle.setFechaLectura(ahora);
			detalleCargaDAO.save(detalle);

			return new LecturaResultado(orden, true, null);
		} catch (NotFoundException | InvalidStateException e) {
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Orden cerrarCarga(Integer orderNumber, String actor)
			throws NotFoundException, InvalidStateException, BusinessException {
		try {
			// Mismo bloqueo que registrarTara/activar/registrarLectura: una
			// vez que este cierre confirma y libera el lock, cualquier
			// lectura o cierre concurrente que esperaba el lock relee la
			// orden ya en estado 3 y se rechaza por el chequeo de estado de
			// su propia operacion (registrarLectura o este mismo metodo).
			Orden orden = ordenDAO.findByOrderNumberForUpdate(orderNumber)
					.orElseThrow(() -> NotFoundException.builder()
							.message("No existe una orden con orderNumber=" + orderNumber).build());

			if (orden.getEstado() != 2) {
				// Cubre tambien el cierre repetido: tras el primer cierre
				// exitoso la orden queda en estado 3 y esta misma rama lo
				// rechaza.
				throw InvalidStateException.builder().message("La orden orderNumber=" + orderNumber
						+ " no esta en estado 2 (estado actual: " + orden.getEstado() + ")").build();
			}
			if (orden.getFechaActivacion() == null) {
				throw InvalidStateException.builder()
						.message("La orden orderNumber=" + orderNumber + " todavia no fue activada").build();
			}
			if (orden.getFechaUltimaLectura() == null) {
				throw InvalidStateException.builder().message(
						"La orden orderNumber=" + orderNumber + " no tiene lecturas aceptadas, no se puede cerrar")
						.build();
			}

			// Hora efectiva del cierre (instante real de esta llamada), para
			// el historial de auditoria. fechaFinCarga usa un valor
			// distinto: ver Javadoc de IOrdenBusiness.cerrarCarga.
			LocalDateTime ahora = LocalDateTime.now(ZONA_RECEPCION).truncatedTo(ChronoUnit.MICROS);

			orden.setFechaFinCarga(orden.getFechaUltimaLectura());
			orden.setEstado(3);
			orden = ordenDAO.save(orden);

			HistorialEstado historial = new HistorialEstado();
			historial.setOrden(orden);
			historial.setEstadoNuevo(3);
			historial.setFechaHora(ahora);
			historial.setUsuarioOProceso(actor);
			historialDAO.save(historial);

			return orden;
		} catch (NotFoundException | InvalidStateException e) {
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Conciliacion registrarPesajeFinal(Integer orderNumber, BigDecimal pesoBrutoKg, String actor)
			throws NotFoundException, InvalidStateException, BusinessException {
		try {
			// Mismo bloqueo que registrarTara/activar/registrarLectura/
			// cerrarCarga: quien pierde la carrera por el lock relee la
			// orden ya en estado 4 y se rechaza por el chequeo de estado.
			Orden orden = ordenDAO.findByOrderNumberForUpdate(orderNumber)
					.orElseThrow(() -> NotFoundException.builder()
							.message("No existe una orden con orderNumber=" + orderNumber).build());

			if (orden.getEstado() != 3) {
				// Cubre tambien el pesaje final repetido: tras el primer
				// pesaje exitoso la orden queda en estado 4 y esta misma
				// rama lo rechaza.
				throw InvalidStateException.builder().message("La orden orderNumber=" + orderNumber
						+ " no esta en estado 3 (estado actual: " + orden.getEstado() + ")").build();
			}

			LocalDateTime ahora = LocalDateTime.now(ZONA_RECEPCION).truncatedTo(ChronoUnit.MICROS);

			orden.setPesoBrutoKg(pesoBrutoKg);
			orden.setFechaPesajeFinal(ahora);
			orden.setEstado(4);
			orden = ordenDAO.save(orden);

			HistorialEstado historial = new HistorialEstado();
			historial.setOrden(orden);
			historial.setEstadoNuevo(4);
			historial.setFechaHora(ahora);
			historial.setUsuarioOProceso(actor);
			historialDAO.save(historial);

			return calcularConciliacion(orden);
		} catch (NotFoundException | InvalidStateException e) {
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	@Override
	public Conciliacion consultarConciliacion(Integer orderNumber)
			throws NotFoundException, InvalidStateException, BusinessException {
		try {
			// Sin bloqueo: lectura simple, no participa de ninguna
			// transicion de estado.
			Orden orden = ordenDAO.findByOrderNumber(orderNumber)
					.orElseThrow(() -> NotFoundException.builder()
							.message("No existe una orden con orderNumber=" + orderNumber).build());

			if (orden.getEstado() != 4) {
				throw InvalidStateException.builder().message("La orden orderNumber=" + orderNumber
						+ " no esta en estado 4 (estado actual: " + orden.getEstado() + ")").build();
			}

			return calcularConciliacion(orden);
		} catch (NotFoundException | InvalidStateException e) {
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	/**
	 * Calculo de conciliacion, compartido por registrarPesajeFinal y
	 * consultarConciliacion para que ambos devuelvan exactamente el mismo
	 * resultado a partir de los mismos valores persistidos.
	 * productoCargadoKg = ultima masa acumulada ACEPTADA del snapshot, no
	 * el preset (ver docs/api.md, calculo de la conciliacion). No hay
	 * redondeo explicito: BigDecimal.subtract conserva toda la precision
	 * de los operandos. Temperatura, densidad y caudal corresponden a la
	 * ultima lectura valida, no a promedios. El calculo de promedios no
	 * esta incluido en esta version.
	 * Precondicion (no verificada aqui, ya garantizada por el circuito):
	 * al llegar a estado 3/4 el snapshot de telemetria no puede ser nulo,
	 * porque cerrarCarga exige al menos una lectura aceptada antes de
	 * transicionar a 3, y ninguna operacion posterior lo modifica.
	 */
	private Conciliacion calcularConciliacion(Orden orden) {
		BigDecimal netoKg = orden.getPesoBrutoKg().subtract(orden.getTaraKg());
		BigDecimal diferenciaKg = netoKg.subtract(orden.getMasaAcumuladaKg());
		return new Conciliacion(orden.getOrderNumber(), orden.getTaraKg(), orden.getPesoBrutoKg(), netoKg,
				orden.getMasaAcumuladaKg(), diferenciaKg, orden.getTemperaturaC(), orden.getDensidad(),
				orden.getCaudalKgH());
	}

	private String causaMasEspecifica(Throwable e) {
		Throwable t = e;
		while (t.getCause() != null && t.getCause() != t) {
			t = t.getCause();
		}
		return t.getMessage();
	}

	/**
	 * Buscar por externalCode, crear si no existe, actualizar si existe.
	 * Campos obligatorios siempre se sobreescriben con el valor recibido.
	 * Campos opcionales: si el DTO los trae null, se conserva el valor ya
	 * guardado (al crear, quedan null). No hay borrado implicito de un
	 * opcional ya guardado por omitirlo en una recepcion posterior.
	 */
	private Camion sincronizarCamion(CamionDTO dto) {
		Camion camion = camionDAO.findByExternalCode(dto.getExternalCode()).orElseGet(Camion::new);
		camion.setExternalCode(dto.getExternalCode());
		camion.setPatente(dto.getPatente());
		camion.setCapacidadKg(dto.getCapacidadKg());
		if (dto.getDescripcion() != null) {
			camion.setDescripcion(dto.getDescripcion());
		}
		return camionDAO.save(camion);
	}

	private Chofer sincronizarChofer(ChoferDTO dto) {
		Chofer chofer = choferDAO.findByExternalCode(dto.getExternalCode()).orElseGet(Chofer::new);
		chofer.setExternalCode(dto.getExternalCode());
		chofer.setNombre(dto.getNombre());
		chofer.setApellido(dto.getApellido());
		if (dto.getDocumento() != null) {
			chofer.setDocumento(dto.getDocumento());
		}
		return choferDAO.save(chofer);
	}

	private Cliente sincronizarCliente(ClienteDTO dto) {
		Cliente cliente = clienteDAO.findByExternalCode(dto.getExternalCode()).orElseGet(Cliente::new);
		cliente.setExternalCode(dto.getExternalCode());
		cliente.setRazonSocial(dto.getRazonSocial());
		if (dto.getContacto() != null) {
			cliente.setContacto(dto.getContacto());
		}
		return clienteDAO.save(cliente);
	}

	private Producto sincronizarProducto(ProductoDTO dto) {
		Producto producto = productoDAO.findByExternalCode(dto.getExternalCode()).orElseGet(Producto::new);
		producto.setExternalCode(dto.getExternalCode());
		producto.setNombre(dto.getNombre());
		if (dto.getDescripcion() != null) {
			producto.setDescripcion(dto.getDescripcion());
		}
		return productoDAO.save(producto);
	}
}
