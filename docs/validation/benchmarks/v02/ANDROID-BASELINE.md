# Referência Android antes das otimizações

Executada em 15/10/2026 no emulador `sdk_gphone16k_x86_64`, API 37. Instrumentação aprovada: **11 cenas, 99 amostras de estágio, toque efetivamente iniciado em cada cena**, 403,516 s. Não houve testes JVM ou builds de Gradle concorrendo com esta medição.

Dados completos em [frames.csv](android-baseline/frames.csv), [ambiente](android-baseline/environment.txt) e [log](android-baseline/instrumentation.log). A cena HOME aparece nos estágios de saída de cada caso. As linhas mantêm a ordem das 11 cenas e dos nove estágios.

| Cena | FPS efetivo | FPS mediano | Render P95 (ms) | Intervalo P95 (ms) |
|---|---:|---:|---:|---:|
| OFFICE | 29.53 | 30.46 | 23.00 | 48.88 |
| RESTAURANT | 30.17 | 29.90 | 7.56 | 39.13 |
| SHOPPING | 30.14 | 30.09 | 7.85 | 38.78 |
| STORE | 29.94 | 30.07 | 2.33 | 36.22 |
| LEISURE | 30.38 | 30.18 | 10.92 | 42.23 |
| BAKERY | 30.20 | 30.17 | 8.68 | 41.41 |
| MALL | 30.25 | 29.97 | 9.52 | 40.94 |
| BEACH | 30.27 | 30.08 | 8.62 | 41.02 |
| CINEMA | 30.26 | 30.20 | 6.36 | 38.76 |
| PHARMACY | 30.22 | 30.11 | 8.96 | 41.26 |
| HOTEL | 30.16 | 30.14 | 9.03 | 42.01 |

As métricas são janelas móveis de até 180 quadros. Os estágios iniciais ainda incluem amostras frias; esta tabela não homologa isoladamente todas as metas de aquecimento. A referência mostra cadência próxima de 30 FPS, mas diversos intervalos P95 ultrapassam a meta proposta de 40 ms. É necessário comparar após as otimizações e executar os gates finais.

Constatações:

- O Escritório desenha em worker; as demais cenas da referência desenham na thread `main`.
- Padaria, Shopping, Praia, Cinema, Farmácia, Hotel e Passeio recriam sessões na troca de período noturno. A redução instantânea do elenco decorre da chave que inclui noite. O reuso precisa preservar o roteiro de uma visita em andamento.
- O estágio de toque confirma a animação real; ativar a máscara no início da preparação troca o renderer existente. A criação dessa preparação é distinta da reação já iniciada.
- Contadores ART são globais do processo desde a primeira amostra da cena; não atribuem toda alocação exclusivamente aos NPCs. Campo vazio significa indisponível.

APKs congelados em `build/validation/android-baseline-apks/`:

- debug SHA-256 `97071a09215b2f22ae1e3108b59c17497b629b1baa9c7f26d5c4ae4a6b2df947`;
- testes SHA-256 `0baa536fb8edb9e378e5ee9eba5b00295b6f22db8a04fc085f58643aae775962`.

Comando instrumentado: `adb -s emulator-5554 shell am instrument -w -r -e class com.hoodie.app.pixel.ScenePerformanceDeviceTest -e benchmarkLabel baseline-instrumented com.hoodie.app.test/androidx.test.runner.AndroidJUnitRunner`.
