# RELEASE-08 — aprovação, promoção e incidentes

Estado: **não aprovado**. Não há pacote aceito ou distribuído pela Google Play.

Após os gates A–E do plano, criar `release/1.0.0`, congelar funcionalidades e permitir apenas correções críticas, segurança, conformidade, testes e publicação. Identificar `1.0.0-rc1` com versionCode novo. Não reutilizar códigos enviados a qualquer faixa; consultar a Console antes de cada despacho. Desenvolvimento conserva `-dev`; estável `1.0.0`; hotfix `1.0.1` etc.

RC e estável usam o mesmo código validado, alterando apenas identificação necessária. Repetir testes relevantes se o binário/código mudar. Guardar SHA, versão, AAB SHA-256, certificado de upload, certificado Play App Signing, mapping R8 e evidências dos APKs derivados.

Antes de aprovar: CI no SHA, AAB assinado, 16 KB estático/runtime, SQLCipher/migrações, backup A/B e interrupções, manifesto/permissões, política pública, Segurança dos Dados, aparelhos, beta, assets/licenças, contato e nenhum P0/P1 aberto.

Bloqueadores técnicos atuais: na última suíte integral auditada, `67bd2d7b`, os 42 direcionados e todos os 210 casos fora da matriz visual passaram. A suíte completa executou 390 casos: 376 aprovados, 14 comparações visuais falhas e zero ignorados. As duas regressões da cidade passaram; as novas imagens do cabeçalho ainda precisam de revisão. [Contagem e mensagens](ci-67-results.json). A CI dos commits posteriores continua necessária no SHA candidato.

[HVR-NATIVE-API35-001](api35-native-crash.json), observado durante exportação/acessibilidade, e [HVR-NATIVE-API35-002](api35-native-frame-crash.json), observado na apresentação da Home, continuam abertos: causa comum e correção não foram provadas. O roteiro mínimo de backup do APK R8 de `a11cb02a` passou em API36/16384, com restauração, reabertura, exportação e autenticação independente; API35 reprovou no mesmo workflow. A comparação estrita do fixture também permanece falha. [Evidências e limites](r8-a11-api36-backup-results.json). Essa passagem parcial não aprova migração, histórico integral, aparelhos físicos ou assinatura de upload.

| Gate | Aprovação/evidência |
|---|---|
| A — Qualidade | Pendente |
| B — Dados e segurança | Pendente |
| C — Distribuição | Pendente |
| D — Google Play | Pendente |
| E — Uso real | Pendente |
| F — Publicação | Pendente |

Publicação depende de aprovação humana explícita. A pipeline não envia automaticamente à Play. Após aprovação da Console, avaliar 5% → 20% → 50% → 100% quando a faixa permitir; na primeira publicação os controles disponíveis podem diferir. Só ampliar com amostra suficiente e métricas estáveis; registrar decisão em cada etapa.

Incidente (crash, SQLCipher, restauração, permissões, bateria, Jornada, rejeição): interromper ampliação, avaliar suspensão, registrar alcance/severidade, preservar evidências sem dados pessoais, corrigir, gerar hotfix com novo versionCode e validar atualização sobre instalação existente. Suspender distribuição não rebaixa apps já atualizados. Não resolver corrupção apagando dados automaticamente.

Suporte: rafael.valquer@gmail.com. Acompanhar Android vitals, instalação/abertura, ANRs/crashes, bateria, backup, permissões, perda/duplicações e resposta do suporte após lançamento.
