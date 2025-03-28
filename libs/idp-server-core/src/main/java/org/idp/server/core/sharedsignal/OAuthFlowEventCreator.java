package org.idp.server.core.sharedsignal;

import java.util.Map;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.tenant.Tenant;

public class OAuthFlowEventCreator {

  Tenant tenant;
  AuthorizationRequest authorizationRequest;
  User user;
  EventType eventType;
  EventDescription eventDescription;

  public OAuthFlowEventCreator(
      Tenant tenant,
      AuthorizationRequest authorizationRequest,
      User user,
      DefaultEventType defaultEventType) {
    this.tenant = tenant;
    this.authorizationRequest = authorizationRequest;
    this.user = user;
    this.eventType = defaultEventType.toEventType();
    this.eventDescription = defaultEventType.toEventDescription();
  }

  public OAuthFlowEventCreator(
      AuthorizationRequest authorizationRequest,
      User user,
      EventType eventType,
      EventDescription eventDescription) {
    this.authorizationRequest = authorizationRequest;
    this.user = user;
    this.eventType = eventType;
    this.eventDescription = eventDescription;
  }

  public Event create() {
    EventBuilder builder = new EventBuilder();
    builder.add(eventType);
    builder.add(eventDescription);

    EventTenant eventTenant =
        new EventTenant(tenant.identifier(), tenant.tokenIssuer(), tenant.name().value());
    builder.add(eventTenant);

    EventClient eventClient = new EventClient(authorizationRequest.clientId(), authorizationRequest.clientNameValue());
    builder.add(eventClient);

    EventUser eventUser = new EventUser(user.sub(), user.name());
    builder.add(eventUser);

    EventDetail eventDetail = new EventDetail(Map.of("user", user.toMap()));

    builder.add(eventDetail);

    return builder.build();
  }
}
