# Vienna Pulse

Live and historical twin of the Wiener Linien network: live map, punctuality per line and time of day. Monorepo opened at this root.

## Repository Layout

- `vienna-pulse-frontend/`: Angular 22 app (zoneless, standalone, Tailwind CSS 4, Vitest, no SSR). Run Angular and pnpm commands from this folder.
- `.github/workflows/`: CI and deployment.
- `probes/`: local API experiments and raw data. Gitignored, never commit or move it into tracked folders.
- Agent setup lives at this root: `AGENTS.md`, `.agents/skills/` (with `.claude/skills/` symlinks), `.mcp.json`, `.codex/config.toml`, `skills-lock.json`.

## Project Tooling

- Tool versions (Node, Angular CLI, pnpm, Wrangler) are pinned with mise in `mise.toml` and locked in `mise.lock`. Use them as they are. Do not add Wrangler as a devDependency and do not upgrade pinned versions unless asked.
- Package manager is pnpm. Never use npm or yarn to install packages.
- The Angular MCP server runs as `ng mcp` (the mise-pinned CLI) and discovers the workspace in `vienna-pulse-frontend/`.

## Cloudflare Deployment

- The frontend is a static SPA served by Cloudflare Workers static assets at https://pulse.vladdancea.com. Config lives in `vienna-pulse-frontend/wrangler.jsonc`.
- Keep `assets.not_found_handling` set to `single-page-application`, otherwise deep links return 404.
- Pushes to `main` deploy automatically through `.github/workflows/frontend.yml`. Manual deploy: `pnpm ng build && wrangler deploy` inside `vienna-pulse-frontend/`. Do not use the beta `cf deploy`, it rewrites the config and drops the SPA setting.
- Use the `cloudflare-docs` MCP server to check current Cloudflare docs before writing Wrangler config. The `cloudflare-api` MCP server needs OAuth on first use.

## Frontend Guidelines (vienna-pulse-frontend)

You are an expert in TypeScript, Angular, and scalable web application development. You write functional, maintainable, performant, and accessible code following Angular and TypeScript best practices.

### TypeScript Best Practices

- Use strict type checking
- Prefer type inference when the type is obvious
- Avoid the `any` type. Use `unknown` when the type is uncertain

### Angular Best Practices

- Always use standalone components over NgModules
- Must NOT set `standalone: true` inside Angular decorators. It's the default in Angular v20+.
- Do NOT set `changeDetection: ChangeDetectionStrategy.OnPush` explicitly. `OnPush` is the default in Angular v22+.
- Use signals for state management
- Implement lazy loading for feature routes
- Do NOT use the `@HostBinding` and `@HostListener` decorators. Put host bindings inside the `host` object of the `@Component` or `@Directive` decorator instead
- Use `NgOptimizedImage` for all static images.
  - `NgOptimizedImage` does not work for inline base64 images.

### Accessibility Requirements

- It MUST pass all AXE checks.
- It MUST follow all WCAG AA minimums, including focus management, color contrast, and ARIA attributes.

#### Components

- Keep components small and focused on a single responsibility
- Use `input()` and `output()` functions instead of decorators
- Use `model()` for two-way bound properties with `[(prop)]` syntax instead of pairing `input()` with `output()`
- Use `computed()` for derived state
- Use `linkedSignal()` for state derived from multiple reactive sources that must stay synchronized
- Prefer inline templates for small components
- Prefer Signal Forms (`@angular/forms/signals`) for new forms. They are stable in Angular v22+ and provide signal-based state, type-safe field access, and schema-based validation
- When not using Signal Forms, prefer Reactive forms instead of Template-driven ones
- Do NOT use `ngClass`, use `class` bindings instead
- Do NOT use `ngStyle`, use `style` bindings instead
- Do NOT import `CommonModule`, import only the directives and pipes the template uses, such as `AsyncPipe` or `DatePipe`
- When using external templates/styles, use paths relative to the component TS file.

### State Management

- Use signals for local component state
- Use `computed()` for derived state
- Keep state transformations pure and predictable
- Do NOT use `mutate` on signals, use `update` or `set` instead

### Templates

- Keep templates simple and avoid complex logic
- Use native control flow (`@if`, `@for`, `@switch`) instead of `*ngIf`, `*ngFor`, `*ngSwitch`
- Use the async pipe to handle observables
- Do not assume globals like (`new Date()`) are available.

### Services

- Design services around a single responsibility
- Use the `providedIn: 'root'` option for singleton services
- Prefer the `@Service` decorator over `@Injectable({providedIn: 'root'})` for new singleton services (Angular v22+)
- Use the `inject()` function instead of constructor injection
