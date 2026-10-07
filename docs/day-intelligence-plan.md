Arquivos atuais relevantes: DailyActivityWindowResolver, Entities.kt, MobilityEntities.kt e HoodieDatabase.kt.
1. Arquitetura final
A arquitetura alvo seria:
                  SINAIS DO CELULAR
                         │
       ┌─────────────────┼──────────────────┐
       │                 │                  │
   Geofence        Activity Recognition   Phone
       │                 │                  │
       ├──────────── GPS/speed ─────────────┤
       │                 │                  │
       ▼                 ▼                  ▼
 ┌────────────────────────────────────────────────┐
 │              Evidence Engine                   │
 │                                                │
 │ Normaliza evidências + confiança + timestamp   │
 └──────────────────────┬─────────────────────────┘
                        │
              ┌─────────▼──────────┐
              │ Confidence Engine  │
              └─────────┬──────────┘
                        │
        ┌───────────────┼─────────────────┐
        │               │                 │
        ▼               ▼                 ▼
 Context Engine   Mobility Engine  Day State Engine
        │               │                 │
        └───────────────┬┴─────────────────┘
                        ▼
               Canonical Day Model
                        │
          ┌─────────────┼─────────────┐
          ▼             ▼             ▼
       Diário        Jornada       Relógio
          │
          ▼
   Correções do usuário
          │
          ▼
      Learning Engine
          │
     ┌────┴────────┐
     ▼             ▼
Routine Model   Transport Model

A regra central deve ser:
sensores → evidências → inferência → confiança → decisão
                                     │
                              correção usuário
                                     │
                                     ▼
                                  aprendizado

2. Fase zero — estabilização antes da feature
Antes dessas funcionalidades, eu criaria uma branch/commit de estabilização da main, porque a versão atual ainda está com CI vermelho.
Gate obrigatório:
unit-tests       ✅
lint             ✅
assemble         ✅
instrumentation  ✅

Não recomendo introduzir DayStateEngine enquanto CharacterGeometry, transportes e lint ainda possuem regressões.
Depois disso:
0.2.0-dev
   ↓
Day Intelligence Foundation

3. P0 — Day State Engine
O DayStateEngine não deve substituir UserContextType.
São coisas diferentes:
DayState
= em qual estágio do dia a pessoa está?

UserContext
= onde/o que ela está fazendo?

Exemplo:
DayState.ACTIVE
Context.WORK
Place = Escritório

ou:
DayState.ACTIVE
Context.GYM
Place = Academia

Modelo
Criar:
domain/daystate/DayState.kt

enum class DayState {
    SLEEPING,
    WAKING,
    ACTIVE,
    COMMUTING,
    WINDING_DOWN
}

Resultado do motor:
data class DayStateSnapshot(
    val state: DayState,
    val startedAt: Long,
    val confidence: ConfidenceScore,
    val reason: DayStateReason,
    val provisional: Boolean,
)

Reasons:
enum class DayStateReason {
    WAKE_PHONE,
    WAKE_MOBILITY,
    WAKE_HOME_EXIT,

    ACTIVE_CONTEXT,
    ACTIVE_PHONE,
    ACTIVE_MOVEMENT,

    COMMUTE_CONFIRMED,
    COMMUTE_INFERRED,

    ARRIVAL_CONFIRMED,
    ARRIVAL_INFERRED,

    HOME_LOW_ACTIVITY,
    BEDTIME_PATTERN,

    SLEEP_INACTIVITY,
    SLEEP_CONFIRMED
}

4. Máquina de estados
O fluxo deve ser estritamente controlado:
                    ┌──────────────┐
                    │   SLEEPING   │
                    └──────┬───────┘
                           │ wake evidence
                           ▼
                    ┌──────────────┐
                    │    WAKING    │
                    └──────┬───────┘
                           │ sustained activity
                           ▼
                    ┌──────────────┐
          ┌────────▶│    ACTIVE    │◀────────┐
          │         └─────┬───┬────┘         │
          │               │   │              │
    arrived               │   │ movement     │ activity resumes
          │               │   ▼              │
          │               │ COMMUTING        │
          │               │   │              │
          │               │   └──────────────┘
          │               │
          │               │ low activity
          │               ▼
          │       ┌─────────────────┐
          └───────│  WINDING_DOWN   │
                  └────────┬────────┘
                           │ inactivity
                           ▼
                       SLEEPING

