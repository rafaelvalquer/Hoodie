# Hoodie V1 — evidências e gates

Plano: [Production Readiness & Release Hardening](../../plans/hoodie-release-v1.md).

Estado inicial: `6e03a2880f4e`. Implementação em andamento; nenhuma aprovação de produção foi emitida.

Em 20/10/2026, a main recebeu merges de família/NPCs, corações de carinho e cenas de rua/carro. A árvore de fontes mudou; os resultados R8 anteriores continuam vinculados aos hashes de seus próprios binários. [Escopo observado](candidate-source-change.json). O conteúdo atual precisa de nova CI completa, R8/runtime e revisão das referências visuais impactadas antes do congelamento do candidato. Nenhuma aprovação anterior foi transferida automaticamente para essa árvore.

Depois, a main recebeu a consolidação de visitas e a Jornada como cidade pixel art. Essas integrações foram preservadas. A árvore do commit `6288349b` difere da árvore `a1395fad` dos APKs R8 anteriores; a correção posterior da apresentação de interações também exige validação dos novos artefatos. [Regressão de identidade do frame](presentation-race-regression.json).

| Entrega | Evidência exigida | Estado |
|---|---|---|
| RELEASE-01 | Unitários, lint, build e suíte instrumentada completa no mesmo SHA; CI verde | Em validação |
| RELEASE-02 | Matriz funcional + aparelhos A/B com Keystores diferentes + interrupções reais | Variante de homologação; testes físicos pendentes |
| RELEASE-03 | AAB assinado, certificado, APKs derivados testados, aceitação Play | Pipeline preparada; chave e conta Play ausentes |
| RELEASE-04 | ELF de todas as ABIs, ZIP do APK derivado e runtime 16384 | Em validação |
| RELEASE-05 | Política pública, suporte e declarações aceitas | Contato confirmado; Console pendente |
| RELEASE-06 | Sete dias reais em Samsung, Motorola e AOSP, comparação de bateria | Sem aparelhos disponíveis no momento; campanha pendente |
| RELEASE-07 | Beta externo e critérios aplicáveis da conta | Pendente de conta, participantes e tempo real |
| RELEASE-08 | RC aprovado, assinatura, Console, rollout e incidentes | Bloqueada pelos gates anteriores |

Na atualização mais recente de 19/10/2026, o responsável informou que não há aparelho físico disponível para conectar. A campanha física não foi executada; modelos, versões Android e conexão USB dependem da disponibilidade efetiva. Conta Play Console e chave de upload continuam pendentes. Não criar uma chave de distribuição fictícia nem declarar testes físicos, beta ou publicação concluídos. Contato confirmado: rafael.valquer@gmail.com.

Roteiro para começar nos aparelhos: [homologação física](physical-homologation.md).

Os resultados de emuladores permanecem separados da homologação física exigida pelo plano.

`homologacao` usa a mesma otimização R8 de `release`, ID separado `com.hoodie.app.homologacao`, assinatura local de desenvolvimento e backup habilitado. Não instala por cima dos dados de `com.hoodie.app`. `release` continua sem backup público até a homologação. Uma assinatura debug não é uma chave de upload para a Play.

Não basta copiar este checklist preenchido: cada aprovação precisa de SHA, versão, aparelho, data, comando/roteiro e evidência verificável. Alterações após a aprovação exigem repetir os gates afetados.
