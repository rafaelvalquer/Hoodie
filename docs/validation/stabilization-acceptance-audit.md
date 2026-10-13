# Auditoria de aceite — estabilização 0.1.8.16

Requisitos derivados do plano fornecido pelo usuário. Evidência local atual:
1.000 unitários, zero falhas e seis ignorados, sobre `83622916` com as alterações
do checkout. A suíte instrumentada completa no AVD executou 329 casos, com
148 aprovados, uma falha ambiental e 180 capturas ignoradas; o teste afetado
passou depois da correção do ambiente. Este documento
não declara homologação física nem CI remoto aprovado.

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
| Nome opcional, somente hoje, posição manual, opções próximas e ausência de prompts ao dirigir | `NewPlaceSaveIntegrationTest`, `NewPlaceSaveSheetTest`, controles da Home | Unitários aprovados; suíte de dispositivo atual em curso |
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
| Entrada por ajuste manual, lugar salvo e descoberta; distinção de Mercado/Restaurante/Trabalho | Três `StoreSceneDeviceTest`, navegação real, banco Hilt de produção, recriação da Activity | Cinco testes direcionados anteriores aprovados; suíte completa atual em curso |
| Cachorro completa entrada, compras, espera, pagamento e saída com variedade/falas próprias | `StoreSceneTest`, `ShoppingNpcBrainTest`, ciclo no dia integrado | Unitários aprovados |
| Rotas conectadas, sem atravessar araras, saída fora da tela e reinício fora da cena | Testes de todas as rotas e amostragem do ciclo; limites e finalização determinísticos | Unitários aprovados |
| Roupa, cortina, caixa e sacola seguem ações sem persistir indevidamente | `StoreSceneTest` verifica flags/props e oclusão da cortina; estados do NPC independentes dos de Hoodie | Unitários aprovados |
| Sessões separadas por planta/dia/variante/contexto; período/recomposição/pausa preservam história | `ShoppingSessionContinuityTest`; chave de sessão, relógio com origem nula, saída para HOME durante pagamento e caminhada sem herdar caixa ativo | Três unitários aprovados |
| Caches não crescem indefinidamente e replay continua determinístico após evicção | LRU de 32 ciclos; regressão de 24 h em Loja/Mercado; cache de sprites 96/96 no teste diagnóstico | Unitários e medição direcionada aprovados |
| Matriz completa e quatro períodos revisados antes de atualizar referências | Aprovações humanas, manifesto APPROVED, galeria `docs/npc-art-review/v3`, goldens comparados sem gravação | Aprovado pelo usuário; unitários aprovados |
| FPS próximo de 30, render P95 até 33 ms e memória sem crescimento contínuo por ciclo | Suíte completa: 30,06 FPS/render P95 7,27 ms; 30 minutos virtuais/54.600 frames; Android na Home real: P95 26 ms em 1.998 frames | Evidência de AVD debug; jank alto do Android impede afirmar ausência de travamentos; falta físico e uso prolongado real |

## Integração e gates

| Gate | Evidência/pendência |
| --- | --- |
| Casa → Carro → Cantina → Loja → Casa e Cantina no dia seguinte | `StabilizationDayIntegrationTest` aprovado; sensores/relógio controlados, Room e engines reais; não equivale a um dia físico |
| Unitários | 1.000 casos, zero falhas, seis ignorados; alteração posterior exige nova validação |
| Lint/assemble/sprites | Aprovados: zero erros/79 avisos, APK com 17 assets |
| Instrumentação/migrações | 329 casos no Pixel 8 AVD Android 17: 148 aprovados, uma falha por tutorial de caneta do Gboard, 180 ignorados; mesmo teste de teclado real passou após desativar escrita por caneta no ambiente |
| CI remoto | Testes remotos integrados e contratos adequados; falta executar os quatro jobs no PR com a configuração de IME reproduzível |
| Homologação física e uso real | Aparelho indisponível, conforme resposta do usuário. Não considerar substituído por emulador ou relógio virtual |

Os screenshots parametrizados têm um gate próprio de comparação e não devem
ser apresentados como comparações aprovadas quando executados em modo de captura.
O total de testes instrumentados deve vir dos XMLs finais, pois o contador do
runner pode contabilizar assumptions duas vezes.
