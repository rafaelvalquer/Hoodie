# H-07 — referência JVM anterior às otimizações

Executado em 15/10/2026, Windows 11, JDK 21.0.12.1, 12 processadores lógicos. Benchmark aprovado: 11 cenas, 99 medições de estágio, reação efetivamente iniciada em cada cena. Fonte e ambiente identificados em `baseline-verified/environment.txt`; dados integrais em `baseline-verified/frames.csv`.

Estes custos medem o renderer JVM sem cadenciamento. Não incluem bitmap Android, Compose ou GPU e não comprovam as metas de FPS no aparelho. O estágio frio tem cache local do renderer frio; caches de sprites do processo podem estar aquecidos pelas cenas anteriores. Alocações incluem a construção dos frames e o checksum. Os saltos para histórias em 90 s e 240 s são deliberados e devem ser comparados separadamente da reprodução contínua.

| Cena | P95 contínuo (ms) | P95 noite (ms) | Alocação contínua por quadro (bytes) | Reações iniciadas no estágio de toque |
|---|---:|---:|---:|---:|
| OFFICE | 1.067 | 5.020 | 14159 | 3 |
| RESTAURANT | 1.015 | 1.576 | 42250 | 3 |
| SHOPPING | 0.590 | 0.680 | 17253 | 3 |
| STORE | 0.266 | 1.008 | 11055 | 3 |
| LEISURE | 0.872 | 3.726 | 29670 | 3 |
| BAKERY | 0.991 | 1.482 | 41900 | 3 |
| MALL | 0.882 | 1.490 | 56310 | 3 |
| BEACH | 0.587 | 1.115 | 28987 | 3 |
| CINEMA | 0.672 | 1.194 | 26031 | 3 |
| PHARMACY | 0.718 | 1.620 | 36356 | 3 |
| HOTEL | 0.771 | 0.990 | 57270 | 3 |

Próximas verificações: instrumentação detalhada por cena, referência Android com apresentação de bitmap e intervalo de frames, continuidade das sessões, comparação após otimização e regressão visual. Nenhuma meta de homologação Android está marcada como atingida por esta referência.

Reprodução: executar `:app:testDebugUnitTest --tests '*ScenePerformanceBenchmarkTest'` com `HOODIE_BENCHMARK_LABEL` específico, usando o JDK e argumentos de Gradle registrados em `gradle.log` e no acompanhamento da sprint. Manter os mesmos fontes do benchmark, hardware, cargas e configuração entre referência e comparação.
