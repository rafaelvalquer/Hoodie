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

### Análise do histórico de quadros

O histórico bruto contém 120 linhas. Uma tem `FrameCompleted` anterior a
`IntendedVsync` e foi excluída da análise de duração. Nos 119 registros válidos
(`Flags = 0`), o intervalo médio de `IntendedVsync` é 33,333332 ms, compatível
com a cadência de 30 FPS. A duração `FrameCompleted - IntendedVsync` tem P95 de
20,67692 ms, sem registros acima de 33 ms nessa amostra final.

`FrameInterval` e `WorkloadTarget` registram aproximadamente 16,67 ms. A diferença
entre esse prazo e a cadência de atualização é uma hipótese para interpretar a
taxa de jank; não demonstra que todos os flags tenham essa causa. A amostra de
119 quadros não substitui o agregado de 1.998 quadros. `DisplayPresentTime` é
zero em todos os registros, portanto não há timestamps de apresentação que
comprovem a cadência efetivamente exibida. A ausência de travamentos visíveis e
a homologação física continuam sem comprovação.

## Permanência de 30 minutos reais na Home

Em 13/10/2026, de 16:35:32 UTC até aproximadamente 17:05:32 UTC, a Loja ficou
aberta no mesmo AVD, sem navegação ou reinstalação. O processo PID 22291 e a
MainActivity em primeiro plano foram observados nas sete coletas. O último
relatório registrou 53.249 quadros, P95 **36 ms** e P99 **73 ms**. O P95 acumulado
chegou a 38 ms na coleta de 25 minutos. A meta inicial de 33 ms não está
comprovada nesta janela prolongada.

Valores em KB, conforme `dumpsys meminfo`:

| Minuto | PSS total | Heap Dalvik alocado | Heap nativo alocado | Bitmaps |
| ---: | ---: | ---: | ---: | ---: |
| 0 | 134.224 | 39.908 | 22.984 | 26 |
| 5 | 132.644 | 42.388 | 23.023 | 26 |
| 10 | 150.652 | 42.852 | 23.047 | 26 |
| 15 | 150.020 | 43.556 | 23.062 | 26 |
| 20 | 141.938 | 43.620 | 23.076 | 26 |
| 25 | 151.758 | 44.692 | 23.132 | 26 |
| 30 | 156.986 | 44.196 | 23.141 | 26 |

Há quedas entre amostras de PSS e no heap Dalvik final; isso não comprova um
platô de memória nem ausência de vazamentos em períodos maiores. O número de
bitmaps ficou constante e o heap nativo alocado variou 157 KB.

**Interferência da coleta:** o script não chamou `System.gc()`, mas
`ActivityThread.dumpMemInfo` solicita `runtime.gc()` no Android. Portanto, esta
janela inclui GC induzido pelo diagnóstico e não pode ser descrita como coleta
sem GC forçado. A chamada consta tanto no SDK Android 37.0 local quanto no
[código AOSP de ActivityThread](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/ActivityThread.java).
Os valores de PSS e heap são obtidos em etapas diferentes do dump e não devem ser
tratados como uma fotografia atômica da memória.

Os sete relatórios de memória, Activity e quadros estão preservados em
`.gradle-workspace/store-real-time-soak-20261013/`, junto de `results.json`.
O metadado inicial `forcedGc=false` foi corrigido após a inspeção da implementação
do Android; os relatórios brutos permanecem intactos. As imagens
`hoodie-store-soak-start.png` e `hoodie-store-soak-end.png`, no mesmo diretório
`.gradle-workspace`, mostram a Loja no início e no fim. Uma janela complementar
usa `/proc/PID/smaps_rollup` para evitar `dumpsys meminfo`.

### Janela complementar com PSS pelo kernel

De 17:06:16 UTC até aproximadamente 17:16:16 UTC, o mesmo PID 22291 permaneceu
na Loja. Foram lidos `Pss` e `Rss` de `/proc/22291/smaps_rollup` com `run-as`, a
cada dois minutos, sem chamar `dumpsys meminfo` nem solicitar GC pelo script.
Isso não desativa a coleta de lixo natural do runtime.

PSS em KB, nos minutos 0/2/4/6/8/10:
`[154651, 143193, 142850, 146530, 143694, 151758]`.
RSS correspondente:
`[297128, 285824, 285488, 289180, 286348, 294428]`.
A amostra final ficou abaixo da inicial, com oscilações intermediárias. Não há
crescimento monotônico nessas seis coletas; isso não prova ausência de vazamentos.

O `gfxinfo` foi resetado antes da janela e lido ao final: **17.971 quadros**,
P50 17 ms, P90 19 ms, **P95 21 ms**, P99 34 ms. Jank: 96,12%; legacy: 3,96%.
A janela atendeu à meta inicial de P95, mas não elimina o resultado de 36 ms da
janela anterior. São janelas consecutivas, sem controle suficiente para atribuir
toda a diferença ao método de memória. O agregado não comprova ausência de
travamentos visíveis ou cadência de apresentação no display.

Relatórios preservados em `.gradle-workspace/store-kernel-soak-20261013/`.

### Trace separado após as janelas

Um `atrace` de 30 segundos, com categorias `gfx view sched dalvik` e marcadores
do app, registrou 896 seções de renderização da Loja no PID 22291. Durações
decorridas dos marcadores, incluindo esperas de escalonamento e overhead do trace:

| Seção | P95 | P99 | Máximo |
| --- | ---: | ---: | ---: |
| Renderização da Loja | 1,163 ms | 4,021 ms | 9,350 ms |
| Atualização do bitmap | 0,116 ms | 0,403 ms | 0,945 ms |
| Desenho da cena | 0,859 ms | 1,861 ms | 8,578 ms |
| Choreographer do app — 1.799 callbacks | 3,851 ms | 13,901 ms | 36,057 ms |

Nessa amostra, nenhuma seção de renderização da Loja ultrapassou 16,67 ms.
Houve um callback do Choreographer acima de 33 ms. O trace não identifica a
renderização da Loja como gargalo nessa janela nem comprova a causa de todos os
flags de jank. Não foi feita uma mudança de thread baseada apenas nesses flags.
O trace bruto e a análise estão em `.gradle-workspace/hoodie-store-baseline-trace.txt`
e `.gradle-workspace/hoodie-store-baseline-trace.analysis.json`.
