package org.idp.server.core.api;

import java.util.Map;
import org.idp.server.core.authentication.MfaInteractionResult;
import org.idp.server.core.authentication.MfaInteractionType;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;
import org.idp.server.core.oauth.io.*;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.extension.Pairs;

public interface OAuthFlowApi {
  Pairs<Tenant, OAuthRequestResponse> request(
      TenantIdentifier tenantIdentifier, Map<String, String[]> params);

  OAuthViewDataResponse getViewData(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier);

  MfaInteractionResult interact(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      MfaInteractionType type,
      Map<String, Object> params);

  FederationRequestResponse requestFederation(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      String federationIdentifier);

  Pairs<Tenant, FederationCallbackResponse> callbackFederation(Map<String, String[]> params);

  OAuthAuthorizeResponse authorize(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier);

  OAuthAuthorizeResponse authorizeWithSession(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier);

  OAuthDenyResponse deny(TenantIdentifier tenantIdentifier, String oauthRequestIdentifier);

  OAuthLogoutResponse logout(TenantIdentifier tenantIdentifier, Map<String, String[]> params);
}
