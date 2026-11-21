# Contributing

Thanks for helping! Nod is small on purpose. Changes that make it simpler, more reliable or more
pleasant to use are the most welcome.

1. **Open an issue first** for anything bigger than a fix, so we can agree on the approach.
2. **Set up** the repo and run the checks: see [docs/development.md](docs/development.md).
3. **Keep the rules:** hooks fail open, protocol changes are mirrored in every client, UI follows
   the design tokens. Everything is in English.
4. **Before you push:** `npm run typecheck && npm test && npm run e2e && npm run lint`, plus the
   Android or desktop build if you touched them.
5. **Pull request:** describe what and why, attach screenshots for UI changes, and add a line
   under "Unreleased" in [CHANGELOG.md](CHANGELOG.md).

By contributing you agree that your contribution is licensed under the [MIT license](LICENSE).
