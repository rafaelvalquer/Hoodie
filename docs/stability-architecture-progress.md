# Hoodie Stability & Architecture — execução

Plano original: [stability-architecture-plan.md](stability-architecture-plan.md).
Trabalho diretamente em `main`. O objetivo só termina após implementar e verificar todas as fases.

| Fase | Entrega | Estado e evidência |
| --- | --- | --- |
| 1 | Onboarding E2E real | Três cenários aprovados no Pixel 8 / Android 17, inclusive após a refatoração: UI desde Welcome, Casa cifrada, contexto HOME, reação do Hoodie e avanço; posição ausente recuperável; falha SQLCipher de escrita seguida de retry. Keystore/Room/SQLCipher/engines reais e DataStore isolado. Relógio Compose explícito evita aguardar o sprite contínuo. |
| 2 | Edição segura | Implementado; `editingId` ausente no banco falha explicitamente; repository não silencia update de local inexistente. Testes de regressão em validação. |
| 3 | PlaceLoadState | Implementado; Loading/Ready/NotFound/Error, retry e retorno. Validação de UI pendente. |
| 4 | Configurações restritas | Sheet e intent de informações do app implementados. Validação no device pendente. |
| 5 | Estado de permissão/UX | `UsagePermissionUiState` ligado à tela: DEBUG DENIED explica restrições, release oferece ajuda opcional. Verificação release pendente. |
| 6 | Opt-in digital | Default false; pedido persistido; ativação após permissão; Settings e Digital usam eventos para abrir a permissão depois da gravação. Verificação de consentimento/reinício pendente. |
| 7 | Room v5 | Implementado; Migration4To5 registrada, schema 5 exportado. Migrações JVM até v5 e SQLCipher Android aprovados. |
| 8 | daily_screen_hourly | Implementado; 24 linhas por dia, restauradas pelo mapper. |
| 9 | daily_phone_timeline | Implementado; blocos derivados persistidos com índices date/start. |
| 10 | daily_context_usage | Implementado; totais e sessões independentes do ranking. |
| 11 | replaceDay transacional completo | Implementado; replace/clear incluem as três tabelas novas. |
| 12 | Mappers históricos completos | Implementado; hourly/timeline/totais restaurados, categoria manual reaplicada. Dias v4 mantêm fallback explícito. |
| 13 | Persistência sem truncar apps | Implementado; removido TOP_APPS_STORED, ranking UI permanece limitado. |
| 14 | Split por interseção de contexto | Implementado; lacunas preservadas, contexto mais recente vence sobreposição, sem duplicar duração. |
| 15 | Testes históricos/migração/split/reopen | Aprovados Migration4To5Test, HistoricalPhoneInsightsTest, AppSessionContextSplitterTest; 40 apps, reopen, replace/rollback, migração e recortes. SQLCipher v5 no Android aprovado (40 apps, 24 horas, timeline e totais após reopen). |
| 16 | Geofence uma vez/dia | Implementado; epochDay persistido apenas após sucesso. Testes aprovados: hora 4, reinício, recuperação de falha e DataStore persistido. |
| 17 | Cancelamento PhoneInsightsWorker | Implementado via runWorkerTask; CancellationException propagada, falha logada e retry. |
| 18 | WorkerResult comum | Implementado e usado em Reconcile, Check e PhoneInsights; testes de sucesso, falha e cancelamento aprovados. |
| 19 | ContextEngine fachada/handlers | Implementado ContextSignalProcessor, GeofenceContextHandler, ContextQuestionHandler, RoutineFallbackHandler, ManualContextHandler e PlaceLearningHandler. ContextTransitionService permanece a única escrita de transições. 13 testes de integração e 9 de regras aprovados. |
| 20 | Mutex em um único nível | Implementado: mutex apenas na fachada; handlers não adquirem locks. Checagem de deslocamento também serializada, evitando sobrescrever contexto após leitura de posição concorrente. Teste aprovado de exclusão entre aprendizado e contexto manual, com registro externo suspenso e timeout para deadlock. |
| 21 | Definições de animação modulares | Implementadas nove famílias em definitions e AnimationRegistry; HoodieClips mantém o contrato público. Referência SHA-256 capturada dos 84 clips compilados antes da divisão: comparação aprovada de frames, poses, durações, eventos, loop, direção e política. 12 testes de comportamento aprovados. |
| 22 | State machine separada | Extraídos TransitionPlanner/AnimationSequence, LoopSelector, InterruptResolver, ReactionResolver e AnimationPlayer. Rodada final: 93 testes pixel aprovados, incluindo relógio/cursor do clip, preservação de ciclo ao mudar direção, reset ao reiniciar, fila de reações, provider e roteiros. Três E2E HOME aprovados após a refatoração. |
| 23 | Painter modular | Extraídos HoodiePoseRenderer, Body/Head/Arms/Legs/Tail/AccessoryPainter, primitivas e geometria compartilhadas. Fachada preserva paleta, IDs das partes, cache e APIs. Comparação aprovada da referência capturada antes da divisão para 1.017 frames dos 84 clips em frente/costas/lado, incluindo pixels, âncoras e mapas de partes. 92 testes pixel aprovados. |
| 24 | UiState + UiEvent | Aplicado nas cinco telas: canais buffered; coleta com lifecycle STARTED; Home reações/falhas, Onboarding configurações e estado de erro, Diary detalhes com data de origem, Settings mensagens/falhas/permissão, PhoneInsights permissão/falhas. Ações de Settings/PhoneInsights em nova validação. |
| 25 | Erros tipados | AppError e quatro famílias sem textos de UI. PlaceException carrega PlaceError; Home/Onboarding/Diary/Settings/PhoneInsights usam erros tipados e apresentação localizada. Cinco testes UiAction aprovados. PlacePicker ainda contém mensagens de erro textuais; auditoria de outras camadas pendente. |
| 26 | strings.xml | Migrados 154 textos estáticos das seis telas; mensagens de Settings e onboarding localizadas. Textos com interpolação, condições e helpers ainda pendentes. Nova compilação/Android em execução. |
| 27 | Acessibilidade | PixelButton/PixelPanel/ChipRow/MapOverlayButton: alvo mínimo 48 dp e papéis; PlaceTypeCell selecionável como RadioButton; fechar Pixel Lab com descrição e 48 dp. Criados três testes Android dos componentes. Auditoria completa e execução pendentes. |
| 28 | Screenshot/golden matrix | Pendente; testes existentes do PlacePicker ajustados para load Ready explícito. |
| 29 | Bottom navigation secundária | Implementado; somente rotas dos cinco tabs mostram a barra. Verificação UI pendente. |
| 30 | Resultados do mapa | Implementado; máximo 140 dp; scroll/zoom dismissResults. Verificação UI pendente. |
| 31 | Snackbar/retry geofence | Pendente |
| 32 | Retenção de sessões 365 dias | Implementado no ReconcileWorker; DAO exclui epochDay anterior ao limite, agregados preservados. Teste Room aprovado. |
| 33 | Índices/migração Room | Parcial: índice composto endedAt/start em context_events e índices date/start na timeline digital. Outros índices em auditoria. |
| 34 | Documentação | Plano preservado; docs funcionais pendentes. |
| 35 | Versionamento | Pendente; manter dev durante implementação. |
| 36 | Revisão de arte | Pendente; não atribuir revisão a Rafael sem revisão real dele. |
| 37 | Fonte pixel OFL | Pendente |

