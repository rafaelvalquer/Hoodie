# Auditoria de aceite — estabilização 0.1.8.16

Requisitos derivados do plano fornecido pelo usuário. Evidência local atual:
1.000 unitários, zero falhas e seis ignorados, sobre `83622916` com as alterações
do checkout. A suíte instrumentada completa no AVD executou 329 casos, com
148 aprovados, uma falha ambiental e 180 capturas ignoradas; o teste afetado
passou depois da correção do ambiente. O CI remoto do PR #9, execução
`38433878862` sobre `37f074d7`, aprovou os quatro jobs. Seu relatório
instrumentado contém 329 casos, zero falhas e 180 capturas ignoradas.
Este documento não declara homologação física.

## Mobilidade

| Requisito | Evidência atual | Estado |
| --- | --- | --- |
| Restaurar velocidades preserva veículo/paradas; idempotência e isolamento de sessões | `TransportFeatureBuilderTest`; runtime usa `TransportEvidenceEntity.features()` e períodos persistidos | Unitários aprovados |
| `evaluate()` e mudanças de trecho não sobrescrevem agregados concorrentes | Updates direcionados em `MobilitySessionDao`, transações de promoção/troca/finalização; `VehicleEvidenceTest` e regressões de estabilidade | Unitários aprovados |
| GPS fora do mutex, com cancelamento e rejeição de resultado obsoleto | `TransportSpeedSampler`; `aBlockedSpeedLookupDoesNotHoldMobilityEngineMutexAndItsStaleResultIsDropped`; testes de frescor e cancelamento | Unitários aprovados |
| Até cinco tentativas por viagem, separadas por 60 s, sem renovar ao trocar de período | Reserva transacional da sessão + evidências; testes de migração dos contadores, concorrência, reinício e esgotamento | Unitários aprovados |
| Caminhada confirmada após 45 s sem segundo ENTER, inclusive ao reabrir Home | `MobilityStabilityRegressionTest`, `VehicleEvidenceTest`, percurso integrado de carro | Unitários aprovados; reinício da engine simulado |
| Semáforo mantém carro; Diário multimodal e limites dos segmentos corretos | `carro semaforo estacionamento e trabalho preservam diario apos reinicio` | Unitário integrado aprovado |
| Inferência provisória não se torna confirmação explícita | Políticas de confiança/decisão e `TransportIntelligenceIntegrationTest`; origens PREFERENCE/LEARNED/USER_CORRECTION distintas | Unitários aprovados |
| Nove falhas históricas classificadas individualmente | Log e HTMLs do CI #46; `stabilization-ci-original-failures.md` | Diagnóstico documentado; referenciar no PR |

## Lugares

| Requisito | Evidência atual | Estado |
| --- | --- | --- |
| Nome opcional, somente hoje, posição manual, opções próximas e ausência de prompts ao dirigir | `NewPlaceSaveIntegrationTest`, `NewPlaceSaveSheetTest`, controles da Home | Unitários e instrumentação aprovados |
| Consultar proximidade sem mesclar vizinhos, com escolha explícita para reutilizar | Busca 75 m independente da geofence; ordenação por tipo/distância; precisão desconhecida informada como aproximada; `PlaceRepositoryNearbyTest` | Unitários aprovados |
| Salvamento idempotente/atômico; rollback permite nova tentativa | Transação de lugar + histórico + resposta; testes de facades concorrentes e falha na gravação da pergunta | Unitários aprovados |
| Categorias, busca normalizada, ordenação, favoritos e última visita | `PlacesScreen`, `PlaceListQueryTest`, updates direcionados de favorito/edição; fluxo real sem localização conferiu Lojas/Restaurantes, favorito, edição e busca EDITADA | Unitários e validação pela interface aprovados |
| Cadastro permanece disponível sem geofence e informa disponibilidade do monitoramento | Estado ativo/indisponível/fora do limite na lista; `OverlappingPlacesIntegrationTest` consulta lugar não monitorado | Unitários aprovados |
| Segunda visita reutiliza placeId; ENTER/DWELL não duplica visita; ambiguidade não escolhe vizinho | `StabilizationDayIntegrationTest`, `PlaceRepositoryNearbyTest`, quatro testes de sobreposição | Unitários aprovados |
| Resposta tardia mantém identidade, intervalo, fonte e presente; renomear/excluir preserva história | `NewPlaceSaveIntegrationTest`, `PlaceHistoryIntegrationTest`; categoria histórica STORE preservada sem cadastro | Unitários aprovados |
| Coordenadas cifradas e cadastros persistentes após reinício | `StoreSceneDeviceTest` usa Keystore real e confere payload/decriptação; testes SQLCipher; force-stop real com PID 20677 encerrado e novo PID 22291 manteve nome editado, favorito, contexto e cena | Instrumentação e reinício completo no AVD verificados |

## Loja

