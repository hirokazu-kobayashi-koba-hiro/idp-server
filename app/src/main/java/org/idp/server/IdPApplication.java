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

package org.idp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// idp-server has its own authentication; exclude Spring Security's default in-memory
// UserDetailsService so it does not auto-generate a dev user / random password at startup (#1348).
// excludeName (by FQCN) is used because the auto-configuration class lives in the runtime-only
// spring-boot-security module (Spring Boot 4.x) and is not on the compile classpath.
@SpringBootApplication(
    excludeName =
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class IdPApplication {

  public static void main(String[] args) {
    SpringApplication.run(IdPApplication.class, args);
  }
}
