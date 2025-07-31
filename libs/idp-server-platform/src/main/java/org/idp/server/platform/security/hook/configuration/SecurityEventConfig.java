package org.idp.server.platform.security.hook.configuration;

import org.idp.server.platform.json.JsonReadable;

public class SecurityEventConfig implements JsonReadable {
    SecurityEventExecutionConfig execution = new SecurityEventExecutionConfig();

    public SecurityEventConfig() {}

    public SecurityEventExecutionConfig execution() {
        if (execution == null) {
            return new SecurityEventExecutionConfig();
        }
        return execution;
    }
}
