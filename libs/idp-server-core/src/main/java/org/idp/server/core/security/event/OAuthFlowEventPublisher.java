package org.idp.server.core.security.event;

import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventPublisher;

public class OAuthFlowEventPublisher {

  SecurityEventPublisher securityEventPublisher;

  public OAuthFlowEventPublisher(SecurityEventPublisher securityEventPublisher) {
    this.securityEventPublisher = securityEventPublisher;
  }

  public void publish(
      Tenant tenant,
      AuthorizationRequest authorizationRequest,
      User user,
      DefaultSecurityEventType type,
      RequestAttributes requestAttributes) {
    OAuthFlowEventCreator eventCreator =
        new OAuthFlowEventCreator(tenant, authorizationRequest, user, type, requestAttributes);
    SecurityEvent securityEvent = eventCreator.create();
    securityEventPublisher.publish(securityEvent);
  }
}
