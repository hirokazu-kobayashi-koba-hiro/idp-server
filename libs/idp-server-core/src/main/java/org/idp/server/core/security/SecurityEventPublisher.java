package org.idp.server.core.security;

public interface SecurityEventPublisher {

  void publish(SecurityEvent securityEvent);
}
