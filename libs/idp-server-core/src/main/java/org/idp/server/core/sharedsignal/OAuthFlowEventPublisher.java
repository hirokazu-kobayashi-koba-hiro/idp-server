package org.idp.server.core.sharedsignal;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.tenant.Tenant;

public class OAuthFlowEventPublisher {

  EventPublisher eventPublisher;

  public OAuthFlowEventPublisher(EventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  public void publish(
      Tenant tenant, AuthorizationRequest authorizationRequest, User user, DefaultEventType type) {
    OAuthFlowEventCreator eventCreator =
        new OAuthFlowEventCreator(tenant, authorizationRequest, user, type);
    Event event = eventCreator.create();
    eventPublisher.publish(event);
  }
}
