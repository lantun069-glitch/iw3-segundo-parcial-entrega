package ar.edu.iua.iw3.ordenes.model.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ar.edu.iua.iw3.ordenes.model.Orden;
import jakarta.persistence.LockModeType;

@Repository
public interface OrdenRepository extends JpaRepository<Orden, Long> {
	boolean existsByOrderNumber(Integer orderNumber);

	/**
	 * Lectura simple, sin bloqueo: para consultarConciliacion, que no
	 * escribe nada y no necesita serializarse con tara/activacion/
	 * lectura/cierre/pesaje final.
	 */
	Optional<Orden> findByOrderNumber(Integer orderNumber);

	/**
	 * Bloqueo pesimista de escritura sobre la fila de la orden, para
	 * serializar tara/activacion concurrentes de la misma orderNumber
	 * dentro de la transaccion que la invoca. No se usa @Version: la
	 * proteccion de concurrencia de esta unidad es exclusivamente este
	 * bloqueo (decision del equipo, ver docs/decisiones.md).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from Orden o where o.orderNumber = :orderNumber")
	Optional<Orden> findByOrderNumberForUpdate(@Param("orderNumber") Integer orderNumber);
}