| Requisito | Evidência atual | Estado |
| --- | --- | --- |
| Entrada por ajuste manual, lugar salvo e descoberta; distinção de Mercado/Restaurante/Trabalho | Três `StoreSceneDeviceTest`, navegação real, banco Hilt de produção, recriação da Activity | Três testes aprovados no CI/API 34, além das execuções locais |
| Cachorro completa entrada, compras, espera, pagamento e saída com variedade/falas próprias | `StoreSceneTest`, `ShoppingNpcBrainTest`, ciclo no dia integrado | Unitários aprovados |
| Rotas conectadas, sem atravessar araras, saída fora da tela e reinício fora da cena | Testes de todas as rotas e amostragem do ciclo; limites e finalização determinísticos | Unitários aprovados |
| Roupa, cortina, caixa e sacola seguem ações sem persistir indevidamente | `StoreSceneTest` verifica flags/props e oclusão da cortina; estados do NPC independentes dos de Hoodie | Unitários aprovados |
| Sessões separadas por planta/dia/variante/contexto; período/recomposição/pausa preservam história | `ShoppingSessionContinuityTest`; chave de sessão, relógio com origem nula, saída para HOME durante pagamento e caminhada sem herdar caixa ativo | Três unitários aprovados |
| Caches não crescem indefinidamente e replay continua determinístico após evicção | LRU de 32 ciclos; regressão de 24 h em Loja/Mercado; cache de sprites 96/96 no teste diagnóstico | Unitários e medição direcionada aprovados |
| Matriz completa e quatro períodos revisados antes de atualizar referências | Aprovações humanas, manifesto APPROVED, galeria `docs/npc-art-review/v3`, goldens comparados sem gravação | Aprovado pelo usuário; unitários aprovados |
| FPS próximo de 30, render P95 até 33 ms e memória sem crescimento contínuo por ciclo | 30,06 FPS/render P95 7,27 ms no teste; Home por 30 minutos reais: P95 36 ms com meminfo/GC; janela complementar de dez minutos: P95 21 ms/17.971 quadros, PSS oscilante via kernel; trace de 30 s: render P95 1,163 ms | Evidência de AVD debug em janelas distintas; não comprova ausência de travamentos ou vazamentos prolongados; falta físico e homologação em uso real |

## Integração e gates

| Gate | Evidência/pendência |
| --- | --- |
| Casa → Carro → Cantina → Loja → Casa e Cantina no dia seguinte | `StabilizationDayIntegrationTest` aprovado; sensores/relógio controlados, Room e engines reais; não equivale a um dia físico |
| Unitários | 1.000 casos, zero falhas, seis ignorados; alteração posterior exige nova validação |
| Lint/assemble/sprites | Aprovados: zero erros/79 avisos, APK com 17 assets |
| Instrumentação/migrações | CI/API 34: 329 casos, 149 aprovados, zero falhas, 180 capturas ignoradas; 13 testes de banco aprovados, incluindo as três migrações instrumentadas; teste de teclado real aprovado |
| CI remoto | Quatro jobs aprovados na execução `38433878862`, commit `37f074d7`; alterações posteriores continuam sujeitas ao CI |
| Homologação física e uso real | Aparelho indisponível, conforme resposta do usuário. Não considerar substituído por emulador ou relógio virtual |

Os screenshots parametrizados têm um gate próprio de comparação e não devem
ser apresentados como comparações aprovadas quando executados em modo de captura.
O total de testes instrumentados deve vir dos XMLs finais, pois o contador do
runner pode contabilizar assumptions duas vezes.

## CI do PR #9 — primeira execução

Execução `38428823281`, commit `6dd68159` (árvore idêntica à validação local):
`unit-tests`, `lint` e `assemble` aprovados, incluindo a verificação de sprites
no APK. O job `instrumentation` falhou antes dos testes: o `avdmanager` não
encontrou o perfil `pixel_8`, e nenhum relatório instrumentado foi produzido.
Classificação: ambiente do CI, não falha de asserção do aplicativo.

As tentativas seguintes expuseram dois problemas na preparação: `sdkmanager`
não estava no PATH antes da ação do emulador, e a atualização de `latest` foi
instalada pelo SDK em `latest-2`, deixando o catálogo antigo em uso. O workflow
agora usa o caminho absoluto, instala `cmdline-tools;23.0`, preserva a instalação
anterior em `preinstalled` e aponta `latest` para a versão explícita. Verifica a
versão instalada e a presença do perfil antes de criar o AVD. Mantém API 34,
Pixel 8, resolução, densidade e toda a suíte instrumentada. Na execução
`38433878862`, a preparação do catálogo passou e o job avançou para
`Connected tests (API 34)`. Este registro é um snapshot anterior ao resultado
final da instrumentação. Posteriormente, o job concluiu com sucesso. O HTML
baixado do artefato `11833852355` confirma 329 testes, zero falhas e 180 ignorados
(149 aprovados), incluindo StoreSceneDeviceTest, PlacePickerScreenTest e as
migrações. SHA-256 do ZIP preservado localmente:
`263e88b43813988f18f9fcf35f176cba9f6e163ab5a712a3419a7d9096c8affb`.
Os 509 eventos de conclusão no log do runner não são 509 casos: assumptions são
contadas novamente. O PR permanece em rascunho pela homologação física pendente.

O artefato unitário dessa execução foi substituído pelo relatório da reexecução
isolada de sprites, embora a etapa da suíte completa tenha passado. O workflow
passou a publicar `unit-test-reports` antes dessa reexecução e a guardar seu
relatório separado em `sprite-sheet-test-reports`. Nenhum teste foi removido ou
teve suas asserções enfraquecidas por esse ajuste de preservação de evidência.
