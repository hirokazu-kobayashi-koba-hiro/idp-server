package org.idp.server.core.adapters.datasource.federation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.federation.FederationSession;
import org.idp.server.core.federation.FederationSessionRepository;
import org.idp.server.core.type.oauth.State;

public class FederationSessionDataSource implements FederationSessionRepository {

  Map<String, FederationSession> map = new HashMap<>();

  @Override
  public void register(FederationSession federationSession) {
    if (map.size() > 10) {
      map.clear();
    }
    map.put(federationSession.state(), federationSession);
  }

  @Override
  public FederationSession find(State state) {
    FederationSession federationSession = map.get(state.value());

    if (Objects.isNull(federationSession)) {
      return new FederationSession();
    }

    return federationSession;
  }

  @Override
  public void delete(FederationSession federationSession) {
    map.remove(federationSession.state());
  }
}
