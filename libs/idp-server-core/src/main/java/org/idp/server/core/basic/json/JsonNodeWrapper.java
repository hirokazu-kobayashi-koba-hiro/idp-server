package org.idp.server.core.basic.json;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JsonNodeWrapper {
    JsonNode jsonNode;

    public JsonNodeWrapper(JsonNode jsonNode) {
        this.jsonNode = jsonNode;

    }

    public Iterator<String> fieldNames() {
        return jsonNode.fieldNames();
    }

    public JsonNodeWrapper getValueAsJsonNode(String fieldName) {
        return new JsonNodeWrapper(jsonNode.get(fieldName));
    }

    public List<JsonNodeWrapper> getValueAsJsonNodeList(String fieldName) {
        List<JsonNodeWrapper> values = new ArrayList<>();
        Iterator<JsonNode> iterator = jsonNode.get(fieldName).elements();
        while (iterator.hasNext()) {
            values.add(new JsonNodeWrapper(iterator.next()));
        }
        return values;
    }

    public Object node() {
        return jsonNode;
    }

    public String getValueOrEmptyAsString(String fieldName) {
        return jsonNode.get(fieldName).asText("");
    }
}
