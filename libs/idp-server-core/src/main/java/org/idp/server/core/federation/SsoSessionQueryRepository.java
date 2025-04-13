package org.idp.server.core.federation;

import org.idp.server.core.type.oauth.State;

public interface SsoSessionQueryRepository {

  <T> T get(SsoSessionIdentifier ssoSessionIdentifier, Class<T> clazz);
}
