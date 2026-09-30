# Findings contract

Report only actionable, concrete findings supported by a reachable scenario. Prefer HIGH confidence. LOW-confidence issues must not enter root's final list without further validation. Do not duplicate findings, report style/preferences, or flag unchanged code unless the diff directly activates/exposes the defect.

Each finding must contain:

- `SEVERITY`: P0 | P1 | P2 | P3
- `TITLE`: short and specific
- `LOCATION`: precise file and line/range
- `SCENARIO`: concrete steps that reach the problem
- `CAUSE`: why the code produces the behavior
- `IMPACT`: real consequence
- `EVIDENCE`: code path, invariant, or test supporting the claim
- `MINIMAL_FIX`: smallest correction direction
- `CONFIDENCE`: HIGH | MEDIUM | LOW

Severity guide: P0 catastrophic/critical security or data loss; P1 serious security-boundary, corruption, or material availability defect; P2 actionable bug to fix before merge; P3 real lower-impact issue suitable for separate handling. No finding is not a claim that the code is bug-free.
