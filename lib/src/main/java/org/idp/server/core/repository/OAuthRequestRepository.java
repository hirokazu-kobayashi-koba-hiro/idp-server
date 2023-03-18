package org.idp.server.core.repository;

import org.idp.server.core.oauth.OAuthRequestContext;
import org.idp.server.core.oauth.OAuthRequestIdentifier;

/** OAuthRequestRepository */
public interface OAuthRequestRepository {
  void register(OAuthRequestContext oAuthRequestContext);

  OAuthRequestContext get(OAuthRequestIdentifier oAuthRequestIdentifier);
}
