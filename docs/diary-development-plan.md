Plano técnico de desenvolvimento — Diário Visual Simplificado do Hoodie
1. Objetivo do módulo
Criar uma nova funcionalidade chamada Diário, onde o usuário poderá visualizar, por dia:
- tempo gasto em casa
- tempo gasto em trabalho
- tempo gasto em transporte
- tempo gasto em almoço
- tempo gasto em academia
- tempo gasto em outros contextos
- eventos da própria rotina
- atividades do Hoodie
- um mapa estilizado 2D em pixel art
- uma linha simbólica do percurso
- um replay animado do dia
- detalhes ao clicar nos estabelecimentos/lugares
2. Visão funcional
O que o módulo fará
Tela “Diário”
O usuário poderá acessar:
Diário
[ Hoje ] [ Ontem ] [ Escolher data ]

Resumo do dia
Mostrar cards com tempos agregados:
- 🏠 Casa
- 🏢 Trabalho
- 🚶 Transporte
- 🍽️ Almoço
- 🏋 Academia
- 🎉 Outros
Linha do tempo
Misturar:
- eventos do usuário
- eventos do Hoodie
Exemplo:
07:12  Acordou em casa
08:03  Saiu de casa
08:49  Chegou ao trabalho
08:55  Hoodie começou a trabalhar
12:16  Saiu para almoço
12:20  Hoodie foi almoçar
13:02  Voltou ao trabalho
18:07  Saiu do trabalho
18:51  Chegou em casa
19:10  Hoodie jogou videogame
21:35  Hoodie assistiu TV

Mapa pixel art
Mostrar visualmente:
- Casa
- Trabalho
- Restaurante
- Academia
- Lugares salvos
- Linha simbólica entre pontos visitados
- marcador do Hoodie
- destaque do ponto atual durante replay
Interação com estabelecimentos
Ao tocar em um ponto do mapa:
- nome do lugar
- tipo
- horário de chegada
- horário de saída
- tempo total passado ali
- quantidade de visitas no dia
- contexto predominante
- possíveis eventos relacionados
Replay do dia
Botão:
▶ Reproduzir meu dia

Durante a reprodução:
- o tempo avança
- o marcador se move entre os locais
- o ponto atual é destacado
- a timeline acompanha
- o resumo pode destacar o contexto atual
3. Escopo do MVP
Incluído no MVP
- tela Diário
- hoje / ontem / escolher data
- resumo por contexto
- timeline do usuário + Hoodie
- mapa pixel art simbólico
- lugares clicáveis
- replay simbólico do percurso
- cálculo baseado em context_events, timeline_events, hoodie_activities
- sem rota real, sem GPS contínuo
Fora do MVP
- rota real por rua
- replay geográfico exato
- heatmap
- estatísticas semanais/mensais
- exportação de imagem/PDF
- compartilhamento
- replay em vídeo
- múltiplos personagens
4. Princípio arquitetural
Esse módulo deve usar a base atual do app, sem mudar a lógica de privacidade.
Fonte dos dados
O diário será derivado principalmente de:
context_events
timeline_events
hoodie_activities
places

O mapa não usará trilha real.
Ele será reconstruído a partir da sequência de lugares/contextos do dia.
Exemplo:
HOME
↓
COMMUTING
↓
WORK
↓
COMMUTING
↓
LUNCH
↓
COMMUTING
↓
WORK
↓
COMMUTING
↓
HOME

Transformado em:
Casa ─── Trabalho ─── Restaurante ─── Trabalho ─── Casa

5. Arquitetura de alto nível
DATABASE
  ├── context_events
  ├── timeline_events
  ├── hoodie_activities
  └── places
        │
        ▼
DIARY ENGINE
  ├── DailySummaryCalculator
  ├── DailyTimelineBuilder
  ├── DailyMapBuilder
  ├── PlaceVisitBuilder
  └── ReplaySequenceBuilder
        │
        ▼
