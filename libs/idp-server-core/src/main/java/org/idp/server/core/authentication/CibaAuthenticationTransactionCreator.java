package org.idp.server.core.authentication;

import java.time.LocalDateTime;
import java.util.List;
import org.idp.server.core.ciba.handler.io.CibaIssueResponse;
import org.idp.server.core.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.basic.type.AuthorizationFlow;
import org.idp.server.basic.type.oauth.RequestedClientId;

public class CibaAuthenticationTransactionCreator {

  Tenant tenant;
  CibaIssueResponse issueResponse;

  public AuthenticationRequest toAuthenticationRequest() {
    AuthorizationFlow authorizationFlow;
    TenantIdentifier tenantIdentifier;
    RequestedClientId requestedClientId;
    User user;
    List<String> availableAuthenticationTypes;
    List<String> requiredAuthenticationTypes;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    LocalDateTime expiredAt;
    return new AuthenticationRequest();
  }
}
