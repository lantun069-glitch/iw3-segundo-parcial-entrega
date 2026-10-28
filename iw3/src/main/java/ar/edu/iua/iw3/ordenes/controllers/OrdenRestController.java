package ar.edu.iua.iw3.ordenes.controllers;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.FoundException;
import ar.edu.iua.iw3.business.execption.NotFoundException;
import ar.edu.iua.iw3.controllers.BaseRestController;
import ar.edu.iua.iw3.controllers.Constants;
import ar.edu.iua.iw3.ordenes.business.Conciliacion;
import ar.edu.iua.iw3.ordenes.business.IOrdenBusiness;
import ar.edu.iua.iw3.ordenes.business.exception.InvalidActivationKeyException;
import ar.edu.iua.iw3.ordenes.business.exception.InvalidStateException;
import ar.edu.iua.iw3.ordenes.business.LecturaResultado;
import ar.edu.iua.iw3.ordenes.controllers.dto.ActivacionRequest;
import ar.edu.iua.iw3.ordenes.controllers.dto.ActivacionResponse;
import ar.edu.iua.iw3.ordenes.controllers.dto.CierreResponse;
import ar.edu.iua.iw3.ordenes.controllers.dto.ConciliacionResponse;
import ar.edu.iua.iw3.ordenes.controllers.dto.LecturaRequest;
import ar.edu.iua.iw3.ordenes.controllers.dto.LecturaResponse;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionRequest;
import ar.edu.iua.iw3.ordenes.controllers.dto.OrdenRecepcionResponse;
import ar.edu.iua.iw3.ordenes.controllers.dto.PesajeFinalRequest;
import ar.edu.iua.iw3.ordenes.controllers.dto.TaraRequest;
import ar.edu.iua.iw3.ordenes.controllers.dto.TaraResponse;
import ar.edu.iua.iw3.ordenes.model.Orden;
import ar.edu.iua.iw3.util.IStandartResponseBusiness;
import ar.edu.iua.iw3.util.StandartResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Expone el circuito completo de una orden: recepcion (recibir()),
 * tara/activacion (registrarTara()/activar()), telemetria
 * (registrarLectura()), cierre (cerrar()), pesaje final con conciliacion
 * (pesajeFinal()) y consulta posterior de esa conciliacion
 * (conciliacion()). Los ultimos valores de temperatura/densidad/caudal
 * en la conciliacion no son promedios (ver docs/api.md, que no cubre
 * esta version). No expone CRUD/consulta para Orden ni para sus
 * referencias (Camion/Chofer/Cliente/Producto); eso no esta pedido.
 * No hay endpoint de consulta del snapshot/historico: la respuesta de
 * cada lectura ya devuelve el snapshot actual, suficiente para esta
 * entrega.
 *
 * La validacion de entrada (400) se maneja con @ExceptionHandler LOCAL
 * a este controlador (no un @ControllerAdvice global, para no introducir
 * infraestructura generica no pedida). Motivo del cambio: dejar que
 * Spring resuelva la excepcion por su mecanismo por defecto termina en
 * un forward interno a /error, que vuelve a pasar por la cadena de
 * filtros de seguridad y devuelve 401 en vez de 400 (/error no esta en
 * la lista de rutas publicas, y no se la agrega: ver docs/decisiones.md).
 * Manejando la excepcion aqui, nunca se llega a ese forward.
 *
 * Ninguno de los catch de este controlador le pasa la excepcion real a
 * StandartResponse: todos pasan null como "ex", para que getDevInfo()
 * devuelva "No stack trace" en vez de un stacktrace real,
 * independientemente de dev.info.enabled. Los 404/409/400 de negocio
 * usan el mensaje propio de la excepcion (ninguno expone detalle de
 * causa interna ni valores de clave). El 500 usa un mensaje generico
 * fijo, porque el mensaje de BusinessException puede terminar
 * reflejando el toString() de la causa interna segun el constructor
 * Lombok que se haya invocado (ver docs/decisiones.md).
 */
@RestController
@RequestMapping(Constants.URL_ORDERS)
@Tag(description = "Recepcion y tara/activacion de ordenes de carga (F2/F3)", name = "Orden")
public class OrdenRestController extends BaseRestController {

