# Overview
## Contributing

We welcome contributions to **idp-server** — whether it's code, docs, feedback, or ideas.

## How to Contribute

### 1. Fork the Repository

```bash
git clone https://github.com/YOUR_USERNAME/idp-server.git
```

### 2. Create a Feature Branch

```bash
git checkout -b feat/your-feature-name
```

### 3. Make Your Changes

- Follow the existing code style (Java + Gradle)
- Use clean architecture principles
- Write unit/integration tests when possible

### 4. Commit and Push

```bash
git commit -m "feat: Add X feature"
git push origin feat/your-feature-name
```

### 5. Create a Pull Request

Submit a PR to the `main` branch.  
Describe your changes clearly and link to any related issues.

## Development Setup

- Java 21+
- Gradle (wrapper included)
- PostgreSQL / MySQL (DDL included in `/ddl`)
- Optional: Redis, Node.js frontend

```bash
./gradlew build
./gradlew bootRun
```

## Documentation

- Docs are written in Markdown under `docs/`
- Use Docusaurus to build:

```bash
cd docs-site
npm install
npm run start
```

## Code Structure

```
libs/
├── core/        # Domain logic
├── adapters/    # DB, other I/O
├── use-cases/ # Use cases
├── spring-boot-adapter/         # REST endpoints
```

## Code Style

- Java 21
- Use snake_case for DB column names
- Prefer constructor injection over field injection
- Avoid framework-specific annotations in core modules

## Feedback & Issues

Open an issue or discussion on [GitHub](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues)  
We appreciate bug reports, feature requests, and architecture discussions!

---

Thank you for contributing to **idp-server**
