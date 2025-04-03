package org.idp.server.core.security;

import java.util.Map;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.security.event.*;
import org.idp.server.core.tenant.Tenant;

public class OAuthFlowEventCreator {

  Tenant tenant;
  AuthorizationRequest authorizationRequest;
  User user;
  SecurityEventType securityEventType;
  SecurityEventDescription securityEventDescription;

  public OAuthFlowEventCreator(
      Tenant tenant,
      AuthorizationRequest authorizationRequest,
      User user,
      DefaultSecurityEventType defaultSecurityEventType) {
    this.tenant = tenant;
    this.authorizationRequest = authorizationRequest;
    this.user = user;
    this.securityEventType = defaultSecurityEventType.toEventType();
    this.securityEventDescription = defaultSecurityEventType.toEventDescription();
  }

  public OAuthFlowEventCreator(
      AuthorizationRequest authorizationRequest,
      User user,
      SecurityEventType securityEventType,
      SecurityEventDescription securityEventDescription) {
    this.authorizationRequest = authorizationRequest;
    this.user = user;
    this.securityEventType = securityEventType;
    this.securityEventDescription = securityEventDescription;
  }

  public SecurityEvent create() {
    SecurityEventBuilder builder = new SecurityEventBuilder();
    builder.add(securityEventType);
    builder.add(securityEventDescription);

    SecurityEventTenant securityEventTenant =
        new SecurityEventTenant(tenant.identifier(), tenant.tokenIssuer(), tenant.name().value());
    builder.add(securityEventTenant);

    SecurityEventClient securityEventClient =
        new SecurityEventClient(authorizationRequest.clientId(), authorizationRequest.clientNameValue());
    builder.add(securityEventClient);

    SecurityEventUser securityEventUser = new SecurityEventUser(user.sub(), user.name());
    builder.add(securityEventUser);

    SecurityEventDetail securityEventDetail = new SecurityEventDetail(Map.of("user", user.toMap()));

    builder.add(securityEventDetail);

    return builder.build();
  }
}
