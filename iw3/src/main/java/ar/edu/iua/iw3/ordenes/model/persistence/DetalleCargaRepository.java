package ar.edu.iua.iw3.ordenes.model.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.edu.iua.iw3.ordenes.model.DetalleCarga;

@Repository
public interface DetalleCargaRepository extends JpaRepository<DetalleCarga, Long> {
}
