package org.idp.server.core.sharedsignal;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class EventDetail {
    Map<String, Object> values;


    public EventDetail() {
        values = new HashMap<>();
    }

    public EventDetail(Map<String, Object> values) {
        this.values = values;
    }

    public Map<String, Object> toMap() {
        return values;
    }

    public boolean exists() {
        return Objects.nonNull(values) && !values.isEmpty();
    }

}