## Validação em andamento

Testes direcionados: `PlacePickerViewModelTest`, `PhoneDiaryIntegrationTest`.
Nova rodada: lógica digital, migrações, histórico/reopen/rollback, WorkerResult e geofence policy. A primeira tentativa passou 57 testes de lógica; seis classes Room não iniciaram porque Robolectric não podia gravar lock no perfil Windows. Segunda execução usa runtime local em modo offline, via init script temporário em app/build.
Resultado final JVM em 02/10/2026: **75 testes, zero falhas**. Corrigido o retorno Unit de um teste JUnit novo. Migrações v1/v2/v3/v4 até v5 e gravação/releitura completa verificadas. Teste Android SQLCipher v5 em execução.
Resultado Android: **3 testes SQLCipherRuntimeTest aprovados** no Pixel 8 / Android 17. Inclui biblioteca JNI, lugar cifrado e histórico digital v5 após fechar e reabrir o arquivo cifrado.

Nova rodada: **24 testes JVM aprovados** (ContextEngineRulesTest 9, ContextEngineIntegrationTest 13, HistoricalPhoneInsightsTest 2). Inclui a divisão dos handlers, exclusão mútua e replaceDay com epochDay derivado.

Rodada após modularizar animações: **26 testes JVM aprovados** (ContextEngineIntegrationTest 13, AnimationBehaviorTest 12, AnimationRegistryTest 1), mais **3 E2E HOME aprovados** no Pixel 8 / Android 17. Build debug/AndroidTest aprovado. Todos os 84 clips preservaram a referência anterior. Corrigida a visibilidade do helper de celular compartilhado com o idle.

