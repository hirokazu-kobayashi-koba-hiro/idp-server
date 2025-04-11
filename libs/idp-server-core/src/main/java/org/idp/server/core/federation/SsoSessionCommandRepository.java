package org.idp.server.core.federation;

public interface SsoSessionCommandRepository {

  <T> void register(SsoSessionIdentifier identifier, T payload);

  void delete(SsoSessionIdentifier identifier);
}
