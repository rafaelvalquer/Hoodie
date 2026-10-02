Plano técnico de desenvolvimento — Hoodie Stability & Architecture
Como você definiu que daqui para frente vamos trabalhar diretamente na main, retiro do plano toda a parte de branches/PR. O foco passa a ser evolução técnica, estabilidade e qualidade da versão atual.
A sequência que eu recomendo é:
P0 — Corrigir riscos funcionais reais
        ↓
P1 — Completar Diário Digital e Workers
        ↓
P1 — Refatorar arquitetura
        ↓
P2 — Qualidade visual e UX
        ↓
P2 — Dados, performance e manutenção
        ↓
Release 0.2

Fase 1 — E2E do onboarding “Estou em casa → Sim”
Objetivo
Transformar o crash real que encontramos em um teste permanente.
Criar:
app/src/androidTest/java/com/hoodie/app/onboarding/
└── OnboardingHomeSaveInstrumentedTest.kt

O teste deve validar o fluxo completo:
App inicia
↓
Onboarding
↓
localização disponível
↓
"Você está em casa agora?"
↓
SIM
↓
ContextEngine.savePlaceHere()
↓
PlaceRepository
↓
CoordinateCipher
↓
SQLCipher
↓
Context HOME
↓
HoodieEngine
↓
próxima etapa

Cenários
Teste	Resultado
localização válida	Casa salva
banco abre	sem crash
SQLCipher carregado	sem UnsatisfiedLinkError
coordenada cifrada	não salvar plaintext
Place HOME criado	1 registro
contexto atualizado	HOME
app continua aberto	obrigatório
localização indisponível	mensagem, sem crash
erro de banco	mensagem, sem crash


Definition of Done
Onboarding real coberto
+
SQLCipher real
+
Room real
+
UI real

Fase 2 — Corrigir edição insegura do PlacePicker
Hoje existe risco de:
editingId existe
↓
places.byId() retorna null
↓
código interpreta como novo lugar
↓
INSERT
↓
duplicação

Alterar
presentation/screens/places/PlacePicker.kt

De:
val existing = s.editingId?.let {
    places.byId(it)
}

if (existing != null) {
    places.update(...)
} else {
    places.add(...)
}

Para lógica explícita:
if (s.editingId != null) {

    val existing = places.byId(s.editingId)
        ?: throw PlaceNotFoundException(s.editingId)

    places.update(
        existing.copy(
            name = name,
            type = s.type,
            latitude = s.latitude,
            longitude = s.longitude,
            radiusMeters = s.radius,
        )
    )

} else {

    places.add(
        name,
        s.type,
        s.latitude,
        s.longitude,
        s.radius,
        clock.nowMillis(),
    )
}

Criar:
core/error/
└── PlaceException.kt

sealed class PlaceException(message: String) : Exception(message) {

    class NotFound(
        val placeId: Long
    ) : PlaceException(
        "Place $placeId não encontrado"
    )

    class SaveFailed(
        cause: Throwable
    ) : PlaceException(
        cause.message ?: "Falha ao salvar"
    )
}

Fase 3 — Estado correto de carregamento do PlacePicker
Hoje erro de leitura pode virar silenciosamente:
null

Criar:
sealed interface PlaceLoadState {

    data object Idle : PlaceLoadState

    data object Loading : PlaceLoadState

    data object Ready : PlaceLoadState

    data object NotFound : PlaceLoadState

    data class Error(
        val cause: Throwable
    ) : PlaceLoadState
}

Adicionar ao PlacePickerState:
val loadState: PlaceLoadState =
    PlaceLoadState.Idle

Fluxo:
editar Place
↓
Loading
↓
byId()
├── encontrado → Ready
├── não existe → NotFound
└── exceção → Error

UI:
Não consegui carregar este local.

[TENTAR NOVAMENTE]
[VOLTAR]

Nunca transformar erro de leitura em cadastro novo.
Fase 4 — “Configurações restritas” no Diário Digital
Esse problema apareceu no seu Samsung e precisa virar parte oficial da UX.
Criar
presentation/screens/phoneinsights/
├── UsagePermissionScreen.kt
└── RestrictedSettingsHelpSheet.kt

Tela:
ANÁLISE DO CELULAR

