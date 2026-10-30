# Security Policy

## Supported version
The current development line is 1.1.x.

## Reporting a vulnerability
Do not publish access tokens, passwords, signing keys, keystores, or private SDK files in issues. Report security concerns privately through GitHub's private vulnerability reporting feature if enabled for this repository.

## Security practices
- Never commit Spotify access tokens, client secrets, signing keys, or keystores. The App Remote AAR in `app/libs` is an SDK dependency, not an authentication secret; follow Spotify's license terms.
- Keep GitHub Actions permissions read-only unless a job needs more.
- Keep the default branch protected and require review before merging when collaborators are added.
- Use unique passwords and two-factor authentication on the GitHub account.
