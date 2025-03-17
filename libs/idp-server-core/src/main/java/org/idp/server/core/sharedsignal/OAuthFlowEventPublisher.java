package org.idp.server.core.sharedsignal;

import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequest;

public class OAuthFlowEventPublisher {

    EventPublisher eventPublisher;

    public OAuthFlowEventPublisher(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(
            AuthorizationRequest authorizationRequest, User user, DefaultEventType type) {
        OAuthFlowEventCreator eventCreator =
                new OAuthFlowEventCreator(authorizationRequest, user, type);
        Event event = eventCreator.create();
        eventPublisher.publish(event);
    }
}
