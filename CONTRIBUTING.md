# Contributing to idp-server

Thank you for considering contributing to **idp-server** – an open-source Identity Provider built for extensibility,
modern protocols, and developer empowerment.

We welcome feedback, bug reports, and pull requests from the community. This guide explains how to contribute
effectively and respectfully.

---

## 🧭 Before you start

* For larger or architectural changes, **please open a GitHub Discussion** first to align with maintainers and get early
  feedback.
* For minor changes (e.g. bug fixes, typo corrections), feel free to go straight to creating an issue and submitting a
  pull request.

---

## ✅ Pull Request Checklist

To help us review your contribution smoothly, please ensure your PR follows these guidelines:

1. 💬 Discussion started beforehand for larger
   changes ([GitHub Discussions](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/discussions))
2. 🐛 A related GitHub Issue is created with a good description
3. 🎯 One feature or fix per PR
4. 📆 PR is a single commit (`git rebase`, not `git merge`). See below for how to do this.
5. 📘 Commit message follows format and links to issue (e.g., `Closes #123`)
6. 🔍 Only modify code related to your change
7. 🧪 Includes unit/integration tests where applicable
8. 📓 Includes documentation updates if applicable

## 🌿 Branch Naming Convention

To keep our repository organized, please follow this convention when naming your feature branches:

```bash
feat/short-description-of-change
fix/bug-description
docs/update-readme
refactor/module-name
test/add-coverage-for-x
```

### Examples

| Type     | Branch name                         |
|----------|-------------------------------------|
| Feature  | `feat/access-token-jwe-support`     |
| Bug fix  | `fix/refresh-token-expiry-check`    |
| Docs     | `docs/contributing-guide-update`    |
| Refactor | `refactor/token-service-cleanup`    |
| Test     | `test/token-introspection-endpoint` |

Please use **kebab-case** (lowercase with dashes) for readability.

---

When creating a Pull Request, name the branch appropriately so others can quickly understand the purpose of your
contribution.

### 🔧 How to squash commits into one using rebase

If you have multiple commits in your PR, please squash them into a single commit before submitting:

```bash
git rebase -i origin/main
```

In the interactive screen:

* Keep `pick` for the first commit
* Change the rest to `squash` or `s`
* Save and edit the commit message if needed

Then force-push your branch:

```bash
git push --force-with-lease
```

---

## 🥪 Testing

* Write tests in the style of existing tests
* Do not introduce new mocking or testing frameworks unless discussed
* Tests should run with `./gradlew test` and pass CI

---

## 📚 Documentation

* Please update the [Docusaurus documentation](https://your-docusaurus-site.com) if your change affects public API or
  behavior.
* Include example configurations or API payloads where possible.

---

## 📅 Commit message format

Use the following format for your commits:

```text
Add support for XYZ feature

Detailed explanation if needed.

Closes #123
```

You can use multiple `-m` flags to create multiline messages:

```bash
git commit -m "Add support for XYZ feature" -m "Closes #123"
```

---

## 🔒 Developer's Certificate of Origin (DCO)

By contributing, you assert that your code can be legally included in the project under the Apache License 2.0.

Please add a signoff to each commit:

```bash
git commit --signoff
```

This will append a line like:

```
Signed-off-by: Your Name <your@email.com>
```

---

## 🗂️ Finding something to work on

You can look for issues labeled with [
`good first issue`](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/labels/good%20first%20issue) or [
`help wanted`](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/labels/help%20wanted).

If you’re unsure, feel free to open a GitHub Discussion to propose your idea!

---

## 💬 Contact

Feel free to discuss in [GitHub Discussions](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/discussions) or
contact the maintainers directly by opening an issue.

We appreciate your support in building a strong, open identity platform.