DIARY VIEW MODEL
        │
        ▼
UI
  ├── Resumo
  ├── Timeline
  ├── Mapa Pixel Art
  ├── Detalhe do Lugar
  └── Replay

6. Estrutura de pacotes sugerida
com.hoodie.app
│
├── engine
│   └── diary
│       ├── DailySummaryCalculator.kt
│       ├── DailyTimelineBuilder.kt
│       ├── DailyMapBuilder.kt
│       ├── PlaceVisitBuilder.kt
│       ├── ReplaySequenceBuilder.kt
│       ├── DiaryAssembler.kt
│       └── DiaryMappers.kt
│
├── data
│   └── repository
│       └── DiaryRepository.kt
│
├── domain
│   └── diary
│       ├── model
│       └── usecase
│
├── presentation
│   └── screens
│       └── diary
│           ├── DiaryScreen.kt
│           ├── DiaryViewModel.kt
│           ├── DiaryUiState.kt
│           ├── DiaryComponents.kt
│           ├── DiaryMapView.kt
│           ├── DiaryReplayController.kt
│           └── PlaceDetailBottomSheet.kt
│
└── pixel
    └── diary
        ├── DiaryMapRenderer.kt
        ├── DiaryMapLayoutEngine.kt
        ├── DiaryMapIcons.kt
        ├── DiaryRoutePainter.kt
        ├── DiaryReplayAnimator.kt
        └── DiaryMapPalette.kt

7. Modelos de domínio
7.1 Resumo do dia
data class DailySummary(
    val date: LocalDate,
    val homeMs: Long,
    val workMs: Long,
    val commutingMs: Long,
    val lunchMs: Long,
    val gymMs: Long,
    val leisureMs: Long,
    val otherMs: Long
)

7.2 Evento da timeline
data class DiaryTimelineItem(
    val id: String,
    val timestamp: Long,
    val type: DiaryTimelineType,
    val actor: DiaryActor,
    val title: String,
    val subtitle: String?,
    val emoji: String?,
    val relatedPlaceId: Long?,
    val relatedContext: UserContextType?
)

Tipos
enum class DiaryActor {
    USER,
    HOODIE,
    SYSTEM
}

enum class DiaryTimelineType {
    ARRIVED,
    LEFT,
    ACTIVITY,
    CONTEXT_CHANGE,
    MEMORY,
    NOTE
}

7.3 Visita a um lugar
data class PlaceVisit(
    val placeId: Long,
    val placeName: String,
    val placeType: PlaceType,
    val arrivalAt: Long,
    val departureAt: Long?,
    val durationMs: Long,
    val visitsCount: Int,
    val relatedTimelineIds: List<String>
)

7.4 Nó do mapa
Cada lugar do dia vira um nó visual.
data class DiaryMapNode(
    val id: String,
    val placeId: Long?,
    val label: String,
    val type: DiaryMapNodeType,
    val x: Int,
    val y: Int,
    val visitIndex: Int,
    val arrivalAt: Long?,
    val departureAt: Long?,
    val durationMs: Long
)

Tipos de nó
enum class DiaryMapNodeType {
    HOME,
    WORK,
    RESTAURANT,
    GYM,
    SCHOOL,
    MARKET,
    LEISURE,
    FAMILY,
    OTHER
}

7.5 Segmento do trajeto simbólico
data class DiaryMapEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val startedAt: Long?,
    val endedAt: Long?,
    val durationMs: Long,
    val style: EdgeStyle
)

7.6 Replay
data class ReplayFrame(
    val timestamp: Long,
    val activeNodeId: String?,
    val activeEdgeId: String?,
    val progressOnEdge: Float,
    val highlightedTimelineItemIds: Set<String>,
    val currentContext: UserContextType?,
    val currentHoodieActivity: HoodieActivity?
)

8. Regras de negócio do diário
8.1 Cálculo do resumo do dia
Fonte:
context_events do dia

