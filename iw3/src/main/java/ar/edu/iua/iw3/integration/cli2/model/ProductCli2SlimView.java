package ar.edu.iua.iw3.integration.cli2.model;

//https://docs.spring.io/spring-data/rest/reference/data-commons/repositories/projections.html#page-title
public interface ProductCli2SlimView {
	Long getId();

	String getProduct();

	Double getPrice();

	Category getCategory();

	interface Category {
		String getCategory();
	}
}
