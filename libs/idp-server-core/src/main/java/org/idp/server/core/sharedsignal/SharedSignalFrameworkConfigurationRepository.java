package org.idp.server.core.sharedsignal;

public interface SharedSignalFrameworkConfigurationRepository {

  SharedSignalFrameworkConfiguration find(String issuer);
}