[ ATIVAR ACESSO ]

Não conseguiu ativar?
Em alguns aparelhos, apps instalados manualmente
precisam liberar Configurações restritas.

[ VER COMO LIBERAR ]

Bottom sheet:
CONFIGURAÇÕES RESTRITAS

1. Abra Informações do Hoodie
2. Toque em ⋮
3. Permitir configurações restritas
4. Confirme PIN/digital
5. Volte para Acesso aos dados de uso
6. Autorize o Hoodie

[ ABRIR INFORMAÇÕES DO APP ]

Criar no UsageAccessManager
fun appDetailsIntent(): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

Fase 5 — Melhorar o estado da permissão de uso
Hoje temos apenas:
GRANTED
DENIED
UNAVAILABLE

Eu evoluiria para:
enum class UsagePermissionState {
    GRANTED,
    DENIED,
    UNAVAILABLE,
}

e adicionaria um estado de UX separado:
data class UsagePermissionUiState(
    val permission: UsagePermissionState,
    val showRestrictedHelp: Boolean,
)

Não tentar detectar diretamente “Configurações restritas”, porque o Android não fornece uma API confiável universal para isso.
Em DEBUG:
showRestrictedHelp =
    BuildConfig.DEBUG &&
    permission == UsagePermissionState.DENIED

Em release:
mostrar "Não conseguiu ativar?"

como ajuda opcional.
Fase 6 — Diário Digital opt-in explícito
Hoje:
analysisEnabled = true

Eu mudaria para:
analysisEnabled = false

Fluxo:
Diário Digital
↓
Explicação
↓
ATIVAR
↓
Permissão Android
↓
GRANTED
↓
analysisEnabled = true

Isso deixa a intenção do usuário muito mais clara.
Fase 7 — Room v5 para histórico digital completo
Hoje o histórico antigo perde:
appTimeline = emptyList()
hourlyScreenMs = emptyList()

Isso precisa ser resolvido.
Nova versão
@Database(
    ...
    version = 5
)

Criar:
Migration4To5.kt

Fase 8 — Nova tabela daily_screen_hourly
@Entity(
    tableName = "daily_screen_hourly",
    primaryKeys = ["date", "hour"]
)
data class DailyScreenHourlyEntity(
    val date: String,
    val hour: Int,
    val screenMs: Long,
)

Persistir:
00 → tempo
01 → tempo
...
23 → tempo

Então um dia histórico mantém o gráfico de 24h.
Fase 9 — Persistir timeline digital
Criar:
@Entity(
    tableName = "daily_phone_timeline",
    indices = [
        Index("date"),
        Index("startedAt")
    ]
)
data class DailyPhoneTimelineEntity(
    @PrimaryKey
    val id: String,

    val date: String,
    val packageName: String,
    val appLabel: String,
    val category: String,

    val startedAt: Long,
    val endedAt: Long,

    val context: String?,
)

Persistir o resultado de:
PhoneInsightsAssembler.timeline()

Assim:
hoje
e
histórico

usam a mesma experiência.
Fase 10 — Criar daily_context_usage
Hoje o total histórico por contexto é calculado a partir apenas dos principais apps.
Criar:
@Entity(
    tableName = "daily_context_usage",
    primaryKeys = [
        "date",
        "context"
    ]
)
data class DailyContextUsageEntity(
    val date: String,
    val context: String,
    val foregroundMs: Long,
    val sessionCount: Int,
)

A regra passa a ser:
daily_context_usage
→ total verdadeiro do contexto

daily_context_app_usage
→ ranking de apps

Fase 11 — Alterar DeviceUsageDao.replaceDay()
Hoje:
day
apps
contexts
sessions

Passará a:
day
apps
contextTotals
contextApps
hourly
timeline
sessions

Exemplo:
@Transaction
suspend fun replaceDay(
    day: DailyDeviceUsageEntity,
    apps: List<DailyAppUsageEntity>,
    contextTotals: List<DailyContextUsageEntity>,
    contextApps: List<DailyContextAppUsageEntity>,
    hourly: List<DailyScreenHourlyEntity>,
    timeline: List<DailyPhoneTimelineEntity>,
    sessions: List<PhoneAppSessionEntity>,
    epochDay: Long,
)