Transições inválidas devem ser bloqueadas.
Por exemplo:
SLEEPING → COMMUTING

não deve ocorrer diretamente.
Seria:
SLEEPING
→ WAKING
→ ACTIVE
→ COMMUTING

mesmo que isso aconteça rapidamente.
5. Reaproveitar Wake/Sleep existentes
Não duplicar:
WakeDetector
SleepOnsetDetector
DailyActivityWindowResolver

O novo motor passa a consumi-los.
Estrutura:
WakeDetector
     │
     ▼
DayStateEngine
     │
     ├─ WAKING
     ├─ ACTIVE
     ├─ COMMUTING
     └─ WINDING_DOWN
     │
     ▼
SleepOnsetDetector

Posteriormente DailyActivityWindowResolver passa a utilizar o resultado do DayStateEngine, e não implementar uma segunda lógica paralela.
6. Persistência do estado atual
Como o Android pode matar o processo, persistir somente o snapshot atual.
Nova entidade:
@Entity(tableName = "day_state")
data class DayStateEntity(
    @PrimaryKey val id: Int = 1,
    val state: DayState,
    val startedAt: Long,
    val confidence: Float,
    val reason: String,
    val provisional: Boolean,
    val updatedAt: Long,
)

Não persistiria cada mudança histórica nesse primeiro momento.
A história continua derivada das fontes canônicas:
context_events
mobility_sessions
phone sessions
timeline

Isso evita duplicação.
7. DayStateCoordinator
Criar:
engine/daystate/DayStateCoordinator.kt

Responsabilidade:
novo evento
    ↓
coleta snapshot
    ↓
EvidenceEngine
    ↓
DayStateEngine.resolve()
    ↓
mudou estado?
    │
    ├── não → atualiza confidence
    │
    └── sim
          ↓
       persiste
          ↓
       publica Flow

O UI deverá observar:
Flow<DayStateSnapshot>

8. P0 — Confidence Engine
Hoje já existem vários confidence: Float no projeto.
O problema é que cada sistema pode interpretar isso de maneira diferente.
Centralizar.
Criar:
domain/detection/ConfidenceScore.kt

@JvmInline
value class ConfidenceScore(
    val value: Float
)

Sempre:
0.0 … 1.0

E:
enum class ConfidenceBand {
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}

Sugestão inicial:
0.00–0.44  VERY_LOW
0.45–0.59  LOW
0.60–0.74  MEDIUM
0.75–0.89  HIGH
0.90–1.00  VERY_HIGH

9. A decisão não deve ser baseada apenas no score
Criar:
DetectionDecision

enum class DetectionDecision {
    UNKNOWN,
    ASK_USER,
    PROVISIONAL,
    AUTO_ACCEPT
}

Política:
Confiança	Comportamento
< 45%	não assumir; manter UNKNOWN
45–59%	perguntar se a informação for relevante
60–84%	aceitar provisoriamente, sem interromper
>= 85%	aceitar automaticamente
correção do usuário	100% / fonte USER


Assim:
Casa       96% → aceita
Ônibus     72% → registra provisoriamente
Restaurante 51% → pergunta

10. Evidências explicáveis
Toda inferência deve saber explicar seu score.
Criar:
data class DetectionEvidence(
    val type: EvidenceType,
    val contribution: Float,
    val timestamp: Long,
)

Exemplo:
RESTAURANTE 78%

+0.35 Geofence
+0.15 permanência > 8 min
+0.12 horário de almoço
+0.10 histórico
+0.06 correções anteriores

Isso será importante para debug e aprendizado.
11. Evitar perguntas repetitivas
Criar:
QuestionPolicy