Regra
Somar o tempo por contexto:
- HOME → Casa
- WORK → Trabalho
- COMMUTING → Transporte
- LUNCH → Almoço
- GYM → Academia
- LEISURE → Lazer
- demais → Outros
Importante
Se um evento atravessar a meia-noite:
23:30 → 01:00

o cálculo deve recortar pelo dia selecionado.
8.2 Construção da timeline
Misturar:
- mudanças de contexto do usuário
- eventos da timeline persistida
- atividades do Hoodie
Ordenação:
timestamp asc

Regra de apresentação:
- eventos do usuário com cor/ícone 1
- eventos do Hoodie com cor/ícone 2
- eventos do sistema com cor neutra
8.3 Construção do mapa simbólico
Não usar coordenadas reais do trajeto.
Usar apenas os lugares visitados na ordem do dia.
Exemplo
HOME
↓
WORK
↓
RESTAURANT
↓
WORK
↓
HOME

Vira:
Casa ─── Trabalho ─── Restaurante ─── Trabalho ─── Casa

Regra para nós
Criar um nó por visita relevante, não necessariamente por lugar único.
Exemplo:
WORK 08:30–12:00
WORK 13:00–18:00

podem virar:
- dois nós diferentes visualmente, ou
- um mesmo lugar com dois estados
Minha recomendação no MVP:
Criar um nó por visita, mesmo que o lugar repita.
Isso melhora o replay e a leitura da jornada.
8.4 Layout do mapa
O mapa será estilizado, não geográfico.
Estratégia
Organizar os nós em uma grade 2D agradável visualmente.
Exemplo:
   [Trabalho]
        |
[Casa]--+--[Restaurante]
        |
   [Academia]

Ou um caminho em zigue-zague:
Casa → Trabalho
           ↓
     Restaurante
           ↓
       Trabalho
           ↓
         Casa

Algoritmo recomendado
DailyMapLayoutEngine:
- recebe a sequência de visitas
- distribui os nós em uma malha lógica
- evita sobreposição
- define posição final (x,y) na cena pixel art
No MVP eu usaria layout determinístico simples:
- eixo principal horizontal
- se a quantidade crescer, quebrar linha
- pequeno deslocamento vertical por tipo de lugar
9. Interação com lugares
Ao clicar em um nó do mapa:
abrir BottomSheet com:
- nome do lugar
- tipo
- chegada
- saída
- tempo no local
- visita nº X do dia
- eventos da timeline relacionados
- atividade do Hoodie predominante naquele período
Exemplo:
🏢 Trabalho

Chegada: 08:49
Saída: 12:16
Tempo: 3h27

Hoodie:
- começou a trabalhar às 08:55
- tomou café às 10:21
- digitou por boa parte da manhã

10. Replay do dia
Objetivo
Animar a rotina do dia sem precisar de rota real.
10.1 Como será a reprodução
Ao apertar:
▶ Reproduzir meu dia

o sistema entra em modo replay.
Durante o replay
- relógio avança
- o marcador do Hoodie se move entre os nós
- a linha do trajeto pode ser revelada gradualmente
- o nó atual é destacado
- a timeline rola automaticamente
- o evento atual fica destacado
10.2 Modelo de tempo do replay
Criar controle de velocidade:
- 1x
- 2x
- 5x
- 10x
No MVP:
- 1x
- 5x
- 10x
10.3 Estados do replay
enum class ReplayState {
    IDLE,
    PLAYING,
    PAUSED,
    FINISHED
}

10.4 Controle
data class ReplayUiState(
    val state: ReplayState,
    val currentTimestamp: Long?,
    val speed: ReplaySpeed,
    val activeNodeId: String?,
    val activeEdgeId: String?,
    val progress: Float
)

