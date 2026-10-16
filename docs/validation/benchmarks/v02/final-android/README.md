# Evidências Android da Fase 5

O arquivo `scene-performance-final-warmed.csv` registra a execução instrumentada original de 15/10/2026 no emulador Pixel 8 virtual (Android 17 / API 37, x86_64). O teste percorreu as 11 cenas com 180 quadros de aquecimento e 180 por estágio, seed de dia 20026, seed aleatória 26, variante 0 e lugar 42.

`scene-performance-final-warmed.csv` contém as 88 linhas publicadas pelo teste para oito estágios de cada cena (frio, aquecimento, contínuo, história, noite, retorno, pausa segura e toque). O teste também mediu a saída e confirmou nove estágios por cena, mas essa etapa não publica uma linha `SCENE_DEVICE`. A captura em streaming preservou as 88 linhas completas sem depender da retenção circular do logcat.

As rodadas diretas posteriores estão descritas em [PACER-FOLLOWUP.md](PACER-FOLLOWUP.md). Na repetição mais recente, o FPS contínuo ficou entre 27,33 e 30,05 (mediana 29,14), e o intervalo P95 entre 44,25 e 65,26 ms. Nenhuma das 11 cenas atingiu P95 ≤40 ms. Os perfis de `LEISURE` e `RESTAURANT` apontaram render P95 abaixo de 17 ms, enquanto o intervalo de publicação ficou acima de 53 ms. H-07 continua sem homologação; as medições usaram o emulador virtual, sem confirmação em aparelho físico.

O CSV instrumentado contém as 29 colunas de P50/P95/P99, intervalos, etapas internas, memória e GC para os oito estágios publicados. A medição direta posterior está em [scene-publication-cadence.csv](scene-publication-cadence.csv). Os logs brutos integrais da execução instrumentada permanecem em `device-test-logcat.txt`; `metrics.csv` contém uma rodada anterior e não deve ser usado como resultado mais recente.

