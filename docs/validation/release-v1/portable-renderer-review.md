# Controle de renderização entre fornecedores de CPU

Estado: comparador validado localmente e executado na CI; nenhuma comparação AMD/Intel completa foi aprovada. Nenhuma referência visual ou configuração da CI obrigatória foi alterada por este controle.

A auditoria das [222 referências](golden-reference-coherence.json) encontrou 210 imagens iguais nos dois hosts observados, seis que coincidem apenas com o AMD e seis apenas com o Intel. Nenhuma dessas capturas completas coincide com o conjunto atual. A comparação continua exata; selecionar uma referência diferente conforme a CPU ou ignorar pixels não resolve esse contrato.

O [workflow de revisão](../../../.github/workflows/android-screen-review.yml) mantém os mesmos APKs congelados de `67bd2d7b`, seus dois hashes, API34/revisão14 e ANGLE/SwiftShader observado. O código do app e a ordem/quantidade de casos permanecem verificáveis. O novo job executa 15 testes do comparador antes das duas capturas independentes e então verifica:

- Commit do driver, fontes e hashes do par de APKs; renderer real igual nos dois hosts.
- 180 casos concluídos em cada runner, sem erro/skip, com ordem compatível e 222 arquivos esperados.
- Saúde da captura e logs brutos de crash/ANR, inclusive após a limpeza do app.
- Integridade SHA-256 dos PNGs, dimensões, opacidade e igualdade exata dos pixels decodificados.
- Fornecedores de CPU diferentes, identificados nos arquivos reais de `lscpu`.

O leitor PNG de teste usa Pillow 12.3.0/CPython3.12 em Linux x86_64, com wheel fixado pelo hash publicado na API oficial do PyPI. Não é uma dependência do aplicativo.

Uma comparação completa e idêntica entre fornecedores distintos produz `identical_across_cpu_vendors`. Diferenças produzem `different_pixels`; dois hosts do mesmo fornecedor produzem `inconclusive_same_cpu_vendor`, mesmo com todos os pixels iguais. Evidências ausentes, incompletas ou inconsistentes produzem `invalid_evidence`. Somente a primeira situação retorna zero, e nenhuma delas emite aprovação de baseline, CI obrigatória ou release. O resultado e a causa de uma inconclusão são preservados.

Os 15 casos locais incluem uma diferença de apenas um nível em um único canal/pixel, imagem ausente/duplicada, hash divergente, driver/APK incorretos, caso instrumentado falho, crash em log bruto, crash após limpeza, opacidade inválida e identidade de CPU ambígua. Isso valida o verificador, sem simular uma captura Android aprovada.

O comparador também foi aplicado aos artefatos reais `dd1e01e2`: os dois hosts AMD concluíram 180 casos/222 imagens idênticas, mas o resultado foi corretamente inconclusivo, com saída 1. [Auditoria do controle preservado](angle-comparator-retained-control.json). Esse resultado não comprova estabilidade no Intel nem encerra a falha anterior de PixelCopy.

A nova execução pareada poderá receber dois hosts do mesmo fornecedor, pois o runner hospedado não fixa a CPU. Nesse caso, preservar a inconclusão e decidir o próximo experimento ou obter hosts controlados; não repetir execuções até obter uma combinação favorável. Uma comparação positiva ainda antecede revisão visual, definição do ambiente de referência e CI completa do código atual. O controle usa APKs congelados e não promove automaticamente renderer, imagens ou release.

O [controle 39691860048](https://github.com/rafaelvalquer/Hoodie/actions/runs/39691860048), driver `41162abb`, executou os 15 testes do comparador e as duas capturas completas. O resultado arquivado registra 180 casos/222 imagens por host, pixels decodificados idênticos e dois AMD EPYC7763. O comparador retornou `inconclusive_same_cpu_vendor` e saída1, como previsto. O ZIP do resultado foi conferido por SHA-256; os dois ZIPs das capturas não foram novamente auditados localmente nesta leitura. [Resultado e limites](angle-411-comparison-results.json). Não houve comparação com Intel nem encerramento da falha original de PixelCopy; não repetir o controle à procura de outro fornecedor.
