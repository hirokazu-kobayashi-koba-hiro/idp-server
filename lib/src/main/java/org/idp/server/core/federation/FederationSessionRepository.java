package org.idp.server.core.federation;

import org.idp.server.core.type.oauth.State;
import org.idp.server.core.type.oauth.TokenIssuer;

public interface FederationSessionRepository {

  void register(TokenIssuer tokenIssuer, FederationSession federationSession);

  FederationSession find(TokenIssuer tokenIssuer, State state);

  void delete(TokenIssuer tokenIssuer, FederationSession federationSession);
}
