# Personalidade do Hoodie 2.0

Personalidade local, determinística e discreta. Nada sai do aparelho, nada pune o usuário e nenhuma
decisão persistida (simulador, `HoodieDecisionEngine`) passa por aqui: só escolhas visuais e falas.

## Perfil (`domain/personality`)

`HoodiePersonalityProfile` (id, versão e traços 0..100, validados no `init`), estável e sem decaimento:

| Traço | Valor | Efeito |
|---|---|---|
| curiosidade | 80 | olha em volta um pouco mais vezes; "perceber/olhada" ao abrir o app; falas de lugar novo |
| afinidade com café | 85 | microações de café ×1,28; falas de café na pausa e na chegada |
| sociabilidade | 65 | acenar/sorrir ao abrir o app; falas de volta |
| afinidade com descanso | 70 | alongar, cochilo sentado, banco ×1,23; alonga um pouco mais vezes |
| espontaneidade | 50 | reservado para variações futuras |

`PersonalityWeightPolicy.adjust(base, mult, eligible)` só ajusta ações elegíveis com peso > 0 (mínimo 1):
uma ação bloqueada continua bloqueada e nenhuma vira obrigatória. `PersonalityVisuals.apply` roda no fim de
`VisualDirector.resolve`; estados com uma ação só não mudam. Ao abrir o app, `ReactionDirector.weights`
redistribui os 45% de reação conforme os traços e mantém "continua o que está fazendo" em 55%.

## Motor (`engine/personality`)

- `PersonalityTriggerDetector`: só bordas observadas (primeira abertura do dia, volta ao app depois de 30 min,
  `DayState` WAKING/WINDING_DOWN, chegada ao trabalho recente, pausa no trabalho, lugar novo confirmado, atividade
  longa encerrada, volta para casa vinda de um contexto conhecido). Sem contexto anterior, não existe "voltou".
- `HoodiePersonalityEngine.decide(context, ledger, gate)`: função pura e determinística (a fala é escolhida pela
  semente do dia e da visita). Devolve `PersonalityDecision(eventId, animation, dialogueId, priority, durationMs)`.
- `PersonalityLedger.record(...)`: a camada separada que marca como mostrado, persistida em DataStore
  (`hoodie_personality`); memória corrompida volta vazia.

Limites (`PersonalityLimits`, configuráveis):

| Reação | Limite |
|---|---|
| cumprimento de abertura | 1/dia (a volta ao app: 1/dia, nunca logo depois do cumprimento) |
| lugar novo, chegada ao trabalho, volta para casa | 1 por visita |
| pausa | 1 a cada 60 min |
| comentário de personalidade | ≥ 2 min entre falas (o gesto pode acontecer sem fala) |
| fim do dia, despertar | 1/dia |
| mesma fala | não volta antes de 6 h |
| microeventos de NPC | até 2 a cada 10 min |

Prioridade: segurança/transição → animação essencial → evento social → reação de personalidade → fala ambiente.
O `PersonalityGate` adia a reação durante caminhada, troca de cena, fala de NPC ou algo mais prioritário; numa
ação de trabalho só sai a fala (o gesto não interrompe as mãos no teclado). Abrir o app não ganha gesto
duplicado: o gesto da abertura continua com o `ReactionDirector`.

## Falas

`assets/metadata/personality-dialogues.json`: `id`, `trigger`, `trait`/`minTrait` (opcionais), `context`,
`activity`, `hourFrom`/`hourTo`, `text` (≤ 48 caracteres) e `priority`. Cada gatilho tem uma fala genérica.
Nenhuma fala cobra ou afirma algo não observado; a volta ao app é um cumprimento neutro ("Olá! Que bom te ver
por aqui."). Um teste barra palavras de culpa.

Na Home, `HomeViewModel.build()` usa `AmbientDialogueScheduler` (as falas de antes: 40%, 9 s) e
`PersonalityDialogueScheduler`; o `DialogueArbiter` escolhe uma fala só (a de maior prioridade) para o mesmo
`HomeUiState.dialogue`/`SpeechBubble`, e a fala ambiente espera enquanto a de personalidade está no balão.

## Microeventos de NPC (`pixel/npc/events`)

`SceneEventDirector`: `SceneStoryEvent` (id, tipo, cena, participantes, pontos, duração, prioridade, cooldown),
fases `PLANNED → APPROACHING → INTERACTING → RESOLVING → COMPLETED/CANCELLED`, orçamento de 2 eventos a cada 10 min,
cooldown por tipo e `admit` (sem participante em dois eventos). Cada cena implementa `SceneStoryController`
(`canStart`, `snapshotAt`, `dispose`) e publica o estado em `SceneEnv.sceneStory` para os props.

### OFFICE_DOCUMENT_DELIVERY (implementado)

`OfficeDocumentDeliveries`, dono `OfficeSocialSession` (sem agendador paralelo). Durante uma visita do bulldog
(60% dos ciclos, respeitando o orçamento), o coelho levanta com a folha da mesa, anda até ficar ao lado do chefe,
entrega, volta e senta. A folha é um objeto único: `ON_DESK → HELD_BY_RABBIT → HELD_BY_BULLDOG → CARRIED_OUT`.
O plano previa `ON_BULLDOG_DESK`, mas a cena não tem mesa para o bulldog (ele visita o escritório em pé), então ele
lê a folha e sai com ela. Só acontece quando o coelho e o gato ficam sentados na mesma ação pela entrega inteira, o
bulldog está parado num ponto livre, nenhuma conversa da rotina está marcada e o recebimento não cobre as falas do
chefe. A rotina-base não pausa: o coelho volta à mesma ação sentada. Folha de revisão em
`build/pixel-preview/story-events/office-document-delivery.png`.

### Ainda não implementados

`OFFICE_COFFEE_CHAT`, `RESTAURANT_SPILL_HELP`, `CAFE_ORDER_HANDOFF`, `LEISURE_SHARED_VIEW`, `BEACH_SHARED_PHOTO`,
`SHOPPING_DROPPED_BAG` e `HOTEL_LUGGAGE_HELP` já existem como tipos e passam pelo mesmo orçamento, mas ainda não têm
roteiro nas cenas. Os próximos devem seguir o padrão da entrega: controlador por cena, reservas na sessão já
existente (`LeisureSocialCoordinator`, `RestaurantNpcBrain`, `ShoppingNpcBrain`, `VenueInteractionCoordinator`) e
o objeto com dono único.

## Testes

`engine/personality/HoodiePersonalityTest` (perfil, política de pesos, determinismo, limites, prioridade, catálogo
sem culpa, codec, detector) e `pixel/npc/events/SceneStoryEventsTest` (orçamento, fases, entregas determinísticas,
objeto único em ordem, sem teletransporte, sem cruzar conversas nem falas do chefe).