Tudo na mesma transação.
Fase 12 — Atualizar DeviceUsageMappers
Remover:
appTimeline = emptyList()
hourlyScreenMs = emptyList()

Novo:
Room
↓
Mappers
↓
DailyPhoneInsights completo

Resultado:
dia atual
≈
dia histórico

Fase 13 — Parar de truncar dados persistidos
Hoje:
apps.take(
    HoodieConfig.TOP_APPS_STORED
)

Eu mudaria para armazenar todos os agregados válidos.
Manter apenas:
TOP_APPS_SHOWN = 10

para UI.
Estratégia:
persistência → completa
visualização → limitada

Fase 14 — Corrigir timeline que atravessa contextos
Hoje uma sessão usa:
midpoint

para descobrir seu contexto.
Trocar para algoritmo de interseção.
Exemplo:
YouTube
11:55 ───────────────────── 12:25

Trabalho
─────────────── 12:10

Almoço
                ───────────

Resultado:
YouTube
11:55–12:10 → WORK
12:10–12:25 → LUNCH

Criar:
engine/deviceusage/
└── AppSessionContextSplitter.kt

API:
fun split(
    sessions: List<AppSession>,
    contexts: List<ContextSpan>,
    end: Long,
): List<ContextualAppSession>

Fase 15 — Testes do Diário Digital v5
Criar:
Migration4To5Test.kt
HistoricalPhoneInsightsTest.kt
AppSessionContextSplitterTest.kt

Cenários:
histórico mantém gráfico por hora
histórico mantém timeline
histórico mantém contexto real
sessão atravessa dois contextos
30+ apps continuam persistidos
reopen DB mantém tudo
migration v4 → v5 preserva dados

Fase 16 — Corrigir re-registro de geofence
Hoje o worker pode registrar várias vezes durante a hora 4.
Adicionar em SettingsRepository:
val lastGeofenceRegisterDay: Long

Nova chave:
LAST_GEOFENCE_REGISTER_DAY

Worker:
val today =
    now.atZone(clock.zone())
        .toLocalDate()
        .toEpochDay()

if (
    now.atZone(clock.zone()).hour ==
        DAILY_REREGISTER_HOUR &&
    settings.lastGeofenceRegisterDay != today
) {
    val result =
        geofences.registerAll()

    if (result.ok) {
        settings.setLastGeofenceRegisterDay(today)
    }
}

Fase 17 — Corrigir cancelamento do PhoneInsightsWorker
Trocar:
runCatching {
    ...
}.onFailure {
    return Result.retry()
}

por:
try {

    deviceUsage.refreshDay(
        today.minusDays(1)
    )

    deviceUsage.refreshDay(
        today
    )

} catch (e: CancellationException) {

    throw e

} catch (e: Exception) {

    log.log(
        DebugEventLogger.Category.WORKER,
        "PHONE_INSIGHTS_FAILED ${e.javaClass.simpleName}"
    )

    return Result.retry()
}

Isso respeita corretamente cancelamentos do WorkManager.
Fase 18 — Padronizar Workers
Criaria uma função comum:
worker/
└── WorkerResult.kt

suspend inline fun runWorkerTask(
    block: suspend () -> Unit
): Result {
    return try {
        block()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }
}

Usar onde fizer sentido.
Fase 19 — Refatorar ContextEngine
Hoje ContextEngine concentra responsabilidades demais.
Nova estrutura:
engine/context/

ContextEngine.kt
ContextSignalProcessor.kt
GeofenceContextHandler.kt
ContextQuestionHandler.kt
RoutineFallbackHandler.kt
ManualContextHandler.kt
PlaceLearningHandler.kt
ContextTransitionService.kt

Responsabilidades
ContextEngine
→ fachada

GeofenceContextHandler
→ ENTER/EXIT/flap

ContextQuestionHandler
→ perguntas/respostas

RoutineFallbackHandler
→ fallback de rotina

ManualContextHandler
→ contexto manual

PlaceLearningHandler
→ salvar/ensinar lugares

ContextTransitionService
→ única escrita de troca de contexto

Fase 20 — Manter mutex em um único nível
Hoje vários fluxos acabam dependendo de:
mutex.withLock

Depois da divisão:
ContextEngine
↓
lock
↓
handlers internos

