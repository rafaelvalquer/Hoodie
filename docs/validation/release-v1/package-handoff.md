# Pacote de entrega — Hoodie V1

Estado em 22/10/2026: implementação entregue para homologação; aprovação de produção pendente. Este fechamento organiza código, automações, roteiros e evidências existentes. Não atesta aprovação dos testes ainda falhos nem distribuição pela Google Play.

## Conteúdo entregue

| Entrega do plano | Conteúdo disponível | Validação restante |
|---|---|---|
| RELEASE-01 — CI | Correções, diagnósticos, relatórios e gate de proveniência do mesmo SHA | CI integral verde; diferenças visuais e causas das intermitências |
| RELEASE-02 — Backup | Exportação/importação criptografada, controles e roteiros de restauração | Histórico integral, interrupções, provedores e aparelhos com chaves diferentes |
| RELEASE-03 — Distribuição | Pipeline de AAB, assinatura configurável e preservação dos artefatos | Chave de upload real, AAB assinado e instalação/atualização dos APKs derivados |
| RELEASE-04 — Compatibilidade | Verificadores nativos, controles de 16 KB e roteiros de migração | Runtime do candidato final, falhas nativas abertas e preservação dos dados |
| RELEASE-05 — Google Play | Política, contato, textos da loja e checklist de conformidade | Revisão de tráfego, declarações, materiais/licenças e preenchimento/aceite na Console |
| RELEASE-06 — Homologação | Matriz de aparelhos e roteiro de bateria, localização e Jornada | Campanha real de sete dias em fabricantes diferentes |
| RELEASE-07 — Beta | Roteiro e registro de resultados preparado | Participantes, distribuição e observação do uso real |
| RELEASE-08 — Publicação | Gates, procedimento de promoção, monitoramento e resposta a incidentes | Aprovação do candidato, identificação final e lançamento gradual |

## Ordem de execução da homologação

1. Obter CI integral aprovada no SHA candidato, sem ignorar testes ou flexibilizar comparações visuais.
2. Validar backup, migrações, estabilidade e memória de 16 KB no pacote candidato, preservando as evidências de falhas anteriores.
3. Disponibilizar conta Play Console, chave de upload e aparelhos; gerar e validar o pacote assinado e sua atualização sobre dados existentes.
4. Concluir revisão de conformidade e executar homologação física e beta.
5. Registrar as aprovações dos gates e somente então preparar a publicação.

Dependências externas atuais: conta Play Console e chave de upload ainda indisponíveis; nenhum aparelho físico disponível no momento. A assinatura de desenvolvimento usada em homologação não substitui a assinatura de upload.

## Evidências e critérios de aceite

Checagens do fechamento: 45 testes das ferramentas aprovados (12 do adaptador local, 14 do coletor comum, sete do controlador macOS e 12 do gate de release), actionlint dos dois workflows alterados e sintaxe Bash do instalador aprovados. Os testes do gate foram executados com diretório temporário dentro do workspace após restrição de acesso ao temporário padrão do sandbox. Essas verificações não executam o aplicativo nem substituem a CI Android, a captura macOS real ou a homologação física.

- [CI e resultados](ci-results.md): a última consulta à execução [39775528368](https://github.com/rafaelvalquer/Hoodie/actions/runs/39775528368), SHA `220239c0d7c5ab4069b24309b98fba9c91d3c5d9`, confirmou unitários, lint e assemble aprovados e instrumentação reprovada. A auditoria detalhada anterior está na [comparação 383/ebe](ci-383-ebe-comparison.json).
- [Controle visual](portable-renderer-review.md): observação Intel Windows reprovada antes de instalar o app; controle Intel macOS preparado para execução independente. Não há aprovação de novas referências visuais.
- [Backup](backup-device-matrix.md), [atualização R8](r8-apk-upgrade.md) e [16 KB](native-16kb-results.md): resultados parciais e limitações permanecem registrados.
- [Aparelhos](physical-homologation.md), [bateria/geofences](battery-geofence-results.md), [beta](beta-summary.md), [Play](play-compliance.md) e [materiais da loja](assets-and-store.md): roteiros para completar as validações.
- [Aprovação final](release-approval.md): gates continuam pendentes; nenhum pacote foi aceito ou distribuído pela Google Play.

Falhas encontradas na homologação podem exigir novas correções de código. O fechamento da implementação não encerra esses bloqueadores e não autoriza a publicação automática.
