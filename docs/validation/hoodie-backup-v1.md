# HOODIE-BACKUP-V1 — execução por etapas

Referência: Room v12, 29 entidades. Flag: `HOODIE_LOCAL_ENCRYPTED_BACKUP_V1 = false` até concluir os gates de liberação.

| Etapa do plano | Implementação |
|---|---|
| 1. Inventário | Matriz das 29 entidades em [cobertura](hoodie-backup-v1-coverage.md), allowlist de preferências e registro dos dois campos dependentes de chave local. Testes quebram se o schema atual ou as preferências ganharem campos sem decisão. |
| 2. Contrato | Modelos V1 e schema lógico congelado, manifesto protegido e arquivo `.hoodiebackup`. [Especificação binária](../backup/FORMAT-V1.md). |
| 3. Criptografia | JCA PBKDF2-HMAC-SHA256 (600.000 iterações), AES-256-GCM por registro, ordem/cabeçalho autenticados e encerramento obrigatório. Teste contra vetor independente hashlib e fixture V1. |
| 4. Room | Cursor progressivo dentro de uma transação consistente, sem copiar o arquivo físico do banco. |
| 5. DataStore | Preferências portáteis selecionadas explicitamente. Sem senhas, chaves, permissões concedidas ou datas operacionais do aparelho anterior. |
| 6. Coordenadas | Transformação em memória, falha explícita se um lugar não decifrar e nova cifragem com o alias de destino. |
| 7. Exportação | Senha/confirmar/mostrar, opção digital explícita, CreateDocument, processamento IO, progresso, cancelamento, verificação por releitura e data local somente após sucesso. Journal permite tentar remover documentos interrompidos no próximo início. |
| 8. Pré-validação | Autenticação integral, limites, validação de versões/tipos/intervalos/enumerações, contagens e prévia. Não ativa a instalação atual. |
| 9. Preparação | Banco Room/SQLCipher isolado, senha e alias de coordenadas novos, IDs preservados, chaves únicas e verificação de integridade/relações. |
| 10. Ativação | Gerações de banco+chaves+DataStore, journal durável, ponteiro único, fechamento do Room anterior, rollback e reinício. Recuperação antes da injeção de Hilt/DatabaseGate/DataStore. |
| 11. Reconciliação | Corte histórico na data do snapshot, nenhuma sessão móvel ativa, nenhum estado provisório/pergunta/desfazer/fila antiga. Workers e geofences reconstruídos conforme permissões atuais. |
| 12. Entradas | Configurações, primeiro acesso progressivo e tela de banco inacessível, todos sob a flag. O grafo de importação usa o banco ativo de forma Lazy, permitindo recuperação sem abri-lo. |
| 13. Testes | Unitários de cripto/contrato/cobertura/configurações; instrumentados com SQLCipher e aliases independentes; recuperação em cada fase do journal; ativador real com falha após fechar o Room antigo; componentes de senha, confirmação, opção digital e recuperação. |
| 14. Documentação/homologação | [Guia de uso](../backup/USUARIO.md), [privacidade](../privacy-policy.md), contrato e evidências abaixo. Liberação permanece condicionada aos gates de produção. |

## Evidências locais

O teste principal usa quatro lugares (Casa, Trabalho, Academia e Restaurante), 120 contextos em 30 dias, visitas repetidas, timeline, memórias, rotina, correções, deslocamentos e histórico digital. Exporta/restaura com e sem histórico digital em gerações com senhas SQLCipher e aliases Keystore independentes.

As rodadas finais cobrem todas as oito tabelas digitais, aprendizados, versões de rotina, leitura do fixture inicial, ativação e rollback reais, falhas do journal/ponteiro, recuperação bloqueada sem criação de chaves e confirmação de interface.

Regressão final local em 18/10/2026: 298 suítes, 1.302 testes, nenhuma falha ou erro e 6 ignorados. Os 32 testes do pacote backup estão incluídos. A validação usa JDK 21 no host Windows; o CI do repositório utiliza JDK 17. [Resumo da execução](hoodie-backup-v1-unit-results.json).

Android: 16 testes aprovados em 101,186 segundos no emulador API 37, incluindo interface, importação sem acessar o banco/chave antigo, restauração depois de destruir as chaves de origem, ativação do banco e preferências e rollback. Os ambientes isolados usam senhas SQLCipher e aliases Keystore diferentes. O vetor PBKDF2 independente também passou no Android; 600.000 iterações tiveram mediana de 4.063 ms durante a validação. Nenhuma senha de teste ou coordenada em texto puro foi encontrada no Logcat. O APK manteve `android:allowBackup=false`, verificado com aapt. [Resumo Android](hoodie-backup-v1-android-results.json).

Build consolidado: `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug` e `assembleRelease` aprovados em 15m03s. Lint: 0 erros, 67 avisos e 1 sugestão. O APK release foi otimizado com R8 e gerado sem assinatura de produção (`app-release-unsigned.apk`); não foi publicado como versão de distribuição.

## Contratos de recuperação

- Antes de COMPLETED, uma reinicialização escolhe a geração anterior integralmente; depois, escolhe a nova integralmente.
- Preferências e banco nunca são publicados individualmente. A geração nova só é apontada após ambos estarem prontos.
- Uma falha de ativação apresenta a rota Reiniciar e recuperar. A navegação comum fica bloqueada até reiniciar.
- Preparações abandonadas são descartadas. A geração anterior confirmada permanece como ponto de recuperação e também é removida por Apagar todos os dados.
- Cancelamento antes da ativação não altera o banco ativo. Após a confirmação começar a ativação, o protocolo termina sem cancelamento intermediário.
- Activity recriada conserva a operação no ViewModel. Senhas não vão para estado salvo nem para disco; após morte do processo a pessoa inicia novamente. Gerações ainda não ativadas são descartadas no startup.

## Gates antes de habilitar em produção

1. CI completa aprovada, incluindo a suíte instrumentada.
2. Revisão manual do seletor de documentos com provedores locais e de nuvem, TalkBack, Activity recriada e fechamento forçado do processo em cada fase.
3. Aprovação da política de privacidade e do guia de perda de senha/arquivo.
4. Assinatura de produção configurada e verificada. O build release local não comprova uma assinatura de distribuição.
5. Manter o fixture da primeira versão como teste permanente de compatibilidade.

A implementação não solicita novas permissões Android, não acessa servidores do Hoodie e mantém `allowBackup=false`.
