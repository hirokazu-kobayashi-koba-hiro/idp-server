package org.idp.server.handler.config;

import java.util.List;

public class MemoryDataSourceConfig {

  java.util.List<String> serverConfigurations;
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
