# UX 2.0 — homologação (S5)

Branch `feature/ux-2-0`. Aparelho de teste: emulador Pixel_8 (`emulator-5554`), Android 17 (API 37), 1080×2400 @ 420 dpi.
Os testes instrumentados rodaram só com `am instrument`, nunca com `connectedDebugAndroidTest`, e nunca no `emulator-5556`.

## Resultado

| Área | Verificação | Resultado |
|---|---|---|
| Testes unitários/integração | `:app:testDebugUnitTest` (1052 testes) | 4 falhas, todas do Passeio 2.0 (ver "Pendências") |
| Testes instrumentados (Home, cartão de configuração, Dia de Demonstração) | `HomeHeroUiTest`, `DayPresentationUiTest`, `SetupAndDemoUiTest` no Pixel_8 | 19 de 19 ok |
| Matriz de telas | 360×640, 360×800 e 411×891, fonte 100% e 130% | ver abaixo |
| Modo sem permissões | notificações, localização e reconhecimento de atividade negados | Home funciona, aviso de rotina provável, sem crash |
| Morte do processo | `am kill` com o app em segundo plano e reabertura | Home restaurada (mesma cena, contexto e horários), sem crash |
| Crashes/ANR | `logcat -b crash` durante toda a sessão | 0 |
| Tarefas agendadas | `dumpsys jobscheduler` | `ReconcileWorker`, `RoutineLearningWorker` e `PhoneInsightsWorker` presentes |
| Migração do banco v11 → v12 | `Migration11To12Test` + app aberto no aparelho com dados existentes | ok |

## Matriz de telas (Home)

Corrigido durante a homologação:

1. **Cabeçalho com fonte a 130% em telas de 360 dp**: o ícone de perfil saía da tela. Com fonte ampliada, hora e período agora empilham.
2. **Cena minúscula em 360×640**: o limite de altura derrubava a escala de 2× para 1× por poucos dp. A escala inteira agora encaixa com tolerância de 15% (`HomeHeroLayout.sceneScale`); a rolagem cobre o excedente.

Estado final: nenhum elemento importante cortado nas 6 combinações. O cartão "Você" fica abaixo da dobra em telas pequenas e se alcança rolando (permitido pelo plano). Com fonte a 130% em 360 dp, a escala da cena é 1× em 360×640 e 2× em 360×800.

## Não verificado

- **Reinício do aparelho**: não reiniciei o emulador. A recuperação da fila de notificações após reinício é coberta por `NotificationDeliveryCoordinatorTest`; o WorkManager reagenda sozinho.
- **Execução real do worker de reavaliação**: o WorkManager não executa trabalho periódico antes da hora, mesmo forçado. O agendamento está confirmado; a lógica, coberta por testes.
- **Bateria**: sem medição. Esta versão não acrescenta trabalho em segundo plano: a reavaliação reutiliza o `ReconcileWorker` de 15 min que já existia.
- **Orientação horizontal, teclado aberto e navegação por acessibilidade**: não testados.
- **Teste com usuários reais** e metas de usabilidade do plano (§11): dependem de pessoas.
- **Onboarding progressivo ponta a ponta no aparelho**: exige instalação limpa (apagaria dados do emulador). Coberto por `ProgressiveOnboardingTest`, `SetupProgressTest` e `DemoDayIsolationTest`, e pela tela do Dia de Demonstração no aparelho.

## Pendências (não são desta entrega)

As 4 falhas abaixo existem na `main` e são do **Passeio 2.0** (flag `LEISURE_LIVING_WORLD_V2`, commit `df907de8`). Não foram alteradas aqui:

- `LeisureNpcDirectorTest.horariosVariantesESementesSaoPrevisiveis`: compara listas de `AmbientNpcSlot` que contêm `LeisureNpcActor`, classe sem `equals`; instâncias novas nunca são iguais.
- `LeisureNpcDirectorTest.parqueTemVisitantesComAcoesDiferentesEmVezDePingPong`: `NullPointerException` na linha 35 do teste.
- `NewPlaceScenesTest` (`leisure_walk`) e `NpcArtV3GoldenTest` (`scene_leisure`): os goldens divergem; precisam de revisão visual por quem mantém o Passeio 2.0 antes de serem regravados.