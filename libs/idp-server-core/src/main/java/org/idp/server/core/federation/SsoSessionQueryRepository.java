package org.idp.server.core.federation;

public interface SsoSessionQueryRepository {

  <T> T get(SsoSessionIdentifier ssoSessionIdentifier, Class<T> clazz);
}
