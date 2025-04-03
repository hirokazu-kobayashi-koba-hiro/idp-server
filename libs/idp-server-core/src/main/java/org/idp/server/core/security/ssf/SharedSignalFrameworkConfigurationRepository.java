package org.idp.server.core.security.ssf;

public interface SharedSignalFrameworkConfigurationRepository {

  SharedSignalFrameworkConfiguration find(String issuer);
}
