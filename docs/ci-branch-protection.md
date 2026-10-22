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
| `assemble` | APK do app ou de instrumentação não compila, ou sprites finais (`hoodie_walk/idle/sleep/work`) ausentes do APK |
| `instrumentation` | testes no emulador (banco cifrado real, migrações, Keystore) |

A fila por branch fica no job `instrumentation`, com `cancel-in-progress: false`: uma suíte em execução preserva sua evidência. Jobs de qualidade/build de commits mais novos podem executar enquanto ela termina. O GitHub pode substituir um job de instrumentação ainda pendente pelo candidato mais recente; isso não o transforma em aprovação. A pipeline de release continua exigindo Android CI concluída com sucesso no SHA exato do candidato. O job Android compila e identifica seus próprios APKs antes de iniciar o AVD e executa os testes direcionados e a suíte completa; os APKs adicionais compilados no estágio `assemble` antecipam erros de compilação, sem transferir resultados entre jobs ou commits.

## Ativar a proteção (feito pelo dono do repositório)

O workflow de release também confere os quatro jobs individualmente com `verify-release-ci.py`. Seleciona a execução Android CI mais recente do SHA, originada por push ou despacho manual no próprio repositório; uma execução mais nova pendente ou reprovada impede usar uma aprovação antiga. Exige `completed/success` para `unit-tests`, `lint`, `assemble` e `instrumentation`, com SHA, run ID e tentativa idênticos. Jobs ausentes, duplicados, ignorados ou misturados entre tentativas bloqueiam a assinatura. A coleta pagina runs/jobs e relê a execução após coletar os jobs. O artefato `release-provenance-<SHA>` preserva as respostas da API e, quando aprovado, `quality.json` com os IDs dos quatro jobs. Isso não aprova assinatura, runtime, aparelhos, beta ou publicação. Doze testes herméticos cobrem os casos de aprovação/rejeição e o comando real; não substituem uma execução da pipeline com chave de upload.

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
