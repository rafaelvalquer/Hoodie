# Relógio do Dia 2.0 — andamento

| Fase | Estado | Onde |
|---|---|---|
| 0. Levantamento e flag | feito | `HoodieConfig.DIARY_DAY_CLOCK_V2`; relógio anterior renomeado para `DayClockLegacy*` e mantido no Diary Lab |
| 1. Modelo e assembler | feito | `domain/diary/clock/DayClockData.kt`, `engine/diary/DayClockAssembler.kt` |
| 2. Geometria | feito | `pixel/diary/clock/DayClockGeometry.kt` (tabelas 312×312 de raio e fração, `K = 3`) |
| 3. Renderer e assets | feito | `DayClockRenderer`, `DayClockPalette`, `ClockIcons`, `PixelDigits`, `ClockHoodieMarker` |
| 4. UI Compose | feito | `presentation/screens/diary/clock/DayClockPanel.kt` |
| 5. Estado e desempenho | feito | `DiaryViewModel` (`dayClock`, `clockSelectedId`, `refreshClock`), `DayClockUiState` |
| 6. Testes | feito | `DayClockAssemblerTest`, `DayClockGeometryTest`, `DayClockAssetsTest`, `DayClockGoldenTest`, `DayClockPanelUiTest` |
| 7. Lab e documentação | feito | `DayClockLab` no Diary Lab, README, `visual-regression.md` |

## Decisões ajustadas ao código real

- **Categorias**: as mesmas dos cards "Seu dia". Cada visita usa o contexto dominante (`ReplaySequence.contexts`),
  com o mesmo agrupamento do `DailySummaryCalculator` (restaurante sem contexto de almoço cai em Outros).
- **TransportProfile** não existe no projeto: os trechos usam `MovementMode`, com as cores de trilha da Jornada.
- **Paleta**: a base é a paleta de 17 cores do Hoodie; como ela não tem verde, roxo nem as cores do céu, o relógio
  também usa cores já validadas da Jornada (biomas, trilhas, dourado) e as cores de período da iluminação.
  `DayClockAssetsTest` proíbe qualquer cor fora dessas fontes.
- **Hoodie**: o próprio marcador da Jornada (`JourneyHoodieMarker`) via `ClockHoodieMarker`, em vez de um clipe do
  AnimationRegistry (os clipes do catálogo são de 48×72). Suas cores entram na paleta fechada.
- **Resolução** (06/10/2026): o mostrador de 104 px ficava pixelado demais (×9 na tela). Passou a pixel art fina de
  312 px (×3), com ícones 15×15, dígitos 5×7, estrelas em "+", plaquinhas com aro e espessuras revistas.
- **Ids**: permanências e trechos entre visitas usam os ids da Jornada (`journey-i`, `journey-seg-i`).

- **Lista removida** (06/10/2026): "Para onde o Hoodie foi" repetia a linha do tempo do Diário, que fica logo abaixo;
  o painel mantém mostrador + barra "Tempo por lugar" e a seleção vem do toque no anel.

## Para virar padrão definitivo

A flag já está ligada (o relógio anterior nunca foi publicado na `main`). Pendente da checagem do plano:
revisão em **aparelho real** de manhã, tarde e noite. Emulador Pixel 8 (API 34) já revisado nas três faixas e
com fonte 1,3×.
