# Controle de rasterização com APKs idênticos

O workflow `Exact APK golden diagnosis` reutiliza os APKs do job de instrumentação de `c40f864bb8920ddafd7065c7d23235f4fd41e6f1`, run `39465411317`, artefato `12286957891`. Não recompila código e verifica os SHA-256 de ambos antes de instalar. A origem e os hashes constam em [ci-c40-results.json](ci-c40-results.json).

Executa somente a matriz original `ScreenGoldenMatrixTest`, com seus 180 casos e `verifyGoldens=true`, em imagem Google APIs API 34/revisão 14 e emulador oficial build 15917651. Não grava referências, não altera tolerâncias e não repete testes para conseguir aprovação. Esse escopo isolado permite comparar a mesma sequência com Windows; não equivale aos 388 testes da CI nem apaga suas dez falhas conhecidas.

O controlador Python também pode rodar no host de referência, com `--adb`, `--serial`, `--app`, `--test` e `--output` explícitos. O diretório de saída deve ser novo. Os pacotes `com.hoodie.app` e `com.hoodie.app.test` precisam estar ausentes: instalações existentes são rejeitadas para preservar seus dados. Apenas os pacotes instalados pela própria invocação entram na limpeza. O AVD deve estar preparado com as mesmas configurações de display/renderer do bootstrap compartilhado e sem ANR de boot.

O relatório exige 180 inícios e conclusões identificados e únicos, término normal do runner e zero ignorados/falhas para aprovar o diagnóstico. Preserva ordem dos casos, eventos brutos, propriedades, SurfaceFlinger, Logcat, crashes e PNGs de divergência antes da limpeza. Se a sequência ou o ambiente diferirem, a comparação não basta para atribuir a causa ao host.

Estado: controlador validado por seis testes de evidência sintética; execuções Android com os binários idênticos ainda pendentes. Uma passagem não aprova o release, os APKs R8 derivados do AAB ou o runtime de 16 KB.