10.5 Geração dos frames
ReplaySequenceBuilder criará uma sequência lógica baseada em:
- visitas
- transições entre visitas
- eventos da timeline
A reprodução não será baseada em frames fixos salvos no banco, mas calculada dinamicamente.
11. Renderização do mapa pixel art
11.1 Canvas
Usar Jetpack Compose Canvas para desenhar:
- fundo
- grid/terreno
- ruas estilizadas
- ícones dos lugares
- linhas do percurso
- marcador do Hoodie
- destaque do replay
11.2 Camadas do mapa
BACKGROUND
↓
DECORAÇÃO / TERRENO
↓
RUAS / LINHAS
↓
NÓS (LUGARES)
↓
MARCADOR HOODIE
↓
OVERLAYS (seleção, replay)

11.3 Elementos visuais
Fundo
Pixel art simplificada:
- gramado
- ruas
- prédios
- árvores
- água opcional
- quadras
Ícones por tipo
- Casa → casinha
- Trabalho → prédio
- Restaurante → lanchonete/café
- Academia → halter
- Mercado → sacola
- Lazer → estrela/controller
- Outros → pin genérico
Marcador
- um mini Hoodie em pixel art, ou
- um ícone de gatinho
Minha recomendação:
mini Hoodie andando.
11.4 Estilo do mapa
Não fazer mapa real tipo Google Maps.
Fazer mapa com linguagem visual do app:
- pixel art
- cores reduzidas
- contorno escuro
- leitura simples
- formato “mini cidade”
12. Componentes de UI
12.1 DiaryScreen
Tela principal.
Seções:
1. barra superior
2. seletor de data
3. cards resumo
4. mapa
5. botão replay
6. timeline
12.2 DiarySummaryCards
Cards horizontais ou grid 2x3.
12.3 DiaryMapCard
Card com:
- mapa pixel art
- botão replay
- controles de velocidade
- botão centralizar
- legenda
12.4 PlaceDetailBottomSheet
Mostra detalhes do local clicado.
12.5 DiaryTimelineList
Lista vertical rolável.
12.6 DateSelector
Botões:
- Hoje
- Ontem
- Escolher data
13. Repositório e casos de uso
13.1 DiaryRepository
Responsável por montar o diário completo.
interface DiaryRepository {
    suspend fun loadDiary(date: LocalDate): DailyDiary
}

13.2 DailyDiary
data class DailyDiary(
    val summary: DailySummary,
    val timeline: List<DiaryTimelineItem>,
    val visits: List<PlaceVisit>,
    val map: DiaryMapData,
    val replay: ReplaySequence
)

13.3 LoadDiaryUseCase
class LoadDiaryUseCase(
    private val repository: DiaryRepository
) {
    suspend operator fun invoke(date: LocalDate): DailyDiary
}

14. Banco de dados
Boa notícia
Para o MVP, não é obrigatório criar novas tabelas.
Dá para montar o diário usando o que já existe:
- context_events
- timeline_events
- hoodie_activities
- places
Tabelas opcionais futuras
Só se houver necessidade de performance:
- daily_diary_cache
- daily_diary_visits
- daily_diary_summary
Minha recomendação
Não criar tabela nova no começo.
Calcular sob demanda.
15. Algoritmos principais
15.1 PlaceVisitBuilder
Entrada:
- context events do dia
- places cadastrados
Saída:
- lista de visitas
Regra
Sempre que o contexto entrar em um lugar conhecido relevante:
- cria visita
- calcula chegada
- calcula saída
- calcula duração
15.2 DailyMapBuilder
Entrada:
- lista de visitas
Saída:
- nós do mapa
- edges do mapa
Regra
- um nó por visita
- um edge para cada transição
- nomes legíveis
- tipos definidos por PlaceType
15.3 ReplaySequenceBuilder
Entrada:
- mapa
- timeline
- visitas
Saída:
- sequência reproduzível
Regra
O replay usa:
- tempo do dia
- progressão linear entre nós
- destaque sincronizado na timeline
15.4 DailySummaryCalculator
Entrada:
- context events
Saída:
- tempos agregados
15.5 DailyTimelineBuilder
Entrada:
- context events
- hoodie activities
- timeline events
Saída:
- timeline única ordenada
16. Performance
Como o cálculo é por um único dia, o custo será baixo.
Cuidados
- usar Dispatchers.Default/IO para cálculo
- cache em memória por data aberta
- recalcular somente ao trocar data
- não regenerar replay a cada recomposição
- o Canvas do mapa deve desenhar estruturas simples
17. Privacidade
Esse módulo mantém a filosofia do Hoodie.
Continuará sem:
- rota GPS contínua
- trilha de rua
- histórico ponto a ponto
Usará apenas:
- lugares já conhecidos
- contextos já calculados
- horários já salvos
Então o diário é:
uma reconstrução simbólica do dia, não um rastreador detalhado.