A pergunta só aparece quando:
confidence baixo
AND
mudança relevante
AND
não perguntou recentemente
AND
não existe correção anterior forte

Adicionar cooldown:
mesmo candidato:
mínimo 30 min

e por sessão:
máximo 1 pergunta sobre transporte

salvo mudança real de modo.
12. P0 — Correção inteligente do Diário
Essa funcionalidade deve modificar o dado canônico, não apenas o cartão.
Exemplo:
12:10–13:04
🏢 Trabalho

Usuário toca:
Editar

e escolhe:
🍴 Restaurante

Resultado:
ContextEvent
WORK
      ↓
LUNCH / RESTAURANT

placeId
Escritório
      ↓
Restaurante XPTO

Depois o sistema recalcula:
Diário
Jornada
Relógio
Resumo
Phone Insights contextual
Routine Model

13. Interface de edição
Ao tocar em qualquer bloco:
┌────────────────────────────┐
│ Editar evento              │
│                            │
│ Tipo                       │
│ Restaurante             ▼  │
│                            │
│ Local                      │
│ Restaurante XPTO        ▼  │
│                            │
│ Início                     │
│ 12:10                      │
│                            │
│ Fim                        │
│ 13:04                      │
│                            │
│ [Cancelar]      [Salvar]   │
└────────────────────────────┘

O usuário poderá corrigir:
contexto
lugar
horário inicial
horário final
transporte

14. DiaryCorrectionEntity
Não recomendo perder a informação original.
Criar:
@Entity(tableName = "diary_corrections")
data class DiaryCorrectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val targetType: CorrectionTargetType,
    val targetId: Long,

    val originalContext: String?,
    val correctedContext: String?,

    val originalPlaceId: Long?,
    val correctedPlaceId: Long?,

    val originalStartAt: Long?,
    val correctedStartAt: Long?,

    val originalEndAt: Long?,
    val correctedEndAt: Long?,

    val createdAt: Long,
)

Ela é:
audit trail
+
feedback de aprendizado

Não é a fonte usada pelo Diário.
O canonical event continua sendo atualizado.
15. DiaryCorrectionService
Criar:
engine/correction/DiaryCorrectionService.kt

Fluxo:
Usuário salva
      ↓
CorrectionService
      ↓
Transaction
      │
      ├── atualiza ContextEvent/Mobility
      ├── registra DiaryCorrection
      ├── corrige Timeline relacionada
      └── recalcula derivados
      ↓
Room Flow invalida UI
      ↓
Diário/Jornada/Clock atualizam

Usar TransactionRunner já existente.
16. Correção precisa vencer qualquer inferência
Adicionar fonte:
USER_CORRECTION

com:
confidence = 1.0

Regra:
USER_CORRECTION
>
USER_CONFIRMATION
>
HIGH-CONFIDENCE SENSOR
>
ROUTINE
>
FALLBACK

Nunca permitir que o motor sobrescreva depois uma correção explícita.
17. Reaprendizado
Exemplo:
segunda 12:05
WORK → corrigido para RESTAURANT

terça 12:12
WORK → corrigido para RESTAURANT

quarta 12:08
WORK → corrigido para RESTAURANT

O Routine Learning passa a enxergar:
11:55–13:20
LUNCH probability ↑

Na próxima semana:
12:09
GPS ambíguo
+
histórico forte
        ↓
LUNCH 86%

Nenhuma pergunta.
18. P1 — Routine Model
Já existe:
RoutineEntity

com horário de:
trabalho
almoço
dias

Eu não substituiria essa entidade.
Ela representa configuração explícita.
Criaria outra camada:
LearnedRoutine

Porque:
configuração do usuário
≠
padrão aprendido

19. Novo modelo de rotina aprendida
Criar:
@Entity(tableName = "learned_routine_slots")
data class LearnedRoutineSlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val dayGroup: String,
    val type: RoutineEventType,

    val medianMinute: Int,
    val deviationMinutes: Int,

    val sampleCount: Int,
    val confidence: Float,

    val updatedAt: Long,
)

