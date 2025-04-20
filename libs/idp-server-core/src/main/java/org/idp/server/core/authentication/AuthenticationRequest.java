package org.idp.server.core.authentication;

import java.time.LocalDateTime;
import java.util.List;
import org.idp.server.core.oauth.client.Client;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.AuthorizationFlow;
import org.idp.server.core.type.oauth.RequestedClientId;

public class AuthenticationRequest {

  AuthorizationFlow authorizationFlow;
  TenantIdentifier tenantIdentifier;
  RequestedClientId requestedClientId;
  User user;
  List<String> availableAuthenticationTypes;
  List<String> requiredAuthenticationTypes;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
  LocalDateTime expiredAt;
}
