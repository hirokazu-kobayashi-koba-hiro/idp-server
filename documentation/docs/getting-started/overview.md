# Overview

## Getting Started

This guide walks you through setting up and running the **idp-server** for the first time.

## Prerequisites

- Java 17 or higher
- PostgreSQL or MySQL
- Node.js (if using with frontend)
- Docker (optional, for container-based setup)

## Installation

### 1. Clone the Repository

```bash
git clone https://github.com/hirokazu-kobayashi-koba-hiro/idp-server.git
cd idp-server
```

* set up

※ fix your configuration

```shell
export ADDRESS=0xf1232f840f3ad7d23fcdaa84d6c66dac24efb198
export PRIVATE_KEY=d8b595680851765f38ea5405129244ba3cbad84467d190859f4c8b20c1ff6c75
export WEB3_URL=wss://eth-sepolia.g.alchemy.com/v2/xxx
export VERIFICATION_Method=did:web:assets.dev.trustid.sbi-fc.com#key-2
export CHAIN=ethereum_sepolia

docker-compose up -d
```

* init table

```shell
./gradlew flywayClean flywayMigrate
```

* generate api-key and api-secret

```shell
./init.sh
```

* set variable

```shell
export IDP_SERVER_DOMAIN=http://localhost:8080/
export IDP_SERVER_API_KEY=xxx
export IDP_SERVER_API_SECRET=xxx
export ENCRYPTION_KEY=xxx
```

### bootRun

```shell
./gradlew bootRun
```

### setup configuration

```shell
./setup.sh
```

### e2e

```shell
cd e2e
npm install
npm test
```