# idp-server

## Overview
This library provides java api supported OAuth2.0 and OIDC spec.


## Architecture

![architecture](./architecture.svg)

## supported spec

1. RFC6749 The OAuth 2.0 Authorization Framework
   1. authorization code flow
   2. implicit flow
   3. client credentials flow
2. OpenID Connect Core 1.0 incorporating errata set 1
   1. authorization code flow
   2. implicit flow
   3. hybrid flow
   4. request object
   5. userinfo
3. OpenID Connect Discovery 1.0 incorporating errata set 1
4. OpenID Connect Client-Initiated Backchannel Authentication Flow - Core 1.0
   1. poll mode
   2. ping mode
   3. push mode
5. RFC7009 OAuth 2.0 Token Revocation
6. RFC7636 Proof Key for Code Exchange by OAuth Public Clients
7. RFC7662 OAuth 2.0 Token Introspection

## supported client authentication

1. client_secret_post
2. client_secret_basic
3. client_secret_jwt
4. private_key_jwt

## License

Apache License, Version 2.0

## sample server

### bootRun

```shell
./gradlew bootRun
```

### e2e

```shell
cd e2e
jest test
```