Os handlers não devem criar locks independentes para a mesma operação.
Isso reduz risco de deadlock e facilita testes.
Fase 21 — Refatorar animações
Hoje:
HoodieAnimations.kt
~34 KB

Criar:
pixel/animation/definitions/

IdleAnimations.kt
WalkAnimations.kt
SleepAnimations.kt
WorkAnimations.kt
PhoneAnimations.kt
FoodAnimations.kt
GymAnimations.kt
LeisureAnimations.kt
TransitionAnimations.kt

E:
object AnimationRegistry {

    val clips =
        buildMap {
            putAll(idleAnimations())
            putAll(walkAnimations())
            putAll(workAnimations())
            ...
        }
}

Fase 22 — Refatorar AnimationStateMachine
Separar:
AnimationStateMachine
↓
TransitionPlanner
LoopSelector
InterruptResolver
ReactionResolver

Arquitetura:
Hoodie state
     ↓
TransitionPlanner
     ↓
AnimationSequence
     ↓
InterruptResolver
     ↓
AnimationPlayer

Fase 23 — Refatorar HoodiePainter
Criar:
pixel/sprite/procedural/

HoodiePoseRenderer.kt
HoodieBodyPainter.kt
HoodieHeadPainter.kt
HoodieArmsPainter.kt
HoodieLegsPainter.kt
HoodieTailPainter.kt
HoodieAccessoryPainter.kt

O HoodiePainter.kt passa a ser apenas:
class HoodiePainter(
    ...
) {
    fun paint(
        pose: HoodiePose
    ): SpriteFrame {
        ...
    }
}

Fase 24 — Usar padrão UiState + UiEvent
O PlacePicker já ficou melhor com:
StateFlow<State>
+
Flow<Event>

Aplicar nas principais telas:
Onboarding
Home
Settings
Diary
PhoneInsights

Exemplo:
sealed interface SettingsUiEvent {

    data class ShowMessage(
        val message: String
    ) : SettingsUiEvent

    data object DataDeleted :
        SettingsUiEvent
}

Fase 25 — Erros tipados
Criar:
core/error/

AppError.kt
DatabaseError.kt
LocationError.kt
PlaceError.kt
UsageAccessError.kt

Exemplo:
sealed interface AppError {

    data object DatabaseUnavailable :
        AppError

    data object LocationUnavailable :
        AppError

    data object PermissionDenied :
        AppError
}

A camada de negócio não deve carregar texto de UI.
Fase 26 — Mover textos para strings.xml
Criar/expandir:
app/src/main/res/values/
└── strings.xml

Migrar progressivamente:
Onboarding
PlacePicker
Settings
PhoneInsights
Diary
Home

Uso:
Text(
    stringResource(
        R.string.place_save_failed
    )
)

Fase 27 — Auditoria de acessibilidade
Revisar componentes customizados:
PixelButton
MapOverlayButton
PlaceTypeCell
Bottom navigation
Replay controls
Pixel Lab
Diary map

Garantir:
mínimo 48dp
contentDescription
Role.Button
Role.RadioButton
selected
stateDescription

Exemplo:
.semantics {
    role = Role.Button
    contentDescription =
        "Usar minha localização"
}

Fase 28 — Screenshot / golden tests
Criar módulo de testes visuais para:
PlacePicker
Onboarding
Home
Diary
Phone Insights
Settings

Dimensões:
360×640
360×800
411×891

Font scale:
1.0
1.3

Estados:
normal
loading
erro
dados vazios
dados completos

Fase 29 — Esconder Bottom Navigation em telas secundárias
Rotas primárias:
home
timeline
places
diary
settings

Mantêm barra.
Rotas secundárias:
place_picker
routine
memories
profile
pixel_lab
dev_lab

não mostram barra.
Implementação:
val bottomRoutes =
    tabs.map { it.route }

val showBottomBar =
    current?.route in bottomRoutes

Fase 30 — Melhorar resultados do mapa
Hoje resultados podem cobrir quase todo o mapa.
Alterar:
180dp

para:
120–140dp

ou máximo:
3 resultados visíveis

E integrar:
onMapInteraction {
    vm.dismissResults()
}

Fase 31 — Snackbar para geofence
Trocar:
Toast

