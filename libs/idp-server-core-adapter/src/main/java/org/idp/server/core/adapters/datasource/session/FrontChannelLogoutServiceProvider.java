/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.session;

import org.idp.server.core.openid.session.logout.DefaultFrontChannelLogoutService;
import org.idp.server.core.openid.session.logout.FrontChannelLogoutService;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class FrontChannelLogoutServiceProvider
    implements ApplicationComponentProvider<FrontChannelLogoutService> {

  @Override
  public Class<FrontChannelLogoutService> type() {
    return FrontChannelLogoutService.class;
  }

  @Override
  public FrontChannelLogoutService provide(ApplicationComponentDependencyContainer container) {
    return new DefaultFrontChannelLogoutService();
  }
}