Eventos:
WAKE
LEAVE_HOME
WORK_START
LUNCH_START
LUNCH_END
WORK_END
GYM_START
HOME_RETURN
SLEEP

20. Agrupamento por dia
Não calcular uma média única de segunda a domingo.
Separar inicialmente:
WEEKDAY
SATURDAY
SUNDAY

Depois, se houver amostras suficientes:
MONDAY
TUESDAY
...

Exemplo:
Seg–Sex
Wake       06:48 ± 17m
Work       08:04 ± 22m
Lunch      12:13 ± 19m
Home       18:54 ± 31m
Sleep      23:22 ± 28m

21. Algoritmo de aprendizado
Não precisa de LLM.
Usar os últimos:
28 dias

com peso maior para dados recentes.
Exemplo:
peso = exp(-ageDays / 14)

Excluir:
day exceptions
confidence muito baixo
eventos contraditórios
dias incompletos

Correções manuais:
peso 2x ou 3x

22. Robustez estatística
Evitar média simples.
Usar:
mediana
MAD (Median Absolute Deviation)

Assim:
normal:
06:45
06:50
06:47
06:51

feriado:
10:38

não destrói o padrão.
23. RoutineLearner
Criar:
engine/routine/RoutineLearner.kt

Executar:
1 vez após fechar o dia

ou:
WorkManager diário

Fluxo:
últimos 28 dias
     ↓
eventos confiáveis
     ↓
correções
     ↓
agrupamento
     ↓
estatística robusta
     ↓
LearnedRoutineSlot

24. Manual vence aprendido
Exemplo:
RoutineEntity
usuário configurou:
trabalho 08:00

LearnedRoutine:
07:48

O sistema pode usar:
08:00 como regra
07:48 como evidência contextual

Mas nunca substituir silenciosamente a configuração explícita.
25. P1 — Transporte 2.0
O Android já informa:
IN_VEHICLE

Mas não:
CAR vs BUS vs TRAIN vs METRO

O Hoodie precisa de um classificador local.
Arquitetura:
Activity Recognition
       │
       ├── IN_VEHICLE
       │
GPS ───┤
       │
Time ──┤
       │
Stops ─┤
       │
History│
       ▼
TransportFeatureBuilder
       │
       ▼
TransportClassifier
       │
       ▼
CAR 0.20
BUS 0.63
TRAIN 0.10
METRO 0.07
       │
       ▼
Confidence Engine

26. Features do classificador
Não persistir rota GPS completa.
Calcular em memória:
Feature	Uso
velocidade média	carro/transporte público
velocidade máxima	trem/carro
variação de velocidade	ônibus
quantidade de paradas	ônibus/trem
intervalo entre paradas	ônibus/trem/metro
duração	reforço
origem/destino conhecidos	histórico
horário	rotina
dia da semana	rotina
modo histórico do mesmo trajeto	forte evidência
Activity Recognition	categoria base


27. Privacidade
Manter a decisão atual do Hoodie:
não guardar rota GPS

Persistir apenas agregados, se necessário:
data class TransportFeatures(
    val durationMs: Long,
    val avgSpeedMps: Float,
    val maxSpeedMps: Float,
    val stopCount: Int,
    val stopRatio: Float,
)

Nunca:
lat1,lng1
lat2,lng2
lat3,lng3
...

28. Padrão aprendido de trajeto
O MobilitySessionEntity já possui:
originPlaceId
destinationPlaceId
currentMode
confirmed

Isso é suficiente para criar:
TransportPattern

Exemplo:
Origem: Trabalho
Destino: Casa
Horário: 17:30–19:30

BUS
18 confirmações

CAR
2 confirmações

Então:
P(BUS | Trabalho→Casa, 18h)
=
90%

