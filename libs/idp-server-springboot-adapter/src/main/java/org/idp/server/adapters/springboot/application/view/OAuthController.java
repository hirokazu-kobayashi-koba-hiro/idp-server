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

package org.idp.server.adapters.springboot.application.view;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.idp.server.adapters.springboot.application.restapi.ParameterTransformable;
import org.idp.server.core.openid.oauth.OAuthFlowApi;
import org.idp.server.core.openid.oauth.io.OAuthRequestResponse;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequestMapping("{tenant-id}/view/v1/authorizations")
public class OAuthController implements ParameterTransformable {

  LoggerWrapper log = LoggerWrapper.getLogger(OAuthController.class);
  OAuthFlowApi oAuthFlowApi;

  public OAuthController(IdpServerApplication idpServerApplication) {
    this.oAuthFlowApi = idpServerApplication.oAuthFlowApi();
  }

  @GetMapping
  public Object get(
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest,
      Model model) {

    Map<String, String[]> params = transform(request);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    OAuthRequestResponse response =
        oAuthFlowApi.request(tenantIdentifier, params, requestAttributes);

    switch (response.status()) {
      case OK -> {
        log.debug(
            "oauth request: status={}, tenant={}, request_id={}",
            response.status(),
            tenantIdentifier.value(),
            response.authorizationRequestId());
        return new RedirectView(
            String.format(
                "/signin/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenantIdentifier.value()));
      }
      case OK_SESSION_ENABLE -> {
        log.debug(
            "oauth request: status={}, tenant={}, request_id={}",
            response.status(),
            tenantIdentifier.value(),
            response.authorizationRequestId());
        return new RedirectView(
            String.format(
                "/signin/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenantIdentifier.value()));
      }
      case OK_ACCOUNT_CREATION -> {
        log.debug(
            "oauth request: status={}, tenant={}, request_id={}",
            response.status(),
            tenantIdentifier.value(),
            response.authorizationRequestId());
        return new RedirectView(
            String.format(
                "/signup/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenantIdentifier.value()));
      }
      case NO_INTERACTION_OK, REDIRECABLE_BAD_REQUEST -> {
        log.debug(
            "oauth request: status={}, tenant={}, redirect_uri={}",
            response.status(),
            tenantIdentifier.value(),
            response.redirectUri());
        return "redirect:" + response.redirectUri();
      }
      default -> {
        log.warn(
            "oauth request failed: tenant={}, error={}, description={}",
            tenantIdentifier.value(),
            response.error(),
            response.errorDescription());

        return new RedirectView(
            String.format(
                "/error/index.html?error=%s&error_description=%s",
                response.error(), response.errorDescription()));
      }
    }
  }

  @PostMapping
  public Object post(
      @RequestParam(required = false) MultiValueMap<String, String> request,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      HttpServletRequest httpServletRequest,
      Model model) {

    Map<String, String[]> params = transform(request);
    RequestAttributes requestAttributes = transform(httpServletRequest);

    OAuthRequestResponse response =
        oAuthFlowApi.request(tenantIdentifier, params, requestAttributes);

    switch (response.status()) {
      case OK -> {
        log.debug(
            "oauth request: status={}, tenant={}, request_id={}",
            response.status(),
            tenantIdentifier.value(),
            response.authorizationRequestId());
        return new RedirectView(
            String.format(
                "/signin/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenantIdentifier.value()));
      }
      case OK_SESSION_ENABLE -> {
        log.debug(
            "oauth request: status={}, tenant={}, request_id={}",
            response.status(),
            tenantIdentifier.value(),
            response.authorizationRequestId());
        return new RedirectView(
            String.format(
                "/signin/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenantIdentifier.value()));
      }
      case OK_ACCOUNT_CREATION -> {
        log.debug(
            "oauth request: status={}, tenant={}, request_id={}",
            response.status(),
            tenantIdentifier.value(),
            response.authorizationRequestId());
        return new RedirectView(
            String.format(
                "/signup/index.html?id=%s&tenant_id=%s",
                response.authorizationRequestId(), tenantIdentifier.value()));
      }
      case NO_INTERACTION_OK, REDIRECABLE_BAD_REQUEST -> {
        log.debug(
            "oauth request: status={}, tenant={}, redirect_uri={}",
            response.status(),
            tenantIdentifier.value(),
            response.redirectUri());
        return "redirect:" + response.redirectUri();
      }
      default -> {
        log.warn(
            "oauth request failed: tenant={}, error={}, description={}",
            tenantIdentifier.value(),
            response.error(),
            response.errorDescription());

        return new RedirectView(
            String.format(
                "/error/index.html?error=%s&error_description=%s",
                response.error(), response.errorDescription()));
      }
    }
  }
}
