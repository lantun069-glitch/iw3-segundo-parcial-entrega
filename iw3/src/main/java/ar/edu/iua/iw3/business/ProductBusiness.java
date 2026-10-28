package ar.edu.iua.iw3.business;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.FoundException;
import ar.edu.iua.iw3.business.execption.NotFoundException;
import ar.edu.iua.iw3.model.Product;
import ar.edu.iua.iw3.model.persistence.ProductRepository;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ProductBusiness implements IProductBusiness {

	// IoC
	@Autowired
	private ProductRepository productDAO;

	@Override
	public List<Product> list() throws BusinessException {
		try {
			return productDAO.findAll();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
	}

	@Override
	public Product load(Long id) throws NotFoundException, BusinessException {
		if (id==null) {
			throw NotFoundException.builder().message("No se encuentra el Producto id=null").build();
		}
		Optional<Product> r;
		try {
			r = productDAO.findById(id);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
		if (r.isEmpty()) {
			throw NotFoundException.builder().message("No se encuentra el Producto id=" + id).build();
		}
		return r.get();

	}

	@Override
	public Product load(String product) throws NotFoundException, BusinessException {
		Optional<Product> r;
		try {
			r = productDAO.findByProduct(product);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}
		if (r.isEmpty()) {
			throw NotFoundException.builder().message("No se encuentra el Producto '" + product + "'").build();
		}
		return r.get();

	}

	@Override
	public Product add(Product product) throws FoundException, BusinessException {
		try {
			load(product.getId());
			throw FoundException.builder().message("Se encuentró el Producto id=" + product.getId()).build();
		} catch (NotFoundException e) {
		}
		try {
			load(product.getProduct());
			throw FoundException.builder().message("Se encuentró el Producto '" + product.getProduct() + "'").build();
		} catch (NotFoundException e) {
		}

		try {
			return productDAO.save(product);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}

	}

	@Override
	public Product update(Product product) throws NotFoundException, BusinessException {
		load(product.getId());
		try {
			return productDAO.save(product);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}

	}

	@Override
	public void delete(long id) throws NotFoundException, BusinessException {
		load(id);
		try {
			 productDAO.deleteById(id);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			throw BusinessException.builder().ex(e).build();
		}


	}

}