Isso vira evidência forte.
29. TransportPatternEntity
Criar:
@Entity(
    tableName = "transport_patterns",
    primaryKeys = [
        "originPlaceId",
        "destinationPlaceId",
        "dayGroup",
        "timeBucket",
        "mode"
    ]
)
data class TransportPatternEntity(
    val originPlaceId: Long,
    val destinationPlaceId: Long,
    val dayGroup: String,
    val timeBucket: Int,
    val mode: MovementMode,
    val confirmations: Int,
    val rejections: Int,
    val confidence: Float,
    val updatedAt: Long,
)

timeBucket:
30 ou 60 minutos

30. Classificador determinístico inicialmente
Eu não colocaria ML nessa versão.
Começaria com scores explicáveis.
Exemplo:
BUS

IN_VEHICLE             +20
velocidade 15–60 km/h   +10
muitas paradas          +20
rota habitual ônibus    +35
horário habitual         +10
----------------------------
95

CAR:
IN_VEHICLE             +20
velocidade média        +15
poucas paradas          +20
rota habitual carro     +15
----------------------------
70

Resultado:
BUS 95%
CAR 70%

Normalizar:
BUS 79%
CAR 21%

E passar para o Confidence Engine.
31. Pergunta de transporte
Se:
BUS 52%
CAR 44%

não decidir automaticamente.
Perguntar:
Como você está se deslocando?

[ 🚗 Carro ]
[ 🚌 Ônibus ]
[ 🚆 Trem ]
[ 🚇 Metrô ]
[ 🚲 Bicicleta ]

Resposta:
MobilitySegment.confirmed = true
source = CONFIRMATION
confidence = 1.0

e atualiza:
TransportPattern

32. Evitar classificação instável
Não trocar:
CAR → BUS → CAR → BUS

durante a mesma viagem.
Adicionar hysteresis:
modo atual:
BUS 72%

novo resultado:
CAR 76%

não troca.
Exigir:
novo candidato >= atual + 15 pontos

por pelo menos:
30–60 segundos

ou uma evidência forte.
33. Segmentos multimodais
A estrutura atual já suporta:
MobilitySegmentEntity

Portanto Transporte 2.0 deve permitir:
Casa
  ↓
WALKING 8 min
  ↓
BUS 31 min
  ↓
WALKING 5 min
  ↓
Trabalho

Não transformar isso em:
BUS 44 min

A Jornada então fica muito melhor.
34. Integração dos cinco sistemas
Um exemplo real:
06:42
Phone usado 3 min

WakeDetector:
88%
       ↓
DayState = WAKING

06:48
atividade continua
       ↓
DayState = ACTIVE

07:25
geofence HOME EXIT
walking
       ↓
DayState = COMMUTING
mode = WALKING 94%

07:34
IN_VEHICLE
rota Casa→Trabalho
histórico BUS
       ↓
BUS 87%

08:10
WORK geofence ENTER
       ↓
DayState = ACTIVE
Context = WORK 96%

12:08
sai do trabalho
novo lugar próximo
rotina LUNCH 12:12
       ↓
RESTAURANT 57%

Hoodie pergunta:
"Você está almoçando?"

Usuário confirma.
       ↓
confidence = 100%
RoutineLearner recebe feedback

18:02
Trabalho→Casa
       ↓
BUS 93%

22:48
Casa
sem mobilidade
pouco telefone
próximo horário habitual de sono
       ↓
DayState = WINDING_DOWN

23:19
inatividade sustentada
       ↓
DayState = SLEEPING

Tudo alimenta a mesma história.
35. Migration 7 → 8
Eu adicionaria quatro estruturas persistentes:
day_state
diary_corrections
learned_routine_slots
transport_patterns

Portanto:
HOODIE_DATABASE_VERSION
7 → 8

Criar:
Migration7To8.kt

A migração deve ser somente aditiva:
CREATE TABLE...
CREATE INDEX...

Nada de reescrever:
places
context_events
mobility_sessions
phone history

Isso reduz o risco do SQLCipher.
36. Novos módulos
Estrutura recomendada:
domain/
 ├── daystate/
 │    ├── DayState.kt
 │    ├── DayStateSnapshot.kt
 │    └── DayStateReason.kt
 │
 ├── detection/
 │    ├── ConfidenceScore.kt
 │    ├── DetectionEvidence.kt
 │    └── DetectionDecision.kt
 │
 ├── routine/
 │    └── LearnedRoutine.kt
 │
 └── correction/
      └── DiaryCorrection.kt

