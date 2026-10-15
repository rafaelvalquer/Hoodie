# Comparação Android intermediária

Duas rodadas completas aprovadas no mesmo emulador API 37: referência em 403,516 s e composição/worker/continuidade em 405,247 s. Cada rodada tem 11 cenas, 99 amostras e confirmação real de toque em cada cena. Sem build ou benchmark JVM concorrente.

| Cena | FPS efetivo antes | FPS efetivo depois | Render P95 antes/depois (ms) | Intervalo P95 antes/depois (ms) |
|---|---:|---:|---:|---:|
| OFFICE | 29.53 | 29.85 | 23.00 / 19.76 | 48.88 / 46.74 |
| RESTAURANT | 30.17 | 27.19 | 7.56 / 22.83 | 39.13 / 62.04 |
| SHOPPING | 30.14 | 30.10 | 7.85 / 7.94 | 38.78 / 40.65 |
| STORE | 29.94 | 29.97 | 2.33 / 8.42 | 36.22 / 41.48 |
| LEISURE | 30.38 | 30.08 | 10.92 / 8.82 | 42.23 / 41.06 |
| BAKERY | 30.20 | 30.14 | 8.68 / 9.00 | 41.41 / 40.38 |
| MALL | 30.25 | 29.97 | 9.52 / 10.62 | 40.94 / 41.11 |
| BEACH | 30.27 | 29.96 | 8.62 / 9.63 | 41.02 / 41.24 |
| CINEMA | 30.26 | 30.13 | 6.36 / 10.78 | 38.76 / 41.14 |
| PHARMACY | 30.22 | 30.11 | 8.96 / 8.26 | 41.26 / 39.94 |
| HOTEL | 30.16 | 30.16 | 9.03 / 9.18 | 42.01 / 41.05 |

Estas são janelas móveis de até 180 frames; a etapa `steady` original tem 150 frames acumulados e ainda inclui o início frio. Não é uma janela isolada de aquecimento. Os dados brutos estão em `android-baseline/` e `android-optimized/`.

- Todas as 11 cenas complexas passam a desenhar em `DefaultDispatcher`, publicando o bitmap no caminho da UI com um único mutex e sem fila.
- Nas seis cenas de estabelecimento e no Passeio, a transição de luz preserva a sessão e o elenco inicial. A carga noturna é deliberadamente maior em parte dos casos; não comparar esse custo como se a quantidade de atores fosse igual.
- O Restaurante apresentou piora no início desta rodada (27,19 FPS efetivos, 29,17 medianos), retornando a aproximadamente 30 FPS posteriormente. Foi identificada reconstrução repetida do estado/objetos do cliente. A homologação desse ponto exige a medição isolada com 180 quadros de aquecimento, preparada posteriormente.
- A meta proposta de intervalo P95 de 40 ms ainda não está comprovada em todas as cenas no emulador. Não declarar aprovação integral de H-07 com base nesta tabela.

O cache de estado do Restaurante foi implementado depois desta rodada. A redução de alocação JVM foi de aproximadamente 39.886 para 25.801 bytes/quadro, com 9/9 checksums da cena iguais à rodada anterior. As demais verificações de H-07 e a homologação integrada permanecem no acompanhamento da sprint.
