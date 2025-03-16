package org.idp.server.core.handler.config;

import java.util.List;

public class MemoryDataSourceConfig {

  List<String> serverConfigurations;
  List<String> clientConfigurations;

  public MemoryDataSourceConfig(
      List<String> serverConfigurations, List<String> clientConfigurations) {
    this.serverConfigurations = serverConfigurations;
    this.clientConfigurations = clientConfigurations;
  }

  public List<String> serverConfigurations() {
    return serverConfigurations;
  }

  public List<String> clientConfigurations() {
    return clientConfigurations;
  }
}
