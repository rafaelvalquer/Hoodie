# Regressão visual das telas

`ScreenGoldenMatrixTest` usa o conteúdo de produção de Home, Diário, Digital, Onboarding, Ajustes e PlacePicker. ViewModels, banco, sensores e busca de endereços não participam dos fixtures. Os testes de interação continuam separados.

## Matriz

- 360×640, 360×800 e 411×891, em densidade lógica 1.
- Escala de fonte 1.0 e 1.3.
- Normal, carregamento, erro, vazio e completo.
- Total: 180 casos principais. Os estados completos também capturam o fim da rolagem (36 imagens); o Diário completo inclui o mapa (seis imagens).

Data, fuso, tempo de animação e semente aleatória são fixos. A arte passa pelos renderers reais. O PlacePicker usa o MapView real em modo offline, com chave de cache própria, preservando pino, raio, controles e atribuição. Essas capturas não avaliam os tiles do serviço externo.

## Gravar e conferir

O modo normal compara cada pixel com os PNGs em `app/src/androidTest/assets/goldens/screens`. Referência ausente ou diferença faz o teste falhar. Em caso de diferença, imagens `actual` e `diff` são gravadas no armazenamento externo do aplicativo.

Para gravar, executar `connectedDebugAndroidTest` com os argumentos do runner:

```text
class=com.hoodie.app.ui.golden.ScreenGoldenMatrixTest
recordGoldens=true
```

O argumento opcional `goldenCasesRegex` seleciona nomes com uma expressão regular, por exemplo `home_normal_360x640_f100`. Sem filtro, executa a matriz inteira.

Os PNGs são gravados em `/sdcard/Android/data/com.hoodie.app/files/goldens/screens`. Copiar primeiro para `app/build`, inspecionar o layout e somente então colocar as referências nos assets. Repetir sem `recordGoldens=true` confirma a comparação. Não aceitar uma atualização de referências apenas porque a gravação terminou.

Ambiente de referência atual: Hoodie_API34, Google APIs Android 14/API 34 r14 x86_64, resolução física 1080×2400, densidade física 420, GPU SwiftShader e renderer HWUI skiagl. O CI usa a mesma API, resolução, densidade e renderer. O enquadramento e a escala de fonte são definidos pelo teste. Barras do sistema ficam ocultas.

## Relógio do Dia 2.0

O mostrador tem regressão própria na JVM: `DayClockGoldenTest` renderiza 8 estados (dia vazio, manhã com uma parada,
dia completo, parada antiga selecionada, deslocamento selecionado, dia passado, muitos trechos curtos e dia de horário
de verão) e compara SHA-256 com `app/src/test/resources/day-clock-v1.sha256`. PNGs em `app/build/pixel-preview/day-clock/`;
regravar com `DAY_CLOCK_GOLDEN_RECORD=1` só depois de inspecionar. O mesmo teste garante que todo pixel pertence à
`DayClockPalette`. Na tela, `DayClockPanelUiTest` cobre toque no anel e no centro, AGORA, ausência da lista (a linha do tempo cumpre esse papel), troca JORNADA/RELÓGIO e fonte 1,3×
sem estourar o centro; O mostrador tem 312×312 px lógicos (PNGs exportados em escala 2). `exportReviewScreenshots` grava manhã, tarde, noite e fonte 1,3× em
`files/day-clock-review/` do aparelho para revisão. Rodar com `am instrument` (não `connectedDebugAndroidTest`).

## Estado atual

As 222 referências revisadas continuam em `app/src/androidTest/assets/goldens/screens`. O manifesto `docs/golden-reference-manifest.json` registra dimensões, SHA-256 e ambiente. As capturas foram revistas e aceitas quando gravadas; isso não garante que correspondam à UI atual.

Execução estrita API 34 em 03/10/2026 (`verifyGoldens=true`): 180 casos executados, 18 aprovados e 162 falharam; nenhum foi ignorado. Distribuição: Home 6/30 aprovados, Diário 0/30, Digital 12/30, Onboarding 0/30, Ajustes 0/30 e PlacePicker 0/30. Os PNGs `actual/diff` ficam no armazenamento externo do AVD; amostras da Home e Digital também foram copiadas para `app/build`. As referências não foram substituídas automaticamente. A matriz geral precisa de análise e revisão visual antes de atualizar qualquer baseline.

Os dois testes instrumentados novos do catálogo físico passaram na API 34: “O que estou fazendo?” e o diálogo real “Novo lugar” expõem os mesmos dez nomes. O teste de teclado passou isoladamente depois de recolher a shade de notificações que tinha tomado o foco da janela. A rodada Android anterior (261 casos) teve apenas esse timeout de foco; a repetição isolada passou.

## Polimento visual (07/10/2026)

Branch `feature/ui-polish`: entrelinha centralizada em todos os estilos, espaço padrão rótulo/valor, ícones pixel no lugar dos emojis da interface fixa, botão "Minha localização" no topo do mapa, esqueletos de carregamento, estado de erro com o Hoodie e cena do Home com a própria altura.

Comparação antes (main `a926566c`) × depois no mesmo aparelho (Pixel_8, API 37.1), matriz completa em modo captura: 180 casos executados nas duas versões e 180 imagens diferentes (esperado: o estilo de texto mudou em todas as telas). Inspeção visual: sem rótulos sobrepostos em Ajustes, Digital e Diário; crédito do OpenStreetMap livre; erro e carregamento sem tela vazia.

As 222 referências NÃO foram regravadas. Elas só valem no ambiente de referência (Hoodie_API34), que não estava disponível nesta máquina: regravar lá com `recordGoldens=true`, inspecionar e só então atualizar os assets e o manifesto. Os fixtures de screenshot desligam o pulso do esqueleto (`LocalSkeletonPulse = false`) para a captura ser determinística.
