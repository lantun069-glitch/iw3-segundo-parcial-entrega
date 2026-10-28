package ar.edu.iua.iw3.ordenes.model.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.edu.iua.iw3.ordenes.model.Camion;

@Repository
public interface CamionRepository extends JpaRepository<Camion, Long> {
	Optional<Camion> findByExternalCode(String externalCode);
}
