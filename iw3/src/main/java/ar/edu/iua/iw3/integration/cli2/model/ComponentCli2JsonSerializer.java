package ar.edu.iua.iw3.integration.cli2.model;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

public class ComponentCli2JsonSerializer extends StdSerializer<ComponentCli2> {

	private static final long serialVersionUID = -3706327488880784297L;

	public ComponentCli2JsonSerializer(Class<?> t) {
		super(t);
	}

	@Override
    public void serialize(
            ComponentCli2 value,
            JsonGenerator gen,
            SerializationContext ctxt) throws JacksonException {
		gen.writeStartObject();
		gen.writeNumberProperty("id", value.getId());
		gen.writeStringProperty("component", value.getComponent());
		gen.writeEndObject();
	}

}

