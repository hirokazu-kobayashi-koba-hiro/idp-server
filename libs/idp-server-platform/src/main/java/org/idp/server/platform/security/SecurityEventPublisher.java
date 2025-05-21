package org.idp.server.platform.security;

public interface SecurityEventPublisher {

  void publish(SecurityEvent securityEvent);
}
