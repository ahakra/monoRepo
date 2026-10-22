# Codex Instructions — Camel ETL Monorepo

Read `docs/PROJECT_CONTEXT.md` before proposing architecture or modifying code.

## Objective
Build a production-oriented integration/ETL platform, incrementally, using Spring Boot + Apache Camel, with React as the eventual visual designer and a separate Rust AI assistant later.

## Working principles
- Work with the existing code and directory structure; inspect files before making changes.
- Make small, verifiable changes and explain why each change is needed.
- Prefer Java DSL `RouteBuilder` beans discovered by Spring Boot; do not create an extra `CamelContext`.
- Let Camel manage ETL execution and Spring Boot manage API, authentication, configuration, and workflow metadata.
- Use Spring Data/JPA for internal platform persistence; use Camel SQL/JDBC for external/dynamic ETL access, without requiring JPA entities for every external source.
- Use Camel DSL (`choice`, `split`, `filter`, `to`, error handling) for orchestration and Spring beans for reusable business operations. Simple expressions are suitable for simple conditions and mapping.
- Keep user-configured connectors and SQL constrained: validate definitions, use least-privilege credentials, parameterize values, allowlist permitted operations and identifiers, and never run arbitrary untrusted code or methods.
- Add tests for routing, transformation and failure paths; pay attention to retry safety, idempotency and observability.
- Don't introduce React, Rust, agents, Kubernetes, Kafka, or more infrastructure until it serves the next agreed milestone.
- Never commit `.env` or secrets; provide `.env.example` instead.
- Keep Camel dependency versions aligned with the existing BOM and Spring Boot compatibility.

## Workflow with the developer
1. Inspect the repo and state what already exists.
2. Suggest the smallest useful next implementation step.
3. Implement with tests, when requested.
4. Report changed files, commands to run, and any unverified assumptions.
