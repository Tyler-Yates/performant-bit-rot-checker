package com.bitrot.data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import org.bson.types.ObjectId;

import java.io.IOException;

public class ObjectIdDeserializer extends JsonDeserializer<ObjectId> {

    @Override
    public ObjectId deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException {
        final JsonNode oid = ((JsonNode) p.readValueAsTree()).get("$oid");
        return new ObjectId(oid.asText());
    }
}