Rodada após separar os resolvers e painters: **92 testes pixel em 19 classes, zero falhas**. Inclui cinco cenários diretos dos resolvers e comparação de pixels/âncoras/mapas de partes de 1.017 frames com referência do pintor original. AnimationPlayer adicionado em seguida; sua rodada final e E2E estão em andamento.

Resultado final das fases 22–23: **93 testes pixel em 19 classes, zero falhas**, e **três E2E HOME aprovados** com a máquina/player/painters divididos. Build debug/AndroidTest aprovado. Nova rodada em execução: UiActionTest, PlacePickerViewModelTest e E2E após eventos/erros da Home.

A primeira rodada da Home falhou no cache incremental do KSP (`FileAlreadyExistsException` ao copiar `HoodieApp_MembersInjector.java`), antes de compilar/testar as mudanças. Diretórios gerados e cache debug do KSP limpos dentro de app/build; repetição com `-Pksp.incremental=false` em execução. As alterações de Home/erros ainda não estão verificadas.

Inspecionados visualmente os previews gerados `hoodie_model_sheet.png` e `hoodie_walk_cycles.png`: contornos, detalhes do moletom, vistas, patas, mochila e silhuetas coerentes após a divisão. Essa inspeção do agente não altera metadados de revisão artística por Rafael (fase 36).

## Próxima sequência

Prioridade: concluir UiState + UiEvent e migração de erros/textos (24–26), depois acessibilidade/matriz visual (27–28), snackbar geofence (31), revisão de índices/documentação/versionamento/arte/fonte (33–37), além das verificações pendentes de UI/consentimento. replaceDay deriva epochDay da data quando omitido e sempre substitui sessões; não aceita null. O objetivo integral permanece ativo.
O primeiro build ocorreu enquanto novos arquivos e recursos de permissão eram adicionados e não os incluiu; foi reiniciado com as alterações completas.
Não considerar nenhuma fase concluída apenas com este checklist: conferir fontes, testes e execução real.


## Rodada de UiState / UiEvent em 02/10/2026

- Onboarding agora expõe AppError no estado e OpenAppSettings em canal buffered. A conclusão publica completeOnboarding somente após persistir rotina/nome, preparar engines e agendar os workers; falha mantém o onboarding aberto. Cancelamento é propagado e busy é limpo no finally.
- Diário expõe ShowPlaceDetails com a data de origem, ignorando detalhes enfileirados de outra data. Falha de leitura usa DatabaseError.ReadFailed com texto localizado na apresentação.
- CollectUiEvents centraliza coleta com lifecycle STARTED e callback atualizado. Home/onboarding impedem requisições concorrentes de salvar a localização.
- Corrigidas duas chamadas do fixture at no MobilityGraph que eram ocultadas pelo método local homônimo.
- Rodada JVM atual: UiActionTest, PlacePickerViewModelTest e PhoneDiaryIntegrationTest. Ainda sem resultado; não registrar aprovação até o processo terminar.
- Preparadas em app/build (ainda NÃO aplicadas) migrações de Settings/PhoneInsights para ações com erros tipados/eventos, extração dos textos estáticos das seis telas e semântica/alvos de componentes comuns. Scripts: phone_ui_events.py, settings_ui_events.py, common_accessibility.py, migrate_ui_strings.py. Aplicar somente após a compilação ativa terminar, revisar o diff e executar nova validação.


Resultado da rodada anterior: **28 testes JVM aprovados**, UiActionTest 5, PlacePickerViewModelTest 19 e PhoneDiaryIntegrationTest 4. Main/KSP/Kotlin/Java/Hilt compilados com Onboarding/Diary/Home e erros tipados. Não houve execução E2E nessa rodada.

As quatro migrações preparadas foram aplicadas depois que o Gradle terminou. Migrados 154 textos estáticos para strings.xml; adicionadas mensagens/eventos de Settings e erros tipados no PhoneInsights. runUiAction mantém cancelamentos e erros fatais; Diary agora preserva diagnóstico no log. Atualizadas as três importações depreciadas de LocalLifecycleOwner. Acessibilidade inclui Pixel Lab e PlaceTypeCell.

Nova validação em execução: os mesmos 28 testes JVM, mais OnboardingHomeSaveInstrumentedTest, DiaryControlsTest, PlacePickerScreenTest e CommonAccessibilityTest no emulator-5554 com Vulkan. Ainda não registrar aprovação dessa nova rodada. Sem edição de fontes durante a compilação.

Fonte Press Start 2P e licença SIL OFL 1.1 obtidas do repositório oficial Google Fonts; arquivos temporários em app/build/PressStart2P-Regular.ttf e app/build/PressStart2P-OFL.txt. Integração em res/font e tipografia ainda pendente (fase 37).
