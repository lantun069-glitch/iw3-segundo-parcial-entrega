package ar.edu.iua.iw3.model.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ar.edu.iua.iw3.model.Category;

@Repository
public interface CategoryRespository extends JpaRepository<Category, Long> {

	public Optional<Category> findOneByCategory(String category);
}
