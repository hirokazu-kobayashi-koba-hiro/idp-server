package org.idp.server.core.security;

public interface SecurityEventHookFactory {

  SecurityEventHookExecutor create();
}
