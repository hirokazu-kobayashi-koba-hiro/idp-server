package org.idp.server.platform.dependency.protocol;

import org.idp.server.platform.dependency.ApplicationComponentContainer;

public interface ProtocolProvider<T> {

  Class<T> type();

  T provide(ApplicationComponentContainer container);
}
