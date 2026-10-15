# Revisão do plano técnico — 15/10/2026

Base: commit `15c1ce45` (`Implementa interações V1 do Hoodie`). O código já continha captura de toque/toque longo no Canvas, hit test por transparência e oclusão, catálogo de 110 regras, resolver contextual, política de segurança, histórico por sessão, animações de carinho, retomada da microação e prioridade de fala no balão existente. A implementação foi preservada.

## Ajustes desta revisão

- `HOODIE_TOUCH_INTERACTIONS_V1 = false`, conforme o valor inicial solicitado. Os testes de interação habilitam o recurso explicitamente.
- Reação pendente válida por 2.000 ms desde a aceitação do gesto, incluindo o tempo de entrega até o motor.
- Solicitações com ID próprio, criação e expiração no relógio monotônico. O motor recebe apenas o tempo restante, limitado pela configuração.
- Expiração exclusiva: no instante do prazo, uma solicitação pendente não pode iniciar animação nem fala. Sequências já iniciadas terminam normalmente.
- Hit test ignora personagem ausente, transição, saída e coordenadas não finitas, antes de consultar a máscara ou o sprite.
- Regressões de sessão, expiração no motor e hit test acrescentadas aos testes existentes.
- Fixtures de continuidade solicitam carinho dentro de uma janela segura: café (2,1 s) e roupa (2,6 s) não podem ser interrompidos por uma solicitação feita no início do ciclo, pois ela expira antes da pausa. As asserções de início único, todas as etapas, postura, posição, objetos e retomada permanecem. Um teste adicional exige a expiração do pedido feito no início do café.

Não foram alteradas persistência, localização, mobilidade, necessidades, rotinas, Diário ou Memórias.

## Validação atual

Resultados da homologação anterior permanecem no relatório histórico; não representam uma nova execução para estas alterações.

- Build debug, APK de instrumentação e lint aprovados na primeira rodada (`build/interaction-plan-20261015-gates.log`).
- Suíte completa JVM: 1.198 testes, três falhas nos fixtures de café/roupa afetados pelo novo prazo, zero erros e seis ignorados. XMLs preservados em `build/interaction-plan-20261015-first-jvm-results`.
- Após ajustar os fixtures e acrescentar a regressão de expiração durante café: **43 testes de interação aprovados, zero falhas, erros ou ignorados**. APK AndroidTest e lint novamente aprovados (`build/interaction-plan-20261015-recheck.log`, `BUILD SUCCESSFUL`). Essa é uma reexecução direcionada; não representa uma segunda suíte completa.
- Folha `build/interaction-review/contact-sheet.png` regenerada e revisada: antes, PET_HEAD, PURR, NUZZLE e retomada em Casa, Escritório com caneca, Restaurante com comida e Loja com roupa.
- A rodada Android inicial de 28 casos encontrou três falhas: timeout da árvore de acessibilidade, expiração na Loja e expiração em uma combinação de fala/café. Os fixtures dos dois últimos foram ajustados à janela segura.
- Diagnóstico do emulador: captura `build/interaction-plan-screen.png` mostra diálogo “System UI isn't responding” cobrindo a tela; a falha persistiu em `build/interaction-plan-final-screen.png` após boot frio. Não houve wipe de dados. A System UI foi reiniciada pelo botão do próprio diálogo. Os resultados dos casos afetados estão registrados abaixo.
- Segunda rodada Android (`build/interaction-plan-20261015-device-final.log`): **26/28 aprovados**, incluindo os 12 casos da matriz visual, três de continuidade e quatro de fala. Dois casos no início foram afetados pela falha/recuperação da System UI: ação nomeada e ausência temporária da árvore Compose no teste de arraste. Após a recuperação, a classe inteira de gestos passou: **9/9**, em `build/interaction-plan-20261015-gestures-final.log`. Todos os 28 cenários possuem aprovação, considerando a reexecução; não se trata de uma única rodada completa verde.
- TalkBack efetivamente ativo: **1/1 aprovado** (`requireTalkBack=true`), em `build/interaction-plan-20261015-talkback-final.log`. O teste verifica o serviço ativo e as ações Android nomeadas de toque e carinho, com exatamente um evento de cada tipo. As configurações anteriores (`enabled_accessibility_services=null`, `accessibility_enabled=0`) foram restauradas em `finally` e conferidas. Isso não comprova navegação gestual humana.
- As 48 saídas da matriz (24 capturas de cena/contexto e 24 diagnósticos) foram copiadas para `build/interaction-plan-20261015-ui-review`. Revisão visual das capturas de 360×640 dp com fonte 130% e personagem sentado e 411×891 dp com fonte 100% e personagem em pé: balão legível, personagem e controles visíveis. Os testes verificam também alcance do contexto por rolagem em todas as 12 combinações.
- `git diff --check` aprovado.

Nenhum teste físico foi realizado nesta revisão. A flag permanece desativada, aguardando homologação para ativação destinada aos usuários.
