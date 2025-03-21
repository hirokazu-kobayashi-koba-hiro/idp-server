package org.idp.server.core.federation;

import org.idp.server.core.type.oauth.State;

public interface FederationSessionRepository {

  void register(FederationSession federationSession);

  FederationSession find(State state);

  void delete(FederationSession federationSession);
}
