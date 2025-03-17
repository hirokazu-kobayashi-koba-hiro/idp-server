package org.idp.server.core.api;

import java.util.Map;

public interface IdpServerStarterApi {

  Map<String, Object> initialize(Map<String, Object> request);
}
