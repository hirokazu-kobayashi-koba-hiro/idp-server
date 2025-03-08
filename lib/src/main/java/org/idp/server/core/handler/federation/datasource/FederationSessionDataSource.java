package org.idp.server.core.handler.federation.datasource;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.federation.FederationSession;
import org.idp.server.core.federation.FederationSessionRepository;
import org.idp.server.core.type.oauth.State;
import org.idp.server.core.type.oauth.TokenIssuer;

public class FederationSessionDataSource implements FederationSessionRepository {

  Map<String, FederationSession> map = new HashMap<>();

  @Override
  public void register(TokenIssuer tokenIssuer, FederationSession federationSession) {
    map.put(federationSession.state(), federationSession);
  }

  @Override
  public FederationSession find(TokenIssuer tokenIssuer, State state) {
    FederationSession federationSession = map.get(state.value());

    if (Objects.isNull(federationSession)) {
      return new FederationSession();
    }

    return federationSession;
  }

  @Override
  public void delete(TokenIssuer tokenIssuer, FederationSession federationSession) {

    map.remove(federationSession.state());
  }
}