	@Autowired
	private IOrdenBusiness ordenBusiness;

	@Autowired
	private IStandartResponseBusiness response;

	@Operation(summary = "Recibir una orden de carga (estado 1)",
			description = "Crea una orden nueva en estado 1, o sincroniza camion/chofer/cliente/producto "
					+ "por externalCode si ya existian. No acepta estado, id interno, fechaRecepcion ni el actor "
					+ "del historial en el JSON de entrada: el servidor los genera. Requiere autenticacion JWT "
					+ "(Authorization: Bearer <token>), igual que el resto de la API.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Orden creada en estado 1.",
					content = @Content(schema = @Schema(implementation = OrdenRecepcionResponse.class), examples = @ExampleObject(name = "Orden creada", value = "{\"id\":1,\"orderNumber\":900001,\"estado\":1,\"fechaRecepcion\":\"2026-10-20T20:06:30.119016859\"}"))),
			@ApiResponse(responseCode = "400", description = "Entrada invalida: campo obligatorio ausente/vacio, texto que excede su longitud maxima, decimales fuera de precision, cantidad <= 0, o JSON malformado.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = {
							@ExampleObject(name = "Campo obligatorio ausente", value = "{\"message\":\"camion.patente: must not be blank\",\"code\":400,\"devInfo\":\"No stack trace\"}"),
							@ExampleObject(name = "JSON malformado", value = "{\"message\":\"JSON de entrada invalido o malformado\",\"code\":400,\"devInfo\":\"No stack trace\"}")
					})),
			@ApiResponse(responseCode = "401", description = "Sin token o token invalido/alterado. Respuesta estandar de Spring Security, sin cuerpo JSON (no pasa por StandartResponse).",
					content = @Content),
			@ApiResponse(responseCode = "409", description = "orderNumber ya existe (chequeo previo o colision concurrente detectada por la restriccion unica de la base).",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"Ya existe una orden con orderNumber=900001\",\"code\":409,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "500", description = "Error no clasificado al procesar la solicitud.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"Error interno al procesar la solicitud\",\"code\":500,\"devInfo\":\"No stack trace\"}")))
	})
	@PostMapping(value = "", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> recibir(
			@org.springframework.web.bind.annotation.RequestBody @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(
					content = @Content(examples = @ExampleObject(value = "{\"orderNumber\":900001,\"fechaCargaPrevista\":\"2026-11-03T08:30:00\",\"presetKg\":10000.500,\"camion\":{\"externalCode\":\"CAM-00891\",\"patente\":\"AB123CD\",\"descripcion\":\"Camion cisterna GLP\",\"capacidadKg\":12000.000},\"chofer\":{\"externalCode\":\"CHO-00457\",\"nombre\":\"Marcos\",\"apellido\":\"Toledo\",\"documento\":\"30111222\"},\"cliente\":{\"externalCode\":\"CLI-00019\",\"razonSocial\":\"Distribuidora del Sur S.A.\",\"contacto\":\"compras@distribuidorasur.com.ar\"},\"producto\":{\"externalCode\":\"PRD-GLP01\",\"nombre\":\"GLP\",\"descripcion\":\"Gas licuado de petroleo\"}}")))
			OrdenRecepcionRequest request) {
		// El actor viene del contexto de seguridad, nunca del JSON de entrada.
		String actor = getUserLogged().getUsername();
		try {
			Orden orden = ordenBusiness.recibirOrden(request, actor);
			OrdenRecepcionResponse body = new OrdenRecepcionResponse(orden.getId(), orden.getOrderNumber(),
					orden.getEstado(), orden.getFechaRecepcion());
			return new ResponseEntity<>(body, HttpStatus.CREATED);
		} catch (FoundException e) {
			// ex=null a proposito: no se expone la excepcion real al cliente.
			return new ResponseEntity<>(response.build(HttpStatus.CONFLICT, null, e.getMessage()), HttpStatus.CONFLICT);
		} catch (BusinessException e) {
			// ex=null a proposito; mensaje generico fijo (no e.getMessage()):
			// no se expone detalle interno de la causa al cliente.
			return new ResponseEntity<>(
					response.build(HttpStatus.INTERNAL_SERVER_ERROR, null, "Error interno al procesar la solicitud"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Operation(summary = "Registrar la tara (estado 1 -> 2)",
			description = "Registra el pesaje inicial (camion vacio), genera la clave de activacion de 5 "
					+ "digitos y transiciona la orden a estado 2. Requiere que la orden exista y este en "
					+ "estado 1. La clave devuelta no se repite en ninguna otra respuesta ni se registra en "
					+ "logs. Requiere autenticacion JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Tara registrada, orden en estado 2.",
					content = @Content(schema = @Schema(implementation = TaraResponse.class), examples = @ExampleObject(value = "{\"orderNumber\":900001,\"estado\":2,\"taraKg\":12000.000,\"fechaTara\":\"2026-10-21T09:00:00\",\"claveActivacion\":54231}"))),
			@ApiResponse(responseCode = "400", description = "taraKg ausente, no positivo, o con mas de 3 decimales/12 enteros.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"taraKg: must be greater than 0\",\"code\":400,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "401", description = "Sin token o token invalido/alterado.", content = @Content),
			@ApiResponse(responseCode = "404", description = "No existe una orden con ese orderNumber.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"No existe una orden con orderNumber=900001\",\"code\":404,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "409", description = "La orden no esta en estado 1.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"La orden orderNumber=900001 no esta en estado 1 (estado actual: 2)\",\"code\":409,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "500", description = "Error no clasificado al procesar la solicitud.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"Error interno al procesar la solicitud\",\"code\":500,\"devInfo\":\"No stack trace\"}")))
	})
	@PostMapping(value = "/{orderNumber}/tara", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> registrarTara(@PathVariable Integer orderNumber,
			@org.springframework.web.bind.annotation.RequestBody @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(
					content = @Content(examples = @ExampleObject(value = "{\"taraKg\":12000.000}")))
			TaraRequest request) {
		String actor = getUserLogged().getUsername();
		try {
			Orden orden = ordenBusiness.registrarTara(orderNumber, request.getTaraKg(), actor);
			TaraResponse body = new TaraResponse(orden.getOrderNumber(), orden.getEstado(), orden.getTaraKg(),
					orden.getFechaTara(), orden.getClaveActivacion());
			return new ResponseEntity<>(body, HttpStatus.OK);
		} catch (NotFoundException e) {
			return new ResponseEntity<>(response.build(HttpStatus.NOT_FOUND, null, e.getMessage()), HttpStatus.NOT_FOUND);
		} catch (InvalidStateException e) {
			return new ResponseEntity<>(response.build(HttpStatus.CONFLICT, null, e.getMessage()), HttpStatus.CONFLICT);
		} catch (BusinessException e) {
			return new ResponseEntity<>(
					response.build(HttpStatus.INTERNAL_SERVER_ERROR, null, "Error interno al procesar la solicitud"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Operation(summary = "Activar el instrumento de carga (dentro de estado 2)",
			description = "Valida la clave de activacion contra la orden identificada por orderNumber y, si "
					+ "coincide, devuelve el preset. Requiere que la orden exista y este en estado 2. "
					+ "Idempotente mientras la orden siga en estado 2: una clave correcta repetida devuelve el "
					+ "mismo preset y la misma fechaActivacion ya fijada, sin volver a escribirla. No cambia el "
					+ "estado de la orden. Requiere autenticacion JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Clave correcta: preset devuelto.",
					content = @Content(schema = @Schema(implementation = ActivacionResponse.class), examples = @ExampleObject(value = "{\"orderNumber\":900001,\"presetKg\":10000.500,\"fechaActivacion\":\"2026-10-21T09:05:00\"}"))),
			@ApiResponse(responseCode = "400", description = "Clave ausente/fuera de [10000,99999] (validacion), o clave que no coincide con la de la orden.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = {
							@ExampleObject(name = "Clave fuera de rango", value = "{\"message\":\"clave: must be greater than or equal to 10000\",\"code\":400,\"devInfo\":\"No stack trace\"}"),
							@ExampleObject(name = "Clave incorrecta", value = "{\"message\":\"Clave de activacion incorrecta\",\"code\":400,\"devInfo\":\"No stack trace\"}")
					})),
			@ApiResponse(responseCode = "401", description = "Sin token o token invalido/alterado.", content = @Content),
			@ApiResponse(responseCode = "404", description = "No existe una orden con ese orderNumber.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"No existe una orden con orderNumber=900001\",\"code\":404,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "409", description = "La orden no esta en estado 2.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"La orden orderNumber=900001 no esta en estado 2 (estado actual: 1)\",\"code\":409,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "500", description = "Error no clasificado al procesar la solicitud.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"Error interno al procesar la solicitud\",\"code\":500,\"devInfo\":\"No stack trace\"}")))
	})
	@PostMapping(value = "/{orderNumber}/activacion", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> activar(@PathVariable Integer orderNumber,
			@org.springframework.web.bind.annotation.RequestBody @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(
					content = @Content(examples = @ExampleObject(value = "{\"clave\":54231}")))
			ActivacionRequest request) {
		try {
			Orden orden = ordenBusiness.activar(orderNumber, request.getClave());
			ActivacionResponse body = new ActivacionResponse(orden.getOrderNumber(), orden.getPresetKg(),
					orden.getFechaActivacion());
			return new ResponseEntity<>(body, HttpStatus.OK);
		} catch (NotFoundException e) {
			return new ResponseEntity<>(response.build(HttpStatus.NOT_FOUND, null, e.getMessage()), HttpStatus.NOT_FOUND);
		} catch (InvalidStateException e) {
			return new ResponseEntity<>(response.build(HttpStatus.CONFLICT, null, e.getMessage()), HttpStatus.CONFLICT);
		} catch (InvalidActivationKeyException e) {
			return new ResponseEntity<>(response.build(HttpStatus.BAD_REQUEST, null, e.getMessage()), HttpStatus.BAD_REQUEST);
		} catch (BusinessException e) {
			return new ResponseEntity<>(
					response.build(HttpStatus.INTERNAL_SERVER_ERROR, null, "Error interno al procesar la solicitud"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Operation(summary = "Registrar una lectura de telemetria (dentro de estado 2, orden activada)",
			description = "Procesa una lectura de masa acumulada (kg), densidad, temperatura (C) y caudal "
					+ "(kg/h). Requiere que la orden exista, este en estado 2 y ya tenga fechaActivacion "
					+ "(decision del equipo: sin activacion no se aceptan lecturas). Una lectura con caudal<=0, "
					+ "masa<=0, o masa estrictamente menor a la ultima aceptada se descarta (aceptada=false, con "
					+ "motivoDescarte): no es un error, siempre responde 200 y el snapshot queda sin cambios. "
					+ "La primera lectura aceptada fija fechaInicioCarga; no se vuelve a modificar despues. "
					+ "Requiere autenticacion JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Lectura procesada (aceptada o descartada; ver el campo aceptada).",
					content = @Content(schema = @Schema(implementation = LecturaResponse.class), examples = {
							@ExampleObject(name = "Aceptada", value = "{\"orderNumber\":900001,\"aceptada\":true,\"motivoDescarte\":null,\"masaAcumuladaKg\":4000.000,\"densidad\":0.5400,\"temperaturaC\":18.50,\"caudalKgH\":3000.000,\"fechaUltimaLectura\":\"2026-10-21T10:00:00.123456\",\"fechaInicioCarga\":\"2026-10-21T10:00:00.123456\"}"),
							@ExampleObject(name = "Descartada (caudal no positivo)", value = "{\"orderNumber\":900001,\"aceptada\":false,\"motivoDescarte\":\"caudal_no_positivo\",\"masaAcumuladaKg\":null,\"densidad\":null,\"temperaturaC\":null,\"caudalKgH\":null,\"fechaUltimaLectura\":null,\"fechaInicioCarga\":null}")
					})),
			@ApiResponse(responseCode = "400", description = "Algun campo ausente, o con mas decimales/enteros que la precision admitida.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"masaAcumuladaKg: must not be null\",\"code\":400,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "401", description = "Sin token o token invalido/alterado.", content = @Content),
			@ApiResponse(responseCode = "404", description = "No existe una orden con ese orderNumber.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"No existe una orden con orderNumber=900001\",\"code\":404,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "409", description = "La orden no esta en estado 2, o no fue activada todavia.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = {
							@ExampleObject(name = "Estado incompatible", value = "{\"message\":\"La orden orderNumber=900001 no esta en estado 2 (estado actual: 1)\",\"code\":409,\"devInfo\":\"No stack trace\"}"),
							@ExampleObject(name = "Sin activar", value = "{\"message\":\"La orden orderNumber=900001 todavia no fue activada\",\"code\":409,\"devInfo\":\"No stack trace\"}")
					})),
			@ApiResponse(responseCode = "500", description = "Error no clasificado al procesar la solicitud.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"Error interno al procesar la solicitud\",\"code\":500,\"devInfo\":\"No stack trace\"}")))
	})
	@PostMapping(value = "/{orderNumber}/lecturas", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> registrarLectura(@PathVariable Integer orderNumber,
			@org.springframework.web.bind.annotation.RequestBody @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(
					content = @Content(examples = @ExampleObject(value = "{\"masaAcumuladaKg\":4000.000,\"densidad\":0.5400,\"temperaturaC\":18.50,\"caudalKgH\":3000.000}")))
			LecturaRequest request) {
		try {
			LecturaResultado resultado = ordenBusiness.registrarLectura(orderNumber, request.getMasaAcumuladaKg(),
					request.getDensidad(), request.getTemperaturaC(), request.getCaudalKgH());
			Orden orden = resultado.getOrden();
			LecturaResponse body = new LecturaResponse(orden.getOrderNumber(), resultado.isAceptada(),
					resultado.getMotivoDescarte(), orden.getMasaAcumuladaKg(), orden.getDensidad(),
					orden.getTemperaturaC(), orden.getCaudalKgH(), orden.getFechaUltimaLectura(),
					orden.getFechaInicioCarga());
			return new ResponseEntity<>(body, HttpStatus.OK);
		} catch (NotFoundException e) {
			return new ResponseEntity<>(response.build(HttpStatus.NOT_FOUND, null, e.getMessage()), HttpStatus.NOT_FOUND);
		} catch (InvalidStateException e) {
			return new ResponseEntity<>(response.build(HttpStatus.CONFLICT, null, e.getMessage()), HttpStatus.CONFLICT);
		} catch (BusinessException e) {
			return new ResponseEntity<>(
					response.build(HttpStatus.INTERNAL_SERVER_ERROR, null, "Error interno al procesar la solicitud"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Operation(summary = "Cerrar la carga (estado 2 -> 3)",
			description = "Cierra explicitamente la carga: bloquea nuevas lecturas de detalle a partir de "
					+ "este momento (registrarLectura rechaza con 409 cualquier llamada posterior, porque la "
					+ "orden deja de estar en estado 2). Requiere que la orden exista, este en estado 2, ya "
					+ "tenga fechaActivacion y al menos una lectura aceptada (decisiones del equipo adicionales "
					+ "al requisito de la fuente). No tiene body. fechaFinCarga en la respuesta es la ultima "
					+ "lectura valida recibida, no el instante de esta llamada. No hay cierre automatico por "
					+ "alcanzar el preset. Requiere autenticacion JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Carga cerrada, orden en estado 3.",
					content = @Content(schema = @Schema(implementation = CierreResponse.class), examples = @ExampleObject(value = "{\"orderNumber\":900001,\"estado\":3,\"fechaFinCarga\":\"2026-10-21T10:07:00.845232\"}"))),
			@ApiResponse(responseCode = "401", description = "Sin token o token invalido/alterado.", content = @Content),
			@ApiResponse(responseCode = "404", description = "No existe una orden con ese orderNumber.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"No existe una orden con orderNumber=900001\",\"code\":404,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "409", description = "La orden no esta en estado 2 (incluye cierre repetido), no fue activada, o no tiene lecturas aceptadas.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = {
							@ExampleObject(name = "Estado incompatible", value = "{\"message\":\"La orden orderNumber=900001 no esta en estado 2 (estado actual: 1)\",\"code\":409,\"devInfo\":\"No stack trace\"}"),
							@ExampleObject(name = "Sin activar", value = "{\"message\":\"La orden orderNumber=900001 todavia no fue activada\",\"code\":409,\"devInfo\":\"No stack trace\"}"),
							@ExampleObject(name = "Sin lecturas aceptadas", value = "{\"message\":\"La orden orderNumber=900001 no tiene lecturas aceptadas, no se puede cerrar\",\"code\":409,\"devInfo\":\"No stack trace\"}")
					})),
			@ApiResponse(responseCode = "500", description = "Error no clasificado al procesar la solicitud.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"Error interno al procesar la solicitud\",\"code\":500,\"devInfo\":\"No stack trace\"}")))
	})
	@PostMapping(value = "/{orderNumber}/cierre", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> cerrar(@PathVariable Integer orderNumber) {
		String actor = getUserLogged().getUsername();
		try {
			Orden orden = ordenBusiness.cerrarCarga(orderNumber, actor);
			CierreResponse body = new CierreResponse(orden.getOrderNumber(), orden.getEstado(),
					orden.getFechaFinCarga());
			return new ResponseEntity<>(body, HttpStatus.OK);
		} catch (NotFoundException e) {
			return new ResponseEntity<>(response.build(HttpStatus.NOT_FOUND, null, e.getMessage()), HttpStatus.NOT_FOUND);
		} catch (InvalidStateException e) {
			return new ResponseEntity<>(response.build(HttpStatus.CONFLICT, null, e.getMessage()), HttpStatus.CONFLICT);
		} catch (BusinessException e) {
			return new ResponseEntity<>(
					response.build(HttpStatus.INTERNAL_SERVER_ERROR, null, "Error interno al procesar la solicitud"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Operation(summary = "Registrar el pesaje final (estado 3 -> 4)",
			description = "Registra el peso bruto (camion cargado), transiciona la orden a estado 4 y devuelve "
					+ "la conciliacion calculada en esa misma operacion (TP16/TP18). Requiere que la orden "
					+ "exista y este en estado 3. productoCargadoKg es la ultima masa acumulada ACEPTADA (no el "
					+ "preset); ultimaTemperaturaC/ultimaDensidad/ultimoCaudalKgH son los ULTIMOS VALORES del "
					+ "snapshot de telemetria, no promedios (TP19, diferido). Una diferenciaKg distinta de 0 no "
					+ "causa ningun rechazo. Requiere autenticacion JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Pesaje final registrado, orden en estado 4, conciliacion incluida.",
					content = @Content(schema = @Schema(implementation = ConciliacionResponse.class), examples = @ExampleObject(value = "{\"orderNumber\":900001,\"taraKg\":12000.000,\"pesoBrutoKg\":22080.000,\"netoKg\":10080.000,\"productoCargadoKg\":10000.000,\"diferenciaKg\":80.000,\"ultimaTemperaturaC\":18.50,\"ultimaDensidad\":0.5400,\"ultimoCaudalKgH\":3000.000}"))),
			@ApiResponse(responseCode = "400", description = "pesoBrutoKg ausente, no positivo, o con mas de 3 decimales/12 enteros.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"pesoBrutoKg: must be greater than 0\",\"code\":400,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "401", description = "Sin token o token invalido/alterado.", content = @Content),
			@ApiResponse(responseCode = "404", description = "No existe una orden con ese orderNumber.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"No existe una orden con orderNumber=900001\",\"code\":404,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "409", description = "La orden no esta en estado 3 (incluye pesaje final repetido).",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"La orden orderNumber=900001 no esta en estado 3 (estado actual: 2)\",\"code\":409,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "500", description = "Error no clasificado al procesar la solicitud.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"Error interno al procesar la solicitud\",\"code\":500,\"devInfo\":\"No stack trace\"}")))
	})
	@PostMapping(value = "/{orderNumber}/pesaje-final", consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> pesajeFinal(@PathVariable Integer orderNumber,
			@org.springframework.web.bind.annotation.RequestBody @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(
					content = @Content(examples = @ExampleObject(value = "{\"pesoBrutoKg\":16080.000}")))
			PesajeFinalRequest request) {
		String actor = getUserLogged().getUsername();
		try {
			Conciliacion conciliacion = ordenBusiness.registrarPesajeFinal(orderNumber, request.getPesoBrutoKg(), actor);
			return new ResponseEntity<>(aConciliacionResponse(conciliacion), HttpStatus.OK);
		} catch (NotFoundException e) {
			return new ResponseEntity<>(response.build(HttpStatus.NOT_FOUND, null, e.getMessage()), HttpStatus.NOT_FOUND);
		} catch (InvalidStateException e) {
			return new ResponseEntity<>(response.build(HttpStatus.CONFLICT, null, e.getMessage()), HttpStatus.CONFLICT);
		} catch (BusinessException e) {
			return new ResponseEntity<>(
					response.build(HttpStatus.INTERNAL_SERVER_ERROR, null, "Error interno al procesar la solicitud"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@Operation(summary = "Consultar la conciliacion (solo estado 4)",
			description = "Devuelve la misma conciliacion calculada por el pesaje final (TP17), sin escrituras. "
					+ "Requiere que la orden exista y este en estado 4; se puede llamar repetidas veces. "
					+ "Requiere autenticacion JWT.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Conciliacion de una orden en estado 4.",
					content = @Content(schema = @Schema(implementation = ConciliacionResponse.class), examples = @ExampleObject(value = "{\"orderNumber\":900001,\"taraKg\":12000.000,\"pesoBrutoKg\":22080.000,\"netoKg\":10080.000,\"productoCargadoKg\":10000.000,\"diferenciaKg\":80.000,\"ultimaTemperaturaC\":18.50,\"ultimaDensidad\":0.5400,\"ultimoCaudalKgH\":3000.000}"))),
			@ApiResponse(responseCode = "401", description = "Sin token o token invalido/alterado.", content = @Content),
			@ApiResponse(responseCode = "404", description = "No existe una orden con ese orderNumber.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"No existe una orden con orderNumber=900001\",\"code\":404,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "409", description = "La orden no esta en estado 4.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"La orden orderNumber=900001 no esta en estado 4 (estado actual: 3)\",\"code\":409,\"devInfo\":\"No stack trace\"}"))),
			@ApiResponse(responseCode = "500", description = "Error no clasificado al procesar la solicitud.",
					content = @Content(schema = @Schema(implementation = StandartResponse.class), examples = @ExampleObject(value = "{\"message\":\"Error interno al procesar la solicitud\",\"code\":500,\"devInfo\":\"No stack trace\"}")))
	})
	@GetMapping(value = "/{orderNumber}/conciliacion", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> conciliacion(@PathVariable Integer orderNumber) {
		try {
			Conciliacion conciliacion = ordenBusiness.consultarConciliacion(orderNumber);
			return new ResponseEntity<>(aConciliacionResponse(conciliacion), HttpStatus.OK);
		} catch (NotFoundException e) {
			return new ResponseEntity<>(response.build(HttpStatus.NOT_FOUND, null, e.getMessage()), HttpStatus.NOT_FOUND);
		} catch (InvalidStateException e) {
			return new ResponseEntity<>(response.build(HttpStatus.CONFLICT, null, e.getMessage()), HttpStatus.CONFLICT);
		} catch (BusinessException e) {
			return new ResponseEntity<>(
					response.build(HttpStatus.INTERNAL_SERVER_ERROR, null, "Error interno al procesar la solicitud"),
					HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	private ConciliacionResponse aConciliacionResponse(Conciliacion c) {
		return new ConciliacionResponse(c.getOrderNumber(), c.getTaraKg(), c.getPesoBrutoKg(), c.getNetoKg(),
				c.getProductoCargadoKg(), c.getDiferenciaKg(), c.getUltimaTemperaturaC(), c.getUltimaDensidad(),
				c.getUltimoCaudalKgH());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<?> handleValidacion(MethodArgumentNotValidException e) {
		String detalle = e.getBindingResult().getFieldErrors().stream()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage()).collect(Collectors.joining("; "));
		// ex=null a proposito: no se expone la excepcion real al cliente.
		return new ResponseEntity<>(response.build(HttpStatus.BAD_REQUEST, null, detalle), HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<?> handleJsonInvalido(HttpMessageNotReadableException e) {
		return new ResponseEntity<>(response.build(HttpStatus.BAD_REQUEST, null, "JSON de entrada invalido o malformado"),
				HttpStatus.BAD_REQUEST);
	}
}
