package org.idp.server.core.basic.protcol;

import org.idp.server.core.basic.dependencies.ApplicationComponentContainer;

public interface ProtocolProvider<T> {

  Class<T> type();

  T provide(ApplicationComponentContainer container);
}
