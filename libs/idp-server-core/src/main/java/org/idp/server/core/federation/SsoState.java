package org.idp.server.core.federation;

import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.tenant.TenantIdentifier;

import java.io.Serializable;

public class SsoState implements Serializable, JsonReadable {
    String sessionId;
    String tenantId;

    public SsoState() {}

    public SsoState(String sessionId, String tenantId) {
        this.sessionId = sessionId;
        this.tenantId = tenantId;
    }

    public SsoSessionIdentifier ssoSessionIdentifier() {
        return new SsoSessionIdentifier(sessionId);
    }

    public TenantIdentifier tenantIdentifier() {
        return new TenantIdentifier(tenantId);
    }
}
