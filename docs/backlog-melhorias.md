# Backlog de melhorias do Hoodie

Backlog montado em 07/10/2026 a partir do estado do código e dos registros em `stability-architecture-progress.md`, `v0.2-release-checklist.md`, `diary-development-progress.md` e `day-clock-progress.md`.

## Como priorizar

- **P0 — Release:** bloqueia confiança, dados, privacidade ou validação da versão 0.2.
- **P1 — Próxima entrega:** fecha fluxos já existentes e reduz fricção importante.
- **P2 — Evolução:** melhora produto, acessibilidade, manutenção ou diagnóstico sem bloquear o uso atual.

Itens já presentes no código aparecem como **validar/concluir**, não como funcionalidades para reimplementar. A árvore de trabalho contém alterações não commitadas; antes de executar itens, conferir o estado atual do código e preservar mudanças em andamento.

## P0 — Confiabilidade e release

### BL-01 — Resolver as divergências da matriz visual API 34

**Estado:** pendente. O registro mais recente descreve 180 casos comparados, 18 aprovados e 162 divergentes, preservando as 222 referências originais.

**Trabalho:** classificar as diferenças por tela e causa; corrigir a interface quando houver regressão; quando a mudança for intencional, revisar cada imagem afetada antes de aceitar uma nova referência.

**Pronto quando:** os 180 casos forem executados em API 34 com zero falhas ou ignorados; as imagens aceitas forem revisadas; o manifesto de hashes corresponder às referências; nenhuma baseline tiver sido atualizada apenas para fazer o teste passar.

### BL-02 — Fechar gates de estabilidade Android da versão 0.2

**Estado:** validar na árvore atual. Há evidências anteriores de suíte JVM e Android aprovadas, mas os registros também apontam execuções posteriores incompletas e divergências visuais.

**Trabalho:** repetir build debug, suíte JVM, testes instrumentados no AVD API 34, SQLCipher/Room, migrações, onboarding, consentimento digital, workers e navegação secundária.

**Pronto quando:** comandos e resultados finais forem registrados em um único relatório; não houver testes ignorados por pressupostos ambientais; migração e reopen do banco cifrado preservarem os dados; o checklist de release estiver todo verde.

### BL-03 — Validar o fim inferido do dia ativo e os segmentos de sono

**Estado:** implementação em andamento na árvore atual; testes focados foram executados, mas a mudança ainda precisa da validação integrada e de revisão do diff.

**Trabalho:** confirmar sono explícito no Relógio do Dia, níveis de confiança, comportamento Unknown quando não há evidência, lookahead após meia-noite e recorte de visitas, replay e totais. Fazer smoke test de hoje e de dias históricos.

**Pronto quando:** testes JVM do detector, assembler, visitas, timeline e repositório passarem juntos; a tela mostrar corretamente os segmentos; nenhuma evidência do dia seguinte aparecer no dia selecionado; não houver mudança de schema Room.

### BL-04 — Completar a revisão em aparelho real do Diário e do Relógio

**Estado:** revisão de manhã/tarde/noite em aparelho real pendente; algumas capturas anteriores foram feitas em emuladores.

**Trabalho:** revisar legibilidade, toque, contraste, fonte ampliada, seleção de segmento, centro do relógio, replay e detalhe do lugar em aparelho físico. Verificar o modal de detalhes, cuja captura anterior não mostrou a camada modal apesar de a hierarquia acessível confirmar sua abertura.

**Pronto quando:** capturas e checklist assinados por cenário forem anexados; os fluxos forem utilizáveis com fonte ampliada; defeitos encontrados forem corrigidos e revalidados.

## P1 — Fluxos e experiência

### BL-05 — Encerrar a auditoria de acessibilidade

**Trabalho:** revisar alvos de toque, ordem e rótulos de TalkBack, foco de teclado, contraste, fonte ampliada e mensagens de erro nas telas principais, seletores, mapa e diálogo de novo lugar.

**Pronto quando:** todos os controles principais tiverem rótulo/ação compreensíveis, alvos mínimos de toque e testes instrumentados para os fluxos críticos; não houver texto cortado nos tamanhos de fonte suportados.

### BL-06 — Completar erros tipados e localização

**Estado:** os registros apontam mensagens textuais ainda no PlacePicker, strings dinâmicas/helper pendentes e uso incompleto de `UiState`/`UiEvent` em ações de algumas telas.

**Trabalho:** migrar mensagens restantes para recursos localizados; mapear erros de domínio para mensagens acionáveis; padronizar retry, loading e eventos sem duplicar mensagens após rotação ou retorno de tela.

