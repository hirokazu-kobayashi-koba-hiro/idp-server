package org.idp.server.core.admin;

import java.util.Map;
import org.idp.server.core.oauth.identity.User;

public interface OnboardingApi {

  Map<String, Object> initialize(User operator, Map<String, Object> request);
}
