package ar.edu.iua.iw3.integration.cli2.model;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import ar.edu.iua.iw3.util.JsonUtiles;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class ProductCli2SlimV1JsonSerializer extends StdSerializer<ProductCli2> {

	private static final long serialVersionUID = -3706327488880784297L;

	public ProductCli2SlimV1JsonSerializer(Class<?> t) {
		super(t);
	}

	@Override
	public void serialize(ProductCli2 value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
		gen.writeStartObject();
		gen.writeNumberProperty("id", value.getId());
		gen.writeStringProperty("product", value.getProduct());
		gen.writeBooleanProperty("stock", value.isStock());
		gen.writeNumberProperty("price", value.getPrice());

		if (value.getCategory() != null) {
			gen.writeObjectPropertyStart("category");
			gen.writeNumberProperty("id", value.getCategory().getId());
			gen.writeStringProperty("category", value.getCategory().getCategory());
			gen.writeEndObject();
		} else {
			gen.writeNullProperty("category");
		}

		gen.writeName("expirationDate");
		ctxt.writeValue(gen, value.getExpirationDate());

		gen.writeNumberProperty("daysExpired", DAYS.between(Instant.ofEpochMilli(value.getExpirationDate().getTime())
				.atZone(ZoneId.systemDefault()).toLocalDateTime(), LocalDateTime.now()));

		String componentsStr = JsonUtiles
				.getObjectMapper(ComponentCli2.class, new ComponentCli2JsonSerializer(ComponentCli2.class), null)
				.writeValueAsString(value.getComponents());
		gen.writeName("components");
		gen.writeRawValue(componentsStr);

		gen.writeEndObject();

	}

}