**Pronto quando:** nenhuma mensagem de erro de domínio estiver embutida em texto da UI; português estiver coberto; os fluxos de erro/retry e lifecycle tiverem testes.

### BL-07 — Finalizar retry e feedback de geofence

**Estado:** pendente no progresso de arquitetura.

**Trabalho:** exibir resultado compreensível quando a verificação automática falhar, permitir nova tentativa manual e evitar repetir notificações sem ação útil.

**Pronto quando:** sucesso, falha temporária, permissão ausente e retry estiverem cobertos por testes; a interface informar o estado sem travar a tela.

### BL-08 — Consolidar onboarding, edição e permissões em testes de ponta a ponta

**Trabalho:** confirmar cadastro e edição de lugar, lugar removido durante edição, permissão de uso digital, retorno das configurações do Android e persistência de consentimento após reinício.

**Pronto quando:** os fluxos instrumentados validarem o resultado na UI e no banco real, incluindo falha recuperável e retry, sem duplicar lugar nem ativar coleta sem opt-in.

### BL-09 — Atualizar documentação funcional e preparar a versão 0.2

**Estado:** documentação funcional e promoção de versão aparecem como pendências; manter `0.2.0-dev` até os gates serem concluídos.

**Trabalho:** atualizar README e guias para refletir catálogo físico, mobilidade, Diário/Relógio e consentimento; revisar instruções de build, validação visual e recuperação de falhas; depois iniciar RC e release conforme o checklist.

**Pronto quando:** documentos coincidirem com o app; checklist não tiver pendências; versionCode/versionName e notas de release estiverem consistentes; a RC tiver sido validada antes da versão estável.

## P2 — Evolução do produto e manutenção

### BL-10 — Tornar o Diário mais explicável

**Ideia:** permitir entender por que um horário foi classificado como sono, despertar, visita ou deslocamento e distinguir registro confirmado de inferência.

**Pronto quando:** detalhes mostrarem origem e confiança sem expor dados desnecessários; correções manuais puderem ser feitas sem alterar eventos de origem silenciosamente; testes cobrirem sobreposições e lacunas.

### BL-11 — Correção manual de rotina inferida

**Ideia:** permitir ajustar início/fim de visita, categoria ou trecho de sono e reaplicar o ajuste ao resumo e replay daquele dia.

**Pronto quando:** o usuário puder revisar e desfazer a correção; conflitos com eventos existentes forem explicados; persistência e privacidade estiverem documentadas e testadas.

### BL-12 — Resumo semanal privado

**Ideia:** oferecer tendências agregadas de casa, trabalho, mobilidade, sono e uso digital, com opt-in separado para os dados digitais.

**Pronto quando:** agregações funcionarem sem criar rota GPS; período vazio e dados parciais forem claros; desligar o opt-in impedir novas leituras; a tela não apresentar estimativas como fatos.

### BL-13 — Diagnóstico local de qualidade dos dados

**Ideia:** mostrar lacunas de localização, permissões, sincronização e histórico que afetam as inferências, com passos simples para corrigir.

**Pronto quando:** diagnóstico for local, não incluir coordenadas em logs e indicar a fonte do problema sem sugerir que dados faltantes significam inatividade.

### BL-14 — Galeria de cenas/NPC reproduzível

**Ideia:** padronizar exportação, manifestos e revisão humana dos NPCs e cenas públicas, conservando as referências aprovadas.

**Pronto quando:** cada galeria identificar fonte, configuração, hash e estado de aprovação; as capturas puderem ser reproduzidas; alterações em goldens exigirem revisão visual explícita.

### BL-15 — Auditoria periódica de banco e desempenho

**Ideia:** repetir análise de índices e planos SQL conforme as tabelas crescem; medir custo de carregar vários dias, Diário e replay.

**Pronto quando:** consultas críticas tiverem plano documentado, limites de leitura e benchmarks repetíveis; migrações mantiverem históricos reais.

## Sequência sugerida

1. BL-03, porque a mudança de dia ativo está em andamento e precisa fechar teste integrado.
2. BL-01 e BL-02, para estabelecer uma referência confiável da versão atual.
3. BL-04 e BL-05, revisão visual e acessibilidade no aparelho.
4. BL-06 a BL-09, fechar fluxos e documentação antes da RC.
5. BL-10 a BL-15, escolher por valor para usuários depois da estabilização.

## Fora do próximo release por padrão

Rota por ruas, rastreamento contínuo por GPS, heatmap, exportação/compartilhamento do Diário e gravação de replay em vídeo continuam fora do escopo imediato. Reabrir esses itens apenas com objetivo, consentimento e impacto de privacidade definidos.
