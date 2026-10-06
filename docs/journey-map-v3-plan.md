# Plano técnico — Jornada do Dia 3.0: Overworld em capítulos + Relógio do dia

## 1. Objetivo

Substituir o mapa da Jornada por duas visualizações complementares do mesmo dia:

| Botão | Visualização | Pergunta que responde | Tamanho |
|---|---|---|---|
| **[JORNADA]** | Overworld de RPG em serpentina, dividido em capítulos (manhã, tarde e noite) quando o dia é cheio | "Por onde passei e em que ordem?" | Previsível: até 3 linhas sem capítulos, ou 3 blocos limitados |
| **[RELÓGIO]** | Anel de 24 h com arcos de permanência e de deslocamento | "Onde gastei meu tempo?" | Fixo |

O botão **[MAPA ANTIGO]** sai do seletor. O mapa clássico (`DiaryMapRenderer`) continua acessível só no Diary Lab durante uma versão e depois é removido (ver seção 12).

### Princípios mantidos

- O mapa mostra **a ordem do dia, não a geografia**. Não há trilha GPS.
- **Cada visita é uma parada própria**. Retornos continuam separados, com o selo "2", "3"…
- O mapa funciona **offline** e é **determinístico**: mesma data e mesmos dados geram os mesmos pixels (seed por data).
- Os layouts e planejadores são **regras puras**, sem Android e 100% testáveis em JVM.
- Pixel art em **escala inteira, sem filtro**, com a Press Start 2P nos rótulos.

## 2. Visão funcional

### 2.1 Jornada (overworld)

**Dia comum** (até o limite configurável, padrão de 9 paradas): um único mapa em serpentina com **3 paradas por linha**. A linha 1 vai da esquerda para a direita, a linha 2 da direita para a esquerda, e assim por diante. As viradas entre linhas são curvas pela borda do mapa.

**Dia cheio** (acima do limite): três **capítulos**, Manhã, Tarde e Noite. Cada capítulo é um painel com moldura de HUD.

- O capítulo **aberto** mostra a serpentina com as paradas daquele período.
- Os capítulos **fechados** mostram um resumo: nome, quantidade de paradas, tempo total e uma fileira de mini ícones de bioma.
- Durante o replay, o capítulo do horário atual **abre sozinho**. Com o replay pausado, o usuário toca em um capítulo para abri-lo.
- Na fronteira entre capítulos, a trilha sai por um **portal** (seta ou degrau na borda inferior) e entra no capítulo seguinte pela borda superior.

**Cada local é um bioma ou construção:**

| Tipo de nó (`DiaryMapNodeType` / `PlaceType`) | Construção no overworld | Detalhes animados |
|---|---|---|
| `HOME` | Casinha com telhado vermelho e chaminé | Fumaça na chaminé, janela acesa à noite |
| `WORK` | Prédio comercial em forma de castelo: torre de vidro com ameias no topo | Bandeira tremulando, janelas acendendo em sequência |
| `GYM` | Templo com colunas e frontão | Chama ou tocha na entrada |
| `RESTAURANT` (almoço) | Taverna com placa de caneca | Placa balançando, vapor na porta |
| Café / lanche | Cabana pequena com toldo | Xícara fumegando |
| `SCHOOL` | Torre do mago / biblioteca | Página voando |
| `MARKET` | Feira com barracas listradas | Toldo balançando |
| `LEISURE` | Coreto no bosque / parque | Folhas e passarinhos |
| `FAMILY` | Chalé com fogueira | Faíscas da fogueira |
| `OTHER` / desconhecido | Acampamento com placa "?" | Bandeirinha |
| Agrupamento de paradas rápidas | Pilha de pedras / marco de estrada "×k" | — |

**Trilhas por meio de transporte**, mantendo a semântica atual:

| Meio | Trilha no overworld | Padrão (além da cor) |
|---|---|---|
| Caminhada | Trilha de terra | Pontilhado verde |
| Bicicleta | Trilha batida | Tracejado amarelo |
| Carro | Estrada de pedra | Faixa dupla cinza |
| Ônibus | Estrada com marcos de parada | Azul com marcos a cada N px |
| Trem / metrô | Trilhos | Dormentes roxo/ciano |
| Desconhecido / outro | Trilha neutra | Pontilhado bege |

