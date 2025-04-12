package org.idp.server.core.basic.dependency.protcol;

import org.idp.server.core.basic.dependency.ApplicationComponentContainer;

public interface ProtocolProvider<T> {

  Class<T> type();

  T provide(ApplicationComponentContainer container);
}
