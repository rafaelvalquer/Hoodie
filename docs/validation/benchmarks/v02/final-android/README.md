# Evidências Android da Fase 5

O arquivo `scene-performance-final-warmed.csv` registra a execução instrumentada original de 15/10/2026 no emulador Pixel 8 virtual (Android 17 / API 37, x86_64). O teste percorreu as 11 cenas com 180 quadros de aquecimento e 180 por estágio, seed de dia 20026, seed aleatória 26, variante 0 e lugar 42.

`scene-performance-final-warmed.csv` contém as 88 linhas publicadas pelo teste para oito estágios de cada cena (frio, aquecimento, contínuo, história, noite, retorno, pausa segura e toque). O teste também mediu a saída e confirmou nove estágios por cena, mas essa etapa não publica uma linha `SCENE_DEVICE`. A captura em streaming preservou as 88 linhas completas sem depender da retenção circular do logcat.

Uma rodada posterior, descrita em [PACER-FOLLOWUP.md](PACER-FOLLOWUP.md), desativou a coleta detalhada durante a amostra, mediu diretamente os callbacks de publicação e passou 28 FPS nas 11 cenas. A mediana foi 29,77 FPS, mas o P95 dos intervalos permaneceu entre 40,68 e 54,22 ms. Essa é a evidência mais recente para a cadência; ela ainda não satisfaz o limite de intervalo P95 de 40 ms e foi executada no mesmo tipo de emulador, sem homologação em aparelho físico.

O CSV instrumentado contém as 29 colunas de P50/P95/P99, intervalos, etapas internas, memória e GC para os oito estágios publicados. A medição direta posterior está em [scene-publication-cadence.csv](scene-publication-cadence.csv). Os logs brutos integrais da execução instrumentada permanecem em `device-test-logcat.txt`; `metrics.csv` contém uma rodada anterior e não deve ser usado como resultado mais recente.