Cada meio tem um **padrão próprio**, além da cor, por acessibilidade para daltonismo. O rastro já percorrido fica **dourado**.

**Plaquinha da parada:** uma placa de madeira com o nome e o horário de chegada, sempre do lado de dentro da serpentina. O selo "2", "3"… aparece em retornos.

### 2.2 Relógio do dia

- Um anel de 24 h, com 00 h no topo e sentido horário: um caminho de pedra em volta de uma praça com relógio de sol, na mesma paleta do overworld.
- **Permanências** viram arcos grossos na cor do tipo de lugar. Arcos longos ganham um mini ícone do bioma e um rótulo.
- **Deslocamentos** viram arcos finos com o padrão do meio de transporte.
- **Paradas curtas**: abaixo de `CLOCK_MIN_LABEL_DEG` não ganham rótulo; abaixo de `CLOCK_MIN_ARC_DEG` viram uma **marca de tique**, e paradas consecutivas são agrupadas em um único tique "×k".
- O **centro** mostra o lugar atual (ou "indo para X"), a hora e "parada k de n".
- No replay, um ponteiro e o Hoodie percorrem o anel. A parte futura fica dessaturada.
- Tocar em um arco abre o **mesmo detalhe da parada** da Jornada. Tocar em um tique agrupado abre a lista das paradas rápidas.

### 2.3 Comportamentos compartilhados

- O **replay** (`ReplayUiState`) é único: trocar de visualização mantém a hora, a velocidade e o estado de play ou pausa.
- A **visualização escolhida** fica salva no DataStore e o Diário reabre nela.
- O **detalhe da parada** continua o mesmo.

## 3. Regras de negócio

### 3.1 Um mapa só ou capítulos

```
se paradas.size <= JOURNEY_SINGLE_MAP_MAX_STOPS (padrão 9) → SINGLE
senão → CHAPTERS
```

### 3.2 Fronteiras dos períodos

| Capítulo | Intervalo (hora local) |
|---|---|
| Manhã | 00:00 – 11:59 (inclui a madrugada) |
| Tarde | 12:00 – 17:59 |
| Noite | 18:00 – 23:59 |

As fronteiras ficam em `HoodieConfig` (`JOURNEY_AFTERNOON_START`, `JOURNEY_NIGHT_START`) e são calculadas com `ZonedDateTime` (horário de verão seguro).

### 3.3 Visitas que atravessam períodos

- A visita pertence ao capítulo da sua **chegada**.
- Se continua em andamento no início do capítulo seguinte, esse capítulo começa com um **nó fantasma** ("continua: Trabalho"), semitransparente, sem selo, que não conta no total.
- Período sem chegada nem visita em andamento: capítulo fechado com "Sem paradas", sem expandir.
- Visita que atravessa a meia-noite é recortada no dia selecionado (regra do Diário).

### 3.4 Agrupamento de paradas rápidas

- Capítulo acima de `JOURNEY_CHAPTER_MAX_STOPS` (12): visitas **consecutivas** menores que `JOURNEY_QUICK_STOP_MS` (10 min) viram um marco "×k".
- Nunca agrupa a parada atual do replay nem a selecionada (o grupo é quebrado em volta).
- No replay o Hoodie passa pelo marco e o detalhe lista as paradas internas.
- Se ainda exceder, a serpentina ganha linhas extras (pior caso, registrado em log de debug).

### 3.5 Capítulo ativo

```
ativo = replay PLAYING → capítulo do timestamp do replay
        escolha manual → escolha manual
        data é hoje → capítulo da hora atual
        senão → capítulo com mais tempo de permanência
```

A escolha manual é limpa ao trocar de data ou ao dar play.

## 4. Arquitetura

