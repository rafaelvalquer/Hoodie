# Desempenho da Loja

Medições de `StorePerformanceDeviceTest` em 13/10/2026, Pixel 8 AVD / Android 17,
build debug. São resultados de emulador; não substituem homologação física.

## Loop real do Compose

Após 120 frames de aquecimento, foram medidos outros 120 frames:

| Métrica | Primeira janela | Janela anterior | Após continuidade da Loja |
| --- | ---: | ---: | ---: |
| Cadência real | 30,04 FPS | 30,08 FPS | 30,17 FPS |
| Renderização P50 | 1,91 ms | 2,06 ms | 2,95 ms |
| Renderização P95 | 5,62 ms | 8,08 ms | 8,16 ms |
| Renderização P99 | 8,43 ms | 15,57 ms | 11,86 ms |
| Desenho do comprador P95 | não separado | 5,41 ms | 5,92 ms |
| Atualização do bitmap P95 | 0,42 ms | 0,24 ms | 0,33 ms |
| Heap Java no final da janela | 48.712.256 bytes | 49.711.680 bytes | 44.026.432 bytes |
| GC acumulado no processo | 5 / 139 ms | 5 / 221 ms | 5 / 152 ms |
| Thread de renderização | main | main | main |

A cadência ficou próxima de 30 FPS e o P95 de renderização abaixo de 33 ms
nas três janelas. O tempo de renderização não inclui a atualização do bitmap, medida
separadamente. Não representa a latência completa de apresentação pelo sistema.
A primeira medição não separava corretamente o desenho do comprador; esse
contador foi corrigido e validado na última execução. São execuções separadas
do emulador, sem comparação controlada para atribuir diferenças à instrumentação.
Os contadores de GC vêm de `Debug.getRuntimeStat` e são acumulados no processo;
o reset da janela de frames não os zera. Não atribuir esse total à janela medida.

## Memória — 30 minutos virtuais após aquecimento

Foram preenchidos os caches de viagens e aquecidos os sprites antes de renderizar
54.600 frames adicionais. A última execução medida levou 24,96 segundos reais
(as anteriores levaram 30,01 e 26,27 segundos). Portanto,
este teste não equivale a usar o app por 30 minutos reais.

As amostras abaixo foram coletadas após GC explícito, apenas no teste diagnóstico.
Valores em MB decimais (bytes / 1.000.000):

| Amostra | Heap Java retido | Heap nativo alocado |
| --- | ---: | ---: |
| Após aquecimento | 35,67 | 18,24 |
| +5 min virtuais | 35,79 | 18,25 |
| +10 min virtuais | 35,89 | 13,40 |
| +15 min virtuais | 35,90 | 13,40 |
| +20 min virtuais | 35,72 | 13,40 |
| +25 min virtuais | 36,15 | 13,40 |
| +30 min virtuais | 36,00 | 13,40 |

O heap Java oscilou numa faixa de 0,48 MB, sem crescimento contínuo nas sete
amostras. O cache de sprites permaneceu em 96/96 entradas. O cache de viagens
tem limite de 32, também coberto pela regressão unitária de 24 horas virtuais.
Essas evidências não comprovam ausência de vazamentos em uso prolongado real,
nem incluem toda a memória gráfica ou PSS do processo.

## Fontes

Resultados e logs em
`.gradle-workspace/stabilization-20261012/outputs/androidTest-results/connected/debug/`,
as três execuções concluíram os dois testes de desempenho com sucesso. A última
inclui também os três testes de navegação da Loja. Execuções instrumentadas
seguintes podem substituir esse diretório; os valores acima preservam as medições.

## Execução na suíte instrumentada completa

Na execução completa posterior, o mesmo teste mediu **30,06 FPS**, renderização
P95 **7,27 ms**, bitmap P95 **0,54 ms** e comprador P95 **1,56 ms**. O processo
já havia executado outros testes: heap final 55.005.824 bytes e GC acumulado
613 / 21.411 ms. Esses contadores de processo não são o custo de GC dos 120 frames.

Os 54.600 frames do probe levaram 44,10 s reais. Heap Java retido, em bytes:
`[43930304, 44012224, 44110528, 44126912, 43963072, 44372672, 44192448]`.
Faixa de 43,93 a 44,37 MB, com quedas entre amostras e cache em 96/96 entradas.
Valores observados preservados em
`.gradle-workspace/stabilization-20261013-full-instrumentation/observed-results.json`.
Os XMLs e logs originais foram substituídos pelo Gradle na reexecução direcionada;
esse JSON é um registro dos valores lidos, não uma cópia dos arquivos originais.
As diferenças entre execuções não são uma comparação controlada de performance.

## Quadros do Android na Home real após force-stop

Foi instalada a aplicação debug e aberta a Loja pela correção da Home, com um
cadastro manual fictício e sem permissão de localização. Após force-stop e
abertura COLD em novo processo, `dumpsys gfxinfo com.hoodie.app reset` iniciou
uma janela sem interação. O relatório final de 1.998 frames apresentou:

- P50: 17 ms; P90: 19 ms; **P95: 26 ms**; P99: 36 ms.
- GPU P95: 16 ms; GPU P99: 22 ms.
- Janky frames: **1.914 / 95,80%**; classificação legacy: **143 / 7,16%**.
- Um missed vsync, 109 registros de alta latência de input, 92 slow UI thread,
  quatro slow bitmap uploads e 1.779 slow issue draw commands.

P95 ficou abaixo da meta inicial de 33 ms, mas a taxa de jank reportada pelo
sistema é alta. Não interpretar a cadência de 30 FPS ou o custo isolado do
renderizador como prova de ausência de travamentos visíveis. A causa desses
flags e o comportamento em aparelho físico/uso prolongado continuam pendentes.
O relatório bruto está em `.gradle-workspace/hoodie-store-gfxinfo-after-restart.txt`.
