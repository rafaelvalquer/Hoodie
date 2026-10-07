# Revisão visual — NPCs sentados de frente

Status: **VISUALLY_REVIEWED_CANDIDATE** (goldens não promovidos)

Revisei os dez renders individuais e os contatos atualizados em 2026-10-06. A revisão anterior identificou que a mesa cobria o tronco inteiro do gato; o tampo e os props foram movidos para baixo, os candidatos regenerados e a composição corrigida revisada novamente.

## Resultado visual

- Escritório: coelho e gato sentam voltados para a frente nos próprios eixos dos monitores. As cadeiras ficam atrás dos personagens; teclados e mesas ficam à frente. O estado de celular mantém o aparelho junto ao centro do corpo.
- Restaurante: gato sentado centralizado na cadeira em `x=205`, cabeça e tronco superior visíveis. O tampo passa na frente da parte baixa do corpo e a borda frontal fica adiante; prato e copo estão junto às mãos sobre o tampo. Comer, beber, telefone, menu, olhar e fala mantêm a orientação frontal.
- Transições: a comparação mostra as mesmas posições e cenas em `SIDE` antes e `FRONT` depois. Testes cobrem o giro ao levantar e o retorno ao assento; caminhada continua lateral.
- O teste do framebuffer foi ajustado para copiar cada frame antes da renderização seguinte, evitando imagens repetidas na folha de contato.

## Verificação

Suíte focada executada com sucesso em 2026-10-06 após o último ajuste: `NpcAnimationRegressionTest`, `NpcFrontSeatedPoseTest`, `OfficeSeatedOrientationTest`, `RestaurantSeatedOrientationTest`, `RestaurantNpcFurnitureAlignmentTest` e `FrontSeatedVisualReviewTest` (21 testes, zero falhas).

`RestaurantNpcFurnitureAlignmentTest` mede também a âncora da mão em `SIT_EAT` em relação ao centro do prato e as camadas do tampo (baseline 224) e borda (260). Os candidatos foram regenerados depois do ajuste de composição.

Goldens existentes permanecem sem alterações automáticas. Escritório e a nova composição do restaurante foram revisados visualmente; a promoção de goldens continua sendo uma etapa explícita e separada do fluxo do projeto.
