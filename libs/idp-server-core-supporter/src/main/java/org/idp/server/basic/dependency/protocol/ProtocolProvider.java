package org.idp.server.basic.dependency.protocol;

import org.idp.server.basic.dependency.ApplicationComponentContainer;

public interface ProtocolProvider<T> {

  Class<T> type();

  T provide(ApplicationComponentContainer container);
}
