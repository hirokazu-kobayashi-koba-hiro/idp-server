package org.idp.server.core.sharedsignal;

import org.idp.server.core.type.oauth.TokenIssuer;

public interface EventHandlerDelegate {

  void register(Event event);

  void notify(Event event, SsfConfiguration ssfConfiguration);

  Events find(TokenIssuer tokenIssuer, EventIdentifier eventIdentifier);

  Events search(TokenIssuer tokenIssuer, String userId);
}
