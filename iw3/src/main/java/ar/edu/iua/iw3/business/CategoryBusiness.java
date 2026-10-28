package ar.edu.iua.iw3.business;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.FoundException;
import ar.edu.iua.iw3.business.execption.NotFoundException;
import ar.edu.iua.iw3.model.Category;
import ar.edu.iua.iw3.model.persistence.CategoryRespository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CategoryBusiness implements ICategoryBusiness {
	@Autowired
	private CategoryRespository categoryDAO;

	@Override
	public Category load(long id) throws NotFoundException, BusinessException {
		Optional<Category> r;
		try {
			r = categoryDAO.findById(id);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
		if (r.isEmpty()) {
			throw NotFoundException.builder().message("No se encuentra la Categoría id=" + id).build();
		}
		return r.get();
	}

	@Override
	public Category load(String category) throws NotFoundException, BusinessException {
		Optional<Category> r;
		try {
			r = categoryDAO.findOneByCategory(category);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
		if (r.isEmpty()) {
			throw NotFoundException.builder().message("No se encuentra la Categoría nombbre=" + category).build();
		}
		return r.get();
	}

	@Override
	public List<Category> list() throws BusinessException {
		try {
			return categoryDAO.findAll();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	@Override
	public Category add(Category category) throws FoundException, BusinessException {
		if (category.getId() != null) {
			try {
				load(category.getId());
				throw FoundException.builder().message("Se encuentró la Categoría id=" + category.getId()).build();
			} catch (NotFoundException e) {
			}
		}

		try {
			return categoryDAO.save(category);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	@Override
	public Category update(Category category) throws NotFoundException, BusinessException {
		load(category.getId());
		try {
			return categoryDAO.save(category);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	@Override
	public void delete(long id) throws NotFoundException, BusinessException {
		load(id);
		try {
			categoryDAO.deleteById(id);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

}
