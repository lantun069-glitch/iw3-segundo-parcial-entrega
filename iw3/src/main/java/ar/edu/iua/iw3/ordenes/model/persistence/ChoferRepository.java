package ar.edu.iua.iw3.ordenes.model.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.edu.iua.iw3.ordenes.model.Chofer;

@Repository
public interface ChoferRepository extends JpaRepository<Chofer, Long> {
	Optional<Chofer> findByExternalCode(String externalCode);
}