engine/
 ├── daystate/
 │    ├── DayStateEngine.kt
 │    └── DayStateCoordinator.kt
 │
 ├── detection/
 │    ├── ConfidenceEngine.kt
 │    └── QuestionPolicy.kt
 │
 ├── correction/
 │    └── DiaryCorrectionService.kt
 │
 ├── routine/
 │    └── RoutineLearner.kt
 │
 └── mobility/
      ├── TransportFeatureBuilder.kt
      ├── TransportClassifier.kt
      ├── TransportPatternLearner.kt
      └── TransportDecisionPolicy.kt

37. Feature flags
Durante o desenvolvimento:
DAY_STATE_ENGINE = false
SMART_DIARY_CORRECTIONS = false
UNIFIED_CONFIDENCE_ENGINE = false
LEARNED_ROUTINE = false
TRANSPORT_CLASSIFIER_V2 = false

Ativar nesta ordem:
Confidence Engine
        ↓
DayStateEngine
        ↓
Diary Corrections
        ↓
Routine Learning
        ↓
Transport 2.0

O Confidence Engine vem antes porque os outros quatro dependem dele.
38. Testes essenciais
Criaria cinco suítes principais:
Suite	O que valida
DayStateEngineTest	todas as transições e rollback de estado
ConfidenceEngineTest	scores, thresholds, conflitos e hysteresis
DiaryCorrectionIntegrationTest	correção propaga para Diário/Jornada/Clock
RoutineLearnerTest	mediana, outliers, correções e exceptions
TransportClassifierTest	carro/ônibus/trem/metro e multimodal


E um E2E:
FullDayIntelligenceIntegrationTest

Cenário:
Sleep
 ↓
Wake
 ↓
Home
 ↓
Walk
 ↓
Bus
 ↓
Work
 ↓
Restaurant
 ↓
Work
 ↓
Bus
 ↓
Gym
 ↓
Home
 ↓
Wind down
 ↓
Sleep

Validar:
DayState
Context
Place
Mobility
Confidence
Diary
Journey
Clock
Routine learning

em um único dia sintético.
39. Sequência de desenvolvimento
Eu dividiria em seis etapas:
Sprint	Entrega
Sprint 0	CI verde + estabilização
Sprint 1	Confidence Engine + Evidence model
Sprint 2	DayStateEngine + persistência + integração Wake/Sleep
Sprint 3	edição/correção inteligente do Diário
Sprint 4	RoutineLearner + rotina aprendida
Sprint 5	TransportFeatureBuilder + TransportClassifier 2.0
Sprint 6	integração E2E + UX + rollout


O mais importante é não desenvolver Transporte 2.0 antes de Confidence e Corrections. Sem isso, o classificador até pode melhorar, mas não terá um mecanismo consistente para aprender quando errar.
Resultado esperado
Ao final dessa evolução, a arquitetura do Hoodie passa de:
vários detectores
       ↓
várias telas

para:
          REALIDADE OBSERVADA
                  │
                  ▼
          Evidence Engine
                  │
                  ▼
         Confidence Engine
                  │
     ┌────────────┼─────────────┐
     ▼            ▼             ▼
 DayState      Context       Mobility
     │            │             │
     └────────────┼─────────────┘
                  ▼
            DIA ENTENDIDO
                  │
       ┌──────────┼──────────┐
       ▼          ▼          ▼
     Diário    Jornada    Relógio
       │
       ▼
   Correções
       │
       ▼
   Aprendizado
       │
   ┌───┴────────────┐
   ▼                ▼
Rotina          Transporte

Esse seria o ponto em que eu consideraria o Hoodie deixando de ser apenas um aplicativo que registra localização e atividades e passando a ser um sistema que interpreta a rotina do usuário, sabe quando está em dúvida e melhora quando é corrigido.