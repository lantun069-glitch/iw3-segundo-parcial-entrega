package ar.edu.iua.iw3.integration.cli1.model;

import ar.edu.iua.iw3.business.ICategoryBusiness;
import ar.edu.iua.iw3.business.execption.BusinessException;
import ar.edu.iua.iw3.business.execption.NotFoundException;
import ar.edu.iua.iw3.util.JsonUtiles;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

public class ProductCli1JsonDeserializer extends StdDeserializer<ProductCli1> {

	private static final long serialVersionUID = -3881285352118964728L;

	private ICategoryBusiness categoryBusiness;

	protected ProductCli1JsonDeserializer(Class<?> vc) {
		super(vc);
	}

	public ProductCli1JsonDeserializer(Class<?> vc, ICategoryBusiness categoryBusiness) {

		super(vc);
		this.categoryBusiness = categoryBusiness;
	}

	@Override
	public ProductCli1 deserialize(JsonParser jp, DeserializationContext ctxt) throws JacksonException {

		ProductCli1 r = new ProductCli1();

		JsonNode node = ctxt.readTree(jp);

		String code = JsonUtiles.getString(node, "product_code,code_product,code,codigo".split(","),
				System.currentTimeMillis() + "");

		String productDesc = JsonUtiles.getString(node,
				"product,description,product_description,product_name".split(","), null);

		double price = JsonUtiles.getDouble(node, "product_price,price_product,price".split(","), 0);

		boolean stock = JsonUtiles.getBoolean(node, "stock,in_stock".split(","), false);

		r.setCodCli1(code);
		r.setProduct(productDesc);
		r.setPrice(price);
		r.setStock(stock);

		String categoryName = JsonUtiles.getString(node, "category,product_category,category_product".split(","), null);

		if (categoryName != null) {
			try {
				r.setCategory(categoryBusiness.load(categoryName));
			} catch (NotFoundException | BusinessException e) {
			}
		}

		return r;
	}
}
