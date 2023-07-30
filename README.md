# idp-server

## Overview
This library provides java api supported OAuth2.0 and OIDC spec.


## Architecture

![architecture](./architecture.svg)

## supported spec

1. RFC6749 The OAuth 2.0 Authorization Framework
   1. authorization code grant
   2. implicit grant
   3. resource owner password credentials grant
   4. client credentials grant
2. OpenID Connect Core 1.0 incorporating errata set 1
   1. authorization code flow
   2. implicit flow
   3. hybrid flow
   4. request object
      1. signature
      2. encryption
      3. signature none
   5. userinfo
3. OpenID Connect Discovery 1.0 incorporating errata set 1
4. OpenID Connect Client-Initiated Backchannel Authentication Flow - Core 1.0
   1. poll mode
   2. ping mode
   3. push mode
5. RFC7009 OAuth 2.0 Token Revocation
6. RFC7636 Proof Key for Code Exchange by OAuth Public Clients
7. RFC7662 OAuth 2.0 Token Introspection
8. Financial-grade API Security Profile 1.0 - Part 1: Baseline
9. Financial-grade API Security Profile 1.0 - Part 2: Advanced

## supported client authentication

1. client_secret_post
2. client_secret_basic
3. client_secret_jwt
4. private_key_jwt
5. tls_client_auth
6. self_signed_tls_client_auth

## License

Apache License, Version 2.0

## sample server

### bootRun

```shell
./gradlew bootRun
```

```shell
./init.sh
```

### e2e

```shell
cd e2e
jest test
```