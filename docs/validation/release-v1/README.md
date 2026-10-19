# Hoodie V1 — evidências e gates

Plano: [Production Readiness & Release Hardening](../../plans/hoodie-release-v1.md).

Estado inicial: `6e03a2880f4e`. Implementação em andamento; nenhuma aprovação de produção foi emitida.

| Entrega | Evidência exigida | Estado |
|---|---|---|
| RELEASE-01 | Unitários, lint, build e suíte instrumentada completa no mesmo SHA; CI verde | Em validação |
| RELEASE-02 | Matriz funcional + aparelhos A/B com Keystores diferentes + interrupções reais | Variante de homologação; testes físicos pendentes |
| RELEASE-03 | AAB assinado, certificado, APKs derivados testados, aceitação Play | Pipeline preparada; chave e conta Play ausentes |
| RELEASE-04 | ELF de todas as ABIs, ZIP do APK derivado e runtime 16384 | Em validação |
| RELEASE-05 | Política pública, suporte e declarações aceitas | Contato confirmado; Console pendente |
| RELEASE-06 | Sete dias reais em Samsung, Motorola e AOSP, comparação de bateria | Aparelhos disponíveis; modelos e campanha pendentes |
| RELEASE-07 | Beta externo e critérios aplicáveis da conta | Pendente de conta, participantes e tempo real |
| RELEASE-08 | RC aprovado, assinatura, Console, rollout e incidentes | Bloqueada pelos gates anteriores |

O usuário confirmou em 19/10/2026 que possui aparelhos físicos para homologação. Modelos, versões Android e conexão USB ainda precisam ser identificados; a campanha física não foi executada. Conta Play Console e chave de upload continuam pendentes. Não criar uma chave de distribuição fictícia nem declarar testes físicos, beta ou publicação concluídos. Contato confirmado: rafael.valquer@gmail.com.

Roteiro para começar nos aparelhos: [homologação física](physical-homologation.md).

Na consulta seguinte, o responsável informou que no momento não há aparelho disponível para conectar. Manter os testes físicos pendentes até disponibilidade efetiva; isso não altera os resultados já obtidos nos emuladores.

`homologacao` usa a mesma otimização R8 de `release`, ID separado `com.hoodie.app.homologacao`, assinatura local de desenvolvimento e backup habilitado. Não instala por cima dos dados de `com.hoodie.app`. `release` continua sem backup público até a homologação. Uma assinatura debug não é uma chave de upload para a Play.

Não basta copiar este checklist preenchido: cada aprovação precisa de SHA, versão, aparelho, data, comando/roteiro e evidência verificável. Alterações após a aprovação exigem repetir os gates afetados.
