package org.idp.server.core.security.factory;

import org.idp.server.core.security.SecurityEventHookExecutor;

public interface SecurityEventHookFactory {

  SecurityEventHookExecutor create();
}
