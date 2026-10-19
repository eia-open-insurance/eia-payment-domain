# eia-payment-domain

- GitHub organization: `eia-open-insurance`
- Repository URL: https://github.com/eia-open-insurance/eia-payment-domain
- Type: Spring Boot service
- Deployable: Yes
- Owner: Payment
- Runtime: Java 22 and Spring Boot
- Data store: eia_payment (tx schema)
- Depends on: eia-payment-expert, eia-contracts, eia-platform/libs

## Purpose

Payment business rules, transaction catalog, orchestration, approvals, and APIs. Payment authority, issuance, SAP S/4HANA posting, reconciliation, tax, and escheatment.

## Responsibilities

- Keep this repository focused on one deployable component or one platform concern.
- Follow the repository standard in `../CONVENTIONS.md` and the specification in `../../SPEC.md`.
- Publish and consume contracts through `eia-contracts`.
- Maintain README, CODEOWNERS, SECURITY, CHANGELOG, tests, and local verification.
- Contain no business logic in shared libraries or front-end remotes.

## Verification

- Lint and format
- Unit tests with 100% business-logic coverage where applicable
- Contract tests
- Integration or Playwright tests where applicable
- Security scan and SBOM
- Build and publish

## Notes

Owns the business logic for this vertical.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