```
DailyDiary (+ movements)
   ▼
JourneyMapAssembler ──► JourneyMapData
   ├──► JourneyChapterPlanner ──► JourneyPlan (SINGLE | CHAPTERS, fantasmas, grupos)
   │       ▼
   │    SerpentineLayoutEngine + JourneyPathBuilder ──► OverworldLayout por capítulo
   │       ▼
   │    OverworldBiomeCatalog ──► pintores por tipo/estado
   │       ▼
   │    JourneyReplayAssembler ──► capítulo ativo + posição do Hoodie (atravessa portais)
   │       ▼
   │    OverworldJourneyRenderer (camadas) ──► JourneyChaptersView / OverworldJourneyView
   └──► DayClockAssembler ──► DayClockData ──► DayClockRenderer ──► DayClockView

DiaryViewModel ──► DiaryMapMode { JOURNEY, CLOCK } (DataStore) + ReplayUiState compartilhado
```

## 5. Renderização

| # | Camada | Cache | Atualiza |
|---|---|---|---|
| 1 | Chão: grama com dithering, água e relevo por seed | Por capítulo/dia | Nunca |
| 2 | Decoração estática sem colidir com trilhas e construções | Junto com a 1 | Nunca |
| 3 | Trilhas por meio de transporte (estado "futuro") | Junto com a 1 | Nunca |
| 4 | Rastro dourado | — | Por quadro de replay |
| 5 | Construções e placas (futura/visitada/atual/fantasma) | — | Na troca de estado |
| 6 | Vida: fumaça, bandeira, chama, folhas, passarinhos | — | ~2,5 FPS parado, ~10 FPS no replay |
| 7 | Luz por minuto: janelas, postes, vaga-lumes | — | Por minuto |
| 8 | Hoodie, seta "você está aqui" e moldura do capítulo | — | Por quadro |

Só o capítulo aberto anima; os fechados são Compose puro. Relógio: canvas fixo 240×240.

## 7. Configuração (`HoodieConfig`)

`DIARY_JOURNEY_MAP_V3`, `DIARY_CLOCK_VIEW`, `JOURNEY_SINGLE_MAP_MAX_STOPS = 9`, `JOURNEY_CHAPTER_MAX_STOPS = 12`,
`JOURNEY_QUICK_STOP_MS = 10 min`, `JOURNEY_AFTERNOON_START = 12:00`, `JOURNEY_NIGHT_START = 18:00`,
`CLOCK_MIN_ARC_DEG = 2`, `CLOCK_MIN_LABEL_DEG = 25`. Preferência `diary_map_mode` (padrão `JOURNEY`).

## 8. Testes

JVM: `JourneyChapterPlannerTest`, `QuickStopClustererTest`, `SerpentineLayoutEngineTest`, `JourneyPathBuilderTest`,
`OverworldBiomeCatalogTest`, `DayClockAssemblerTest`, `JourneyReplayAssemblerTest`.
Goldens: `journey-map-v3.sha256` e `DayClockGoldenTest` (PNGs em `app/build/pixel-preview/journey-v3/`),
regravados com `JOURNEY_GOLDEN_RECORD=1` depois de revisar os PNGs.
Instrumentados: seletor persistente, capítulo abre ao tocar e segue o replay, troca de visualização mantém o
replay, toque em construção/arco/tique abre o detalhe, TalkBack na ordem do dia.

## 10. Fases

1. Planejamento puro · 2. Layout em serpentina · 3. Arte do overworld · 4. Renderer e capítulos na tela ·
5. Replay integrado · 6. Relógio do dia · 7. Seletor, persistência e transição · 8. Polimento e validação.

## 12. Transição

1. v0.3-dev: `DIARY_JOURNEY_MAP_V3 = true`; seletor [JORNADA] [RELÓGIO]; mapa clássico só no Diary Lab.
2. Versão seguinte: remover `DiaryMapRenderer` e a camada clássica de `pixel/diary` e seus goldens.
3. A Jornada 2.0 (zigue-zague) fica atrás de `DIARY_JOURNEY_MAP_V3 = false` até a Fase 8 e depois é removida.
4. Sem mudança de banco.