por:
Snackbar

Mensagem:
Local salvo, mas o aviso de chegada
não pôde ser ativado.

[TENTAR NOVAMENTE]

Adicionar evento:
data object RetryGeofence :
    PlacePickerUiEvent

Fase 32 — Retenção do Diário Digital
Criar em HoodieConfig:
const val PHONE_SESSION_RETENTION_DAYS =
    365L

Worker de manutenção:
phone_app_sessions
mais antigas que 365 dias
↓
DELETE

Manter:
daily_device_usage
daily_context_usage
daily_app_usage

como histórico agregado.
Fase 33 — Índices do Room
Adicionar índices onde as queries são frequentes.
Exemplo:
@Entity(
    tableName = "context_events",
    indices = [
        Index("startedAt"),
        Index("endedAt"),
        Index(
            value = [
                "endedAt",
                "startedAt"
            ]
        )
    ]
)

Também avaliar:
phone_app_sessions
daily_app_usage
daily_context_usage
timeline_events

Isso pode entrar junto da migration v5.
Fase 34 — Atualizar documentação
Corrigir HoodieDatabase.kt.
Remover comentário antigo:
SQLCipher pode ser plugado depois

porque já está implementado.
README:
Room v5
SQLCipher ativo
phone_app_sessions
historical timeline
hourly history

Remover dos “próximos passos” coisas que já existem.
Fase 35 — Normalizar versionamento
Manter:
0.2.0-dev

durante esse trabalho.
Quando estabilizar:
0.2.0-rc1

Depois:
0.2.0

versionCode sempre cresce:
5
6
7
8
...

Fase 36 — Revisão manual da arte
Hoje:
"manualReview": false

para:
walk
idle
sleep
work

Executar revisão no Pixel Lab/Aseprite.
Atualizar:
{
  "walk": {
    "final": true,
    "manualReview": true,
    "pass": "manual-v1",
    "reviewedBy": "Rafael"
  }
}

Fase 37 — Fonte pixel
Adicionar posteriormente uma fonte OFL licenciada.
Estrutura:
res/font/
└── hoodie_pixel.ttf

Usar principalmente:
HUD
headlines
labels
números
botões

Textos longos continuam com fonte legível normal.
Ordem recomendada de execução
Ordem	Entrega	Prioridade
1	E2E onboarding HOME	🔴 P0
2	PlacePicker edição segura	🔴 P0
3	LoadState do PlacePicker	🔴 P0
4	Configurações restritas	🔴 P0
5	Phone Insights opt-in	🟠 P1
6	Room v5	🟠 P1
7	Histórico hourly	🟠 P1
8	Histórico timeline	🟠 P1
9	Totais por contexto	🟠 P1
10	Split de sessões por contexto	🟠 P1
11	Worker cancellation	🟠 P1
12	Geofence 1×/dia	🟠 P1
13	Refactor ContextEngine	🟠 P1
14	Refactor Animations	🟡 P2
15	Refactor Painter	🟡 P2
16	UiState + UiEvent	🟡 P2
17	Erros tipados	🟡 P2
18	strings.xml	🟡 P2
19	Accessibility	🟡 P2
20	Screenshot tests	🟡 P2
21	Bottom nav secundária	🟡 P2
22	Search overlay	🟡 P2
23	Snackbar geofence	🟡 P2
24	Retenção digital	🟡 P2
25	Índices Room	🟡 P2
26	Docs/versionamento	🟡 P2
27	Revisão artística	🟡 P2
28	Pixel font	🟢 P3


Meta da próxima versão
Eu fecharia essa sequência como:
HOODIE 0.2

ESTABILIDADE
✓ SQLCipher
✓ onboarding E2E
✓ Place seguro
✓ permissions UX

DIÁRIO DIGITAL
✓ histórico completo
✓ contexto correto
✓ Room v5

BACKGROUND
✓ workers robustos
✓ geofence correto

ARQUITETURA
✓ ContextEngine menor
✓ animations modulares
✓ painter modular

UX
✓ telas responsivas
✓ screenshot tests
✓ acessibilidade

ARTE
✓ revisão manual

Depois dessas etapas, a base fica muito mais adequada para voltar a adicionar funcionalidades sem aumentar rapidamente a dívida técnica.