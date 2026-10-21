# Hoodie V1 — evidências e gates

Plano: [Production Readiness & Release Hardening](../../plans/hoodie-release-v1.md).

Estado inicial: `6e03a2880f4e`. Implementação em andamento; nenhuma aprovação de produção foi emitida.

Em 20/10/2026, a main recebeu merges de família/NPCs, corações de carinho e cenas de rua/carro. A árvore de fontes mudou; os resultados R8 anteriores continuam vinculados aos hashes de seus próprios binários. [Escopo observado](candidate-source-change.json). O conteúdo atual precisa de nova CI completa, R8/runtime e revisão das referências visuais impactadas antes do congelamento do candidato. Nenhuma aprovação anterior foi transferida automaticamente para essa árvore.

Depois, a main recebeu a consolidação de visitas e a Jornada como cidade pixel art. Essas integrações foram preservadas. A árvore do commit `6288349b` difere da árvore `a1395fad` dos APKs R8 anteriores; a correção posterior da apresentação de interações também exige validação dos novos artefatos. [Regressão de identidade do frame](presentation-race-regression.json).

Última suíte integral auditada: `75da3655`, com 42 direcionados aprovados e 390 casos completos — 384 aprovados, seis comparações visuais falhas, nenhum ignorado. Todos os 210 testes fora da matriz visual e as duas regressões da cidade passaram. [JUnit e identidades dos APKs](ci-75-results.json). As 18 referências da cidade foram atualizadas após revisão das 42 capturas do Diário; a comparação continua exata e as diferenças de um nível de cor entre hosts permanecem abertas. [Revisão visual](city-reference-review.json), [controle de renderer](swiftshader-mode-control.json). O experimento ANGLE e os commits posteriores precisam de seus próprios resultados; não transferir a eles a aprovação parcial deste run.

O APK R8 derivado do AAB de `f41b99b4` concluiu restauração/reabertura/exportação do fixture mínimo em API35/16384, com autenticação independente das nove unidades. Depois, os 13 testes de banco debug passaram, incluindo a migração cifrada v11→12 e reabertura; não constituem teste de migração no APK R8. API36 falhou na splash inicial, e a comparação estrita da representação numérica do fixture continua falha. [Runtime atual e limites](r8-f41-api35-results.json), [falha API36 preservada](r8-f41-api36-splash.json). A validação estática inclui proteção RELRO, além dos checks de LOAD e ZIP: [14 testes e bibliotecas verificadas](native-relro-validation.json). Esses resultados não aprovam histórico completo, atualização entre APKs, dois aparelhos físicos ou distribuição assinada pela chave de upload.

O controle ANGLE confirmou o renderer nos dois hosts, mas só a captura AMD concluiu os 180 casos/222 imagens. A Intel falhou em PixelCopy e o coletor anterior não preservou PNGs parciais nem health.json. A falha e a perda de evidência foram registradas; o coletor foi corrigido e passou em 14 testes, ainda sem validação Android dessa correção. [Captura concluída](angle-first-capture.json), [falha preservada](angle-intel-failure.json). Estabilidade do backend e aprovação visual permanecem abertas.

| Entrega | Evidência exigida | Estado |
|---|---|---|
| RELEASE-01 | Unitários, lint, build e suíte instrumentada completa no mesmo SHA; CI verde | Seis falhas visuais no último run auditado; gate aberto |
| RELEASE-02 | Matriz funcional + aparelhos A/B com Keystores diferentes + interrupções reais | Backup mínimo R8/16KB observado; histórico completo e matriz física pendentes |
| RELEASE-03 | AAB assinado, certificado, APKs derivados testados, aceitação Play | Pipeline preparada; chave e conta Play ausentes |
| RELEASE-04 | ELF de todas as ABIs, ZIP do APK derivado e runtime 16384 | Estática e runtime mínimo API36 observados; falhas API35 e gate completo abertos |
| RELEASE-05 | Política pública, suporte e declarações aceitas | Contato confirmado; Console pendente |
| RELEASE-06 | Sete dias reais em Samsung, Motorola e AOSP, comparação de bateria | Sem aparelhos disponíveis no momento; campanha pendente |
| RELEASE-07 | Beta externo e critérios aplicáveis da conta | Pendente de conta, participantes e tempo real |
| RELEASE-08 | RC aprovado, assinatura, Console, rollout e incidentes | Bloqueada pelos gates anteriores |

Na atualização mais recente de 19/10/2026, o responsável informou que não há aparelho físico disponível para conectar. A campanha física não foi executada; modelos, versões Android e conexão USB dependem da disponibilidade efetiva. Conta Play Console e chave de upload continuam pendentes. Não criar uma chave de distribuição fictícia nem declarar testes físicos, beta ou publicação concluídos. Contato confirmado: rafael.valquer@gmail.com.

Roteiro para começar nos aparelhos: [homologação física](physical-homologation.md).

Os resultados de emuladores permanecem separados da homologação física exigida pelo plano.

`homologacao` usa a mesma otimização R8 de `release`, ID separado `com.hoodie.app.homologacao`, assinatura local de desenvolvimento e backup habilitado. Não instala por cima dos dados de `com.hoodie.app`. `release` continua sem backup público até a homologação. Uma assinatura debug não é uma chave de upload para a Play.

Não basta copiar este checklist preenchido: cada aprovação precisa de SHA, versão, aparelho, data, comando/roteiro e evidência verificável. Alterações após a aprovação exigem repetir os gates afetados.
