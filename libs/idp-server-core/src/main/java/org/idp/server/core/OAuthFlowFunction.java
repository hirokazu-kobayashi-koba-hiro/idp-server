package org.idp.server.core;

import org.idp.server.core.handler.oauth.io.*;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionResult;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionType;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.extension.Pairs;
import java.util.Map;

public interface OAuthFlowFunction {
     Pairs<Tenant, OAuthRequestResponse> request(
            TenantIdentifier tenantIdentifier, Map<String, String[]> params);

    OAuthViewDataResponse getViewData(
            TenantIdentifier tenantIdentifier, String oauthRequestIdentifier);

    OAuthUserInteractionResult interact(TenantIdentifier tenantIdentifier, String oauthRequestIdentifier, OAuthUserInteractionType type, Map<String, Object> params);

    OAuthAuthorizeResponse authorize(
            TenantIdentifier tenantIdentifier, String oauthRequestIdentifier);

    OAuthAuthorizeResponse authorizeWithSession(
            TenantIdentifier tenantIdentifier, String oauthRequestIdentifier);

    OAuthDenyResponse deny(TenantIdentifier tenantIdentifier, String oauthRequestIdentifier);

    OAuthLogoutResponse logout(
            TenantIdentifier tenantIdentifier, Map<String, String[]> params);
}
