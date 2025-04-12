package org.idp.server.core.basic.protcol;

import org.idp.server.core.basic.datasource.DataSourceContainer;

public interface ProtocolProvider<T> {

  Class<T> type();

  T provide(DataSourceContainer container);
}
