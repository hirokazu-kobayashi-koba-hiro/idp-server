package org.idp.server.datasource.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.OAuthRequestIdentifier;
import org.idp.server.core.repository.OAuthRequestRepository;

/** OAuthRequestMemoryDataSource */
public class OAuthRequestMemoryDataSource implements OAuthRequestRepository {

  Map<OAuthRequestIdentifier, OAuthRequestContext> map;

  public OAuthRequestMemoryDataSource() {
    this.map = new HashMap<>();
  }

  @Override
  public void register(OAuthRequestContext oAuthRequestContext) {
    map.put(oAuthRequestContext.identifier(), oAuthRequestContext);
  }

  @Override
  public OAuthRequestContext get(OAuthRequestIdentifier oAuthRequestIdentifier) {
    OAuthRequestContext oAuthRequestContext = map.get(oAuthRequestIdentifier);
    if (Objects.isNull(oAuthRequestContext)) {
      throw new RuntimeException(
          String.format("not found oauth request (%s)", oAuthRequestIdentifier.value()));
    }
    return oAuthRequestContext;
  }
}
