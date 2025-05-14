package org.idp.server.core.adapters.datasource.security.hook.configuration.query;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.security.hook.SecurityEventHookConfiguration;

import java.util.Map;

class ModelConverter {

    public static SecurityEventHookConfiguration convert(Map<String, String> result) {
        JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
        return jsonConverter.read(result.get("payload"), SecurityEventHookConfiguration.class);
    }
}
