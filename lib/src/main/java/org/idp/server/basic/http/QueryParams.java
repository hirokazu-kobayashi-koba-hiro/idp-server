package org.idp.server.basic.http;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class QueryParams {
    Map<String, String> values;

    public QueryParams() {
        this.values = new HashMap<>();
    }

    public QueryParams(Map<String, String> values) {
        this.values = values;
    }

    public void add(String key, String value) {
        values.put(key, value);
    }
    public String params() {
        StringBuilder stringBuilder = new StringBuilder();
        Set<Map.Entry<String, String>> entries = values.entrySet();
        entries.forEach(entry -> {
            if (Objects.nonNull(entry.getValue()) && !entry.getValue().isEmpty()) {
                stringBuilder.append(entry.getKey());
                stringBuilder.append("=");
                stringBuilder.append(entry.getValue());
            }
        });
        return stringBuilder.toString();
    }
}
