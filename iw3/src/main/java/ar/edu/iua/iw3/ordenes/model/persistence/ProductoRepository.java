package ar.edu.iua.iw3.ordenes.model.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.edu.iua.iw3.ordenes.model.Producto;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
	Optional<Producto> findByExternalCode(String externalCode);
}
