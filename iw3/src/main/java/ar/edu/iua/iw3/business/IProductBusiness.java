package ar.edu.iua.iw3.business;

import java.util.List;

import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.FoundException;
import ar.edu.iua.iw3.business.execption.NotFoundException;
import ar.edu.iua.iw3.model.Product;

public interface IProductBusiness {
	public List<Product> list() throws BusinessException;

	public Product load(Long id) throws NotFoundException, BusinessException;

	public Product load(String product) throws NotFoundException, BusinessException;

	public Product add(Product product) throws FoundException, BusinessException;

	public Product update(Product product) throws NotFoundException, BusinessException;

	public void delete(long id) throws NotFoundException, BusinessException;

}
