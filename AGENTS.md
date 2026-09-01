# Repository Guidelines

## Project Structure & Module Organization

Modules are built independently:

- `taurus-be/`: Java 17 Spring Boot API. Code is under `src/main/java`; configuration, Liquibase migrations, and Docker files are under `src/main/resources`; tests are under `src/test/java`.
- `taurus-fe/`: Angular 19 client. Features live in `src/app/pages`, shared UI in `components` and `dialogs`, API access in `service`, and assets in `src/assets`.
- `keycloak-authenticator/`: Java 17 Keycloak provider and custom themes, following the standard Maven `src/main`/`src/test` layout.
- `taurus-info/`: Astro marketing site; pages, components, and layouts are in `src`, with public assets in `public`.

## Build, Test, and Development Commands

- Backend: `./mvnw` starts the API; `./mvnw verify` runs checks/tests; `docker compose -f src/main/docker/services.yml up --wait` starts dependencies.
- Frontend: `npm ci`, then `npm start` for development, `npm run build` for production, and `npm test` for Karma/Jasmine tests.
- Keycloak provider: `./mvnw verify` builds and tests the provider JAR.
- Info site: `pnpm install`, `pnpm dev`, `pnpm check`, and `pnpm build`. Use pnpm 11 and Node.js 22.

## Coding Style & Naming Conventions

Honor module-local configuration. Angular uses strict TypeScript, four-space indentation, single quotes, semicolons, and Prettier (`npm run format`). Name Angular files in kebab case with role suffixes, for example `inventory.service.ts`. Java uses four-space indentation and standard package/class naming; backend formatting is enforced by Spotless/Prettier. Keep Astro TypeScript strict and follow existing two-space formatting.

For PrimeNG UI changes in `taurus-fe/`, consult `docs/llms-full.md` and read only the sections relevant to the component.

## Testing Guidelines

Backend tests use JUnit 5. Name unit tests `*Test.java` and integration tests `*IT.java`; mirror production packages. Test service logic, REST endpoints, persistence, and tenant isolation. Angular specs should sit beside their subject as `*.spec.ts`. No coverage threshold is documented, but new behavior and regressions should be tested. Run each affected module's verification command before submitting.

## Commit & Pull Request Guidelines

Recent commits use short, imperative, sentence-case summaries such as `Add media-asset table` and `Improve notices messages`. Keep commits focused and avoid mixing unrelated modules. Pull requests should explain the problem and solution, list affected modules and verification commands, link relevant issues/specs, and include screenshots for UI changes. Call out database migrations, configuration changes, and compatibility impacts explicitly.

## Security & Configuration

Never commit credentials, tokens, or production `.env` files. Start from tracked examples such as `taurus-info/.env.example`. Treat keystores, OAuth/Keycloak settings, tenant configuration, and Liquibase changes as security-sensitive; document required deployment values without embedding secrets.
