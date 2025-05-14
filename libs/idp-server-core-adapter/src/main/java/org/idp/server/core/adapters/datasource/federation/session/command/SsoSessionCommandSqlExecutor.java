package org.idp.server.core.adapters.datasource.federation.session.command;

import org.idp.server.core.federation.sso.SsoSessionIdentifier;

public interface SsoSessionCommandSqlExecutor {

  <T> void insert(SsoSessionIdentifier identifier, T payload);

  void delete(SsoSessionIdentifier identifier);
}
