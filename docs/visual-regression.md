# Regressão visual das telas

`ScreenGoldenMatrixTest` usa o conteúdo de produção de Home, Diário, Digital, Onboarding, Ajustes e PlacePicker. ViewModels, banco, sensores e busca de endereços não participam dos fixtures. Os testes de interação continuam separados.

## Matriz

- 360×640, 360×800 e 411×891, em densidade lógica 1.
- Escala de fonte 1.0 e 1.3.
- Normal, carregamento, erro, vazio e completo.
- Total: 180 casos principais. Os estados completos também capturam o fim da rolagem (36 imagens); o Diário completo inclui o mapa (seis imagens).

Data, fuso, tempo de animação e semente aleatória são fixos. A arte passa pelos renderers reais. O PlacePicker usa o MapView real em modo offline, com chave de cache própria, preservando pino, raio, controles e atribuição. Essas capturas não avaliam os tiles do serviço externo.

## Gravar e conferir

O modo normal compara cada pixel com os PNGs em `app/src/androidTest/assets/goldens/screens`. Referência ausente ou diferença faz o teste falhar. Em caso de diferença, imagens `actual` e `diff` são gravadas no armazenamento externo do aplicativo.

Para gravar, executar `connectedDebugAndroidTest` com os argumentos do runner:

```text
class=com.hoodie.app.ui.golden.ScreenGoldenMatrixTest
recordGoldens=true
```

O argumento opcional `goldenCasesRegex` seleciona nomes com uma expressão regular, por exemplo `home_normal_360x640_f100`. Sem filtro, executa a matriz inteira.

Os PNGs são gravados em `/sdcard/Android/data/com.hoodie.app/files/goldens/screens`. Copiar primeiro para `app/build`, inspecionar o layout e somente então colocar as referências nos assets. Repetir sem `recordGoldens=true` confirma a comparação. Não aceitar uma atualização de referências apenas porque a gravação terminou.

Ambiente de referência atual: Hoodie_API34, Google APIs Android 14/API 34 r14 x86_64, resolução física 1080×2400, densidade física 420, GPU SwiftShader e renderer HWUI skiagl. O CI usa a mesma API, resolução, densidade e renderer. O enquadramento e a escala de fonte são definidos pelo teste. Barras do sistema ficam ocultas.

## Estado atual

As 222 referências foram inspecionadas e aceitas, incluindo a recaptura de Ajustes após corrigir a quebra de palavras nos botões. Estão em `app/src/androidTest/assets/goldens/screens`. O manifesto `docs/golden-reference-manifest.json` registra dimensões, SHA-256 e ambiente. A comparação automática completa pixel a pixel está em preparação e ainda não foi aprovada.

Gravação original: 180 casos/222 imagens. Recaptura de Ajustes: 30 casos, todos aprovados; as 198 imagens restantes conservaram hashes idênticos. Os testes Android de interação passaram nos 72 casos, incluindo teclado real, sem ignorados, antes desse último ajuste de layout. A validação final posterior permanece necessária.
