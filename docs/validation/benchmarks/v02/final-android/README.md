# Medição Android final da Fase 5

Executada em 15/10/2026 no emulador Pixel 8 virtual (Android 17 / API 37, x86_64), com o teste `ScenePerformanceDeviceTest` e o caminho de apresentação do Canvas de produção. O caso instrumentado terminou aprovado em 10m59s e percorreu as 11 cenas. Esta rodada final usou 180 quadros de aquecimento e 180 quadros por estágio, seed de dia 20026, seed aleatória 26, variante 0 e lugar 42.

`scene-performance-final-warmed.csv` contém as 88 linhas publicadas pelo teste para oito estágios de cada cena (frio, aquecimento, contínuo, história, noite, retorno, pausa segura e toque). O teste também mediu a saída e confirmou nove estágios por cena, mas essa etapa não publica uma linha `SCENE_DEVICE`. A captura em streaming preservou as 88 linhas completas sem depender da retenção circular do logcat.

No estágio contínuo, o FPS efetivo ficou entre 19,87 e 29,80 e o FPS mediano entre 22,06 e 30,19. O render P95 ficou até 25 ms em 9/11 cenas; Escritório (40,62 ms) e Shopping (26,13 ms) excederam a meta. O intervalo P95 excedeu 40 ms nas 11 cenas (53,32–82,54 ms). A execução confirma que as cenas publicam quadros e o teste passa, mas H-07 não atende ainda aos limites de desempenho do plano. Esses valores vieram de emulador x86_64 e precisam ser considerados dentro dessa condição de medição.

O CSV contém as 29 colunas de P50/P95/P99, intervalos, etapas internas, memória e GC para os oito estágios publicados. Os logs brutos integrais da execução anterior permanecem em `device-test-logcat.txt` e as métricas da rodada anterior em `metrics.csv`.
