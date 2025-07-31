package org.idp.server.platform.security.hook.configuration;

import org.idp.server.platform.json.JsonReadable;

import java.util.HashMap;
import java.util.Map;

public class SecurityEventExecutionConfig implements JsonReadable {
    String type;
    SecurityEventHttpRequestConfig httpRequest = new SecurityEventHttpRequestConfig();
    SecurityEventMockConfig mock = new SecurityEventMockConfig();
    Map<String, Object> details = new HashMap<>();

    public SecurityEventExecutionConfig() {}

    public String type() {
        return type;
    }

    public SecurityEventHttpRequestConfig httpRequest() {
        if (httpRequest == null) {
            return new SecurityEventHttpRequestConfig();
        }
        return httpRequest;
    }

    public boolean hasHttpRequest() {
        return httpRequest != null && httpRequest.exists();
    }

    public SecurityEventMockConfig mock() {
        if (mock == null) {
            return new SecurityEventMockConfig();
        }
        return mock;
    }

    public boolean hasMock() {
        return mock != null;
    }

    public Map<String, Object> details() {
        return details;
    }

    public boolean exists() {
        return type != null && !type.isEmpty();
    }
}
