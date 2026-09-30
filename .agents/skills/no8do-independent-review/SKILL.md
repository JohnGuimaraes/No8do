---
name: no8do-independent-review
description: Use for risk-proportional pre-merge and pull request reviews, independent diff audits, and materially risky security, backend, database, or MCP changes. Classify risk before selecting zero, one, or three independent read-only reviewers.
---

# No8do Independent Review

## RISK CLASSIFICATION — always first

Inspect the target diff and classify it before creating any subagent. A PR is not, by itself, a reason for multi-agent review. Use concrete changed behavior and affected invariants; when between levels, choose the lower-cost mode unless evidence supports the higher one.

- `REVIEW_NONE`: simple text/docs, trivial rename/formatting, isolated small change without business rules or critical contract. No subagent; root performs ordinary inspection.
- `REVIEW_SINGLE`: low/medium risk such as a small visual/UI interaction, local refactor, small logic change without a critical boundary, or simple API change without sensitive persistence. At most one reviewer, using the closest existing lens.
- `REVIEW_TRIPLE`: only when the diff materially changes authentication, authorization, security, credentials/secrets, workspace isolation/multitenancy, schema/migrations/constraints, transactions, concurrency/locking, critical idempotency/lifecycle/persistence, MCP/protocol, capability/policy, or trust boundaries. Run exactly three independent reviewers in parallel: Security, Data/Transactions, Regression/Tests.
- `REVIEW_TRIPLE_REREVIEW`: reserve for genuinely critical changes (central authentication, cross-tenant authorization, destructive data/schema changes, critical credential lifecycle/protocol changes, P0/P1 findings, or multiple P2s in a critical area). After fixes, review only the correction delta. For ordinary P2 fixes, use only reviewer(s) who found them; use three only if severity or newly crossed boundaries justify it.

Record `REVIEW_MODE` and one-sentence evidence-based rationale before dispatch. If collaboration/subagents are unavailable, report `MULTI_AGENT_UNAVAILABLE`; never simulate independent review as root-only work.

## Independent review workflow

Implementer must differ from reviewer. Reviewers start from: “The code may be wrong despite green tests.” Give each reviewer only base ref, head ref, target diff, role instructions, and `references/findings-contract.md`. Do not provide implementation history, author justifications, prior audit results, test/CodeQL results, or desired conclusion. Use a fresh/minimal context when supported.

Reviewers are strictly read-only: no edits, patches, staging, commits, pushes, PR changes, merges, database changes, or migrations. Root does not fix findings during review.

## Token budget

Every reviewer must:

1. Start with the target diff; do not read the whole repository.
2. Open complete files only when diff context requires it; inspect only directly relevant callers/callees.
3. Avoid repeating extensive known documentation, unrelated architecture explanations, and another reviewer's work.
4. Never run the full test suite or a general build. Use a focused test only when root needs to validate a concrete finding.
5. Keep the response concise; if no issue, reply exactly `NO_ACTIONABLE_FINDINGS`.

## Reviewer dispatch

- Security role: use `references/security-reviewer.md`.
- Data/transaction role: use `references/data-transaction-reviewer.md`.
- Regression/test role: use `references/regression-test-reviewer.md`.
- Single mode: dispatch only the most relevant one of these profiles; do not spawn all three.
- Triple mode: dispatch exactly the three roles above concurrently, each in an independent context, reviewing the same base-to-head diff through its own lens.

Review only changed code, except when changed code directly activates or exposes an inherited defect. Do not repeat findings from a prior PR without concrete evidence of impact in this diff. Do not emit speculative hardening, style, naming, or optional refactor comments.

## Root synthesis

Root gathers and deduplicates findings, checks locations and scenarios against source, validates whether each path is reachable, resolves contradictions, and rejects false positives. Open more code or run a focused test only when needed to validate a concrete claim. Do not accept findings solely because a reviewer produced them. Report `NO_ACTIONABLE_FINDINGS_FOUND` when none survive; never claim “bug-free”.

Map P0/P1 to BLOCKER, P2 to REQUIRED, and P3 to TECH-DEBT or OPTIONAL according to impact. For re-review, pass only the fix base and new head plus the minimal relevant role/contract; focus on the correction delta, not the full PR.

## Skill references

- `references/security-reviewer.md`
- `references/data-transaction-reviewer.md`
- `references/regression-test-reviewer.md`
- `references/findings-contract.md`