Isso deve ficar claro na UX.
18. Fluxo de uso do usuário
Cenário
Abertura
Usuário entra em:
Diário

Seleciona data
- Hoje
- Ontem
- data específica
Vê resumo
- tempo por categoria
Vê mapa
- pontos do dia
- sequência visual
Toca em Trabalho
Abre detalhes do local.
Reproduz o dia
Replay mostra:
- jornada
- transições
- eventos
- Hoodie acompanhando
19. Navegação no app
Adicionar uma nova rota:
Diary

Se hoje existe bottom nav com 4 itens, minha recomendação é avaliar:
Opção A
Adicionar novo item:
- Hoje
- Histórico
- Lugares
- Diário
- Ajustes
Opção B
Manter 4 itens e colocar Diário dentro de Histórico
Minha recomendação
Para MVP:
colocar Diário como aba própria, porque será um dos diferenciais visuais do app.
20. Fases de desenvolvimento
Fase 1 — Base do diário
Objetivo
Montar os dados do diário sem UI final.
Entregas
- DiaryRepository
- DailySummaryCalculator
- DailyTimelineBuilder
- PlaceVisitBuilder
- DailyMapBuilder
- modelos de domínio
- testes unitários do cálculo
DoD
- dado um dia com eventos, gerar:
  - resumo
  - timeline
  - visitas
  - mapa lógico
Fase 2 — Tela Diário v1
Objetivo
Tela funcional sem replay ainda.
Entregas
- DiaryScreen
- seletor de data
- cards resumo
- timeline
- mapa estático
- clique em lugar
- bottom sheet de detalhes
DoD
- usuário consegue abrir um dia e entender a rotina
Fase 3 — Mapa pixel art
Objetivo
Trocar mapa lógico cru por mapa estilizado.
Entregas
- DiaryMapRenderer
- ícones pixel art
- layout 2D
- edges estilizados
- marcador do Hoodie
- efeitos de destaque
DoD
- mapa bonito, coerente com o app
Fase 4 — Replay
Objetivo
Animar o dia.
Entregas
- ReplaySequenceBuilder
- DiaryReplayController
- play/pause/reset
- velocidades
- movimento do marcador
- highlight sincronizado da timeline
DoD
- o replay percorre o dia inteiro corretamente
Fase 5 — Polimento
Objetivo
Acabamento final.
Entregas
- skeleton/loading
- empty state
- tratamento de dias incompletos
- animações suaves
- acessibilidade
- otimização de performance
21. Casos especiais
Dia sem dados
Mostrar:
Ainda não há dados suficientes para montar o diário deste dia.

Dia com poucos lugares
Exemplo:
- casa
- trabalho
- casa
Mapa continua funcionando.
Lugar não reconhecido
Criar nó como:
- “Outro lugar”
- tipo OTHER
Contextos sobrepostos ou corrigidos
Sempre usar os dados já reconciliados do banco.
22. Testes
22.1 Testes unitários
Criar:
DailySummaryCalculatorTest
DailyTimelineBuilderTest
PlaceVisitBuilderTest
DailyMapBuilderTest
ReplaySequenceBuilderTest
DiaryRepositoryTest

