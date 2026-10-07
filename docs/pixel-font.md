# Fonte pixel

A interface usa **Press Start 2P**, de CodeMan38, sob SIL Open Font License 1.1. A fonte foi obtida do projeto Google Fonts e está em `app/src/main/res/font/hoodie_pixel.ttf`.

A licença completa acompanha o aplicativo em `assets/licenses/press_start_2p_ofl.txt` e o repositório em `docs/licenses/press_start_2p_ofl.txt`.

`PixelFont` centraliza a família. Headlines, títulos, labels, botões e números do HUD usam a fonte pixel; descrições e textos longos mantêm a fonte normal do sistema. Os tamanhos foram ajustados para a largura dos glifos. A matriz de screenshots cobre escalas 1.0/1.3 e três dimensões; sua aprovação ainda depende da execução e inspeção das imagens atuais.

## Escala tipográfica

Os tamanhos ficam em `HoodieTypographyTokens` e a tipografia do tema em `HoodieTypography` (`presentation/theme`). A entrelinha da fonte pixel é ~1,4× o tamanho, não 1,5×.

| Estilo | Tamanho / entrelinha | Fonte |
|---|---|---|
| displaySmall | 22 / 30 sp | pixel |
| headlineSmall | 15 / 21 sp | pixel |
| titleLarge | 13 / 18 sp | pixel |
| titleMedium | 11 / 16 sp | pixel |
| labelLarge | 10 / 15 sp | pixel |
| labelSmall | 8 / 12 sp | pixel |
| bodyLarge / bodyMedium / bodySmall | 15 / 13 / 11 sp | sistema (SansSerif) |

HUD (`RetroFontStyles`): título de painel 9 sp, número grande 26 sp, número 14 sp, rótulo 8 sp. Textos longos (descrições, privacidade, erros) usam sempre fonte do sistema. Alvos de toque seguem em 48 dp e a escala de fonte do usuário é respeitada.

Barra inferior: uma linha, `softWrap = false`, mesmo token em todas as abas. Textos visíveis: "Hoje, Hist., Locais, Diário, Ajustes" (as descrições de acessibilidade seguem por extenso: "Histórico", "Lugares"); `BottomNavigationFitTest` confere 360/411 dp com fonte 1.0 e 1.3.

## Ícones, espaçamento e contraste

- **Ícones pixel** (`pixel/icons/PixelIcons.kt`): sprites 10×10 próprios (gato, calendário, pino, mapa, engrenagem, brilho, caminhada, sol, nascer do sol, lua) e os 10 ícones de lugar do `DiaryMapIcons`. `PixelIconView` desenha em escala inteira; `IconLabel` junta ícone e texto. A interface fixa (barra inferior, resumo do Diário, Home, tipos de lugar) usa ícones; emojis em texto livre (linha do tempo, notificações, Jornada, Digital) continuam.
- **Entrelinha:** todo estilo de texto usa `HoodieLineHeightStyle` (centralizado, sem corte), o que evita o glifo invadir o texto vizinho.
- **Espaço rótulo → valor:** `HoodieSpacing.LabelToValue` (4 dp), já incluído no `SectionLabel`.
- **Contraste:** rótulos de 8 sp usam `HoodieColors.MutedStrong` (6,3:1 sobre `PanelLight`); `Muted` fica para textos maiores. `HoodieContrastTest` garante os limites.
