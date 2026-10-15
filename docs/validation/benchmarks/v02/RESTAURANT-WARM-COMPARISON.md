# Restaurante — investigação com janela aquecida isolada

Cada rodada foi aprovada no mesmo emulador, com 180 quadros de aquecimento e 180 quadros no trecho contínuo. Nessa etapa, a janela móvel de 180 quadros já não contém a entrada fria. As rodadas foram sequenciais, sem builds concorrentes.

| Variante | FPS efetivo | FPS mediano | Render P95 (ms) | Intervalo P95 (ms) |
|---|---:|---:|---:|---:|
| Referência na Main | 30.00 | 30.41 | 9.52 | 42.24 |
| Cache em worker | 28.65 | 29.49 | 13.80 | 48.80 |
| Cache na Main e cadência | 29.22 | 30.33 | 20.85 | 50.99 |

O worker não trouxe vantagem para o Restaurante neste dispositivo. A implementação conserva essa cena na Main, com cache de estado/slots; as outras dez cenas complexas utilizam worker. O cache reduziu alocações JVM e preservou os 9 checksums da cena. A cadência usa prazos estáveis e descarta slots atrasados, sem enfileirar desenhos antigos.

A variante atual mantém FPS mediano acima de 28 e render P95 abaixo de 25 ms. **A meta proposta de intervalo P95 de 40 ms continua não atingida neste ensaio**; a avaliação completa das fontes finais permanece na Fase 5. Os ensaios apresentam variação de GC/scheduling: não afirmar melhora uniforme de todos os percentis.

Dados e logs individuais: `restaurant-warm/`. A primeira comparação de 11 cenas é preservada separadamente em `ANDROID-COMPARISON.md`.