Exemplos
DailySummaryCalculatorTest
- soma tempos corretamente
- recorta evento na meia-noite
- trata evento sem endedAt
PlaceVisitBuilderTest
- cria visita ao chegar em lugar conhecido
- ignora commuting puro como lugar
- separa duas visitas ao mesmo lugar no mesmo dia
DailyMapBuilderTest
- cria nós na ordem correta
- cria edges entre visitas
- não quebra com lugar repetido
ReplaySequenceBuilderTest
- ativa nó correto
- destaca timeline correta
- termina no estado final
22.2 Testes visuais
No Developer Lab, adicionar preview do diário.
Criar:
Diary Lab

Permitir:
- carregar dia fake
- ver mapa
- testar replay
- clicar em nós
23. Exemplo de estrutura de arquivos
engine/diary/
├── DailySummaryCalculator.kt
├── DailyTimelineBuilder.kt
├── PlaceVisitBuilder.kt
├── DailyMapBuilder.kt
├── ReplaySequenceBuilder.kt
├── DiaryAssembler.kt
└── DiaryFixtures.kt

presentation/screens/diary/
├── DiaryScreen.kt
├── DiaryViewModel.kt
├── DiaryUiState.kt
├── DiaryTopBar.kt
├── DiarySummaryCards.kt
├── DiaryMapCard.kt
├── DiaryTimelineList.kt
├── PlaceDetailBottomSheet.kt
└── DiaryReplayControls.kt

pixel/diary/
├── DiaryMapRenderer.kt
├── DiaryMapLayoutEngine.kt
├── DiaryMapIcons.kt
├── DiaryRoutePainter.kt
├── DiaryReplayAnimator.kt
└── DiaryMapPreview.kt

24. Definition of Done do módulo Diário
Considerarei esse módulo pronto quando:
- [ ] abrir Hoje, Ontem e uma data escolhida
- [ ] mostrar resumo por categoria
- [ ] mostrar timeline mesclada do usuário + Hoodie
- [ ] montar mapa simbólico corretamente
- [ ] destacar lugares no mapa
- [ ] abrir detalhe de lugar ao toque
- [ ] executar replay do dia
- [ ] sincronizar replay com timeline
- [ ] manter performance boa
- [ ] funcionar sem internet
- [ ] não exigir GPS contínuo
- [ ] possuir testes unitários principais
25. Ordem que eu recomendo executar
Sprint 1
- modelos
- repositório
- resumo
- timeline
- visitas
Sprint 2
- mapa lógico
- layout simples
- tela Diário funcional
Sprint 3
- mapa pixel art bonito
- clique nos estabelecimentos
- detalhe do lugar
Sprint 4
- replay
- sincronização com timeline
- controles
Sprint 5
- polimento
- testes
- hardening
26. Resultado final esperado
Ao final, o Hoodie terá uma nova funcionalidade onde o usuário abre uma data e vê:
Resumo
🏠 Casa: 4h32
🏢 Trabalho: 8h11
🚶 Transporte: 1h27
🍽️ Almoço: 52min
🏋️ Academia: 48min

Timeline
07:12  Acordou em casa
08:03  Saiu de casa
08:49  Chegou ao trabalho
08:55  Hoodie começou a trabalhar
12:16  Saiu para almoço
12:20  Hoodie foi almoçar
13:02  Voltou ao trabalho
18:07  Saiu do trabalho
18:51  Chegou em casa
19:10  Hoodie jogou videogame

Mapa
Casa ─── Trabalho ─── Restaurante ─── Trabalho ─── Casa

em um cenário pixel art, com o Hoodie se movendo no replay.
Se você quiser, no próximo passo eu posso fazer a continuação já no formato de plano técnico de implementação por arquivos e classes, dizendo exatamente:
- quais arquivos criar
- quais arquivos alterar no projeto atual
- e em que ordem codar cada parte.