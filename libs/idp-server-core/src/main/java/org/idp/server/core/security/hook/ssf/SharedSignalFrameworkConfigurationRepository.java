package org.idp.server.core.security.hook.ssf;

public interface SharedSignalFrameworkConfigurationRepository {

  SharedSignalFrameworkConfiguration find(String issuer);
}
