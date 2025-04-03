package org.idp.server.core.security;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.security.event.DefaultSecurityEventType;
import org.idp.server.core.tenant.Tenant;

public class OAuthFlowEventPublisher {

  SecurityEventPublisher securityEventPublisher;

  public OAuthFlowEventPublisher(SecurityEventPublisher securityEventPublisher) {
    this.securityEventPublisher = securityEventPublisher;
  }

  public void publish(
      Tenant tenant, AuthorizationRequest authorizationRequest, User user, DefaultSecurityEventType type) {
    OAuthFlowEventCreator eventCreator =
        new OAuthFlowEventCreator(tenant, authorizationRequest, user, type);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }
}
