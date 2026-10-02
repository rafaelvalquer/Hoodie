# Proteção da `main` (gate de CI)

O workflow `.github/workflows/android-ci.yml` roda em três estágios:

```
QUALITY                      BUILD          ANDROID INTEGRATION
unit-tests ─┐
            ├──▶ assemble ──▶ instrumentation
lint ───────┘
```

| check | o que bloqueia |
|---|---|
| `unit-tests` | qualquer teste JVM vermelho (inclui `ShippedSheetsValidationTest`, `VersionConsistencyTest`, segurança do banco) e `TODO`/`FIXME`/`NotImplementedError` no código principal |
| `lint` | erros de lint |
| `assemble` | APK não compila ou sprites finais (`hoodie_walk/idle/sleep/work`) ausentes do APK |
| `instrumentation` | testes no emulador (banco cifrado real, migrações, Keystore) |

## Ativar a proteção (feito pelo dono do repositório)

A proteção é uma configuração do GitHub, não um arquivo do repositório. Pela interface:
**Settings › Branches › Add branch ruleset** (ou *Branch protection rule*) para `main`:

- ✅ Require a pull request before merging
- ✅ Require status checks to pass before merging → adicionar `unit-tests`, `lint`, `assemble`, `instrumentation`
- ✅ Require branches to be up to date before merging
- ✅ Do not allow bypassing the above settings

Ou pelo GitHub CLI (precisa de permissão de admin no repositório):

```bash
gh api -X PUT repos/rafaelvalquer/Hoodie/branches/main/protection \
  -H "Accept: application/vnd.github+json" \
  -F required_status_checks[strict]=true \
  -F "required_status_checks[contexts][]=unit-tests" \
  -F "required_status_checks[contexts][]=lint" \
  -F "required_status_checks[contexts][]=assemble" \
  -F "required_status_checks[contexts][]=instrumentation" \
  -F enforce_admins=true \
  -F required_pull_request_reviews[required_approving_review_count]=0 \
  -F restrictions=
```

Os checks só aparecem na lista depois de o workflow rodar pelo menos uma vez num PR.
