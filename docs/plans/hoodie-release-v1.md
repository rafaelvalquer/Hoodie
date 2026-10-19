
# Plano Técnico de Desenvolvimento
## HOODIE-V1 — Production Readiness & Release Hardening

**Projeto:** Hoodie
**Repositório:** https://github.com/rafaelvalquer/Hoodie
**Branch de referência:** `main`
**Commit-base:** `6e03a2880f4e`
**Arquitetura:** Kotlin, Jetpack Compose, Hilt, Room, SQLCipher, DataStore e WorkManager
**Meta:** disponibilizar a V1 na Google Play com dados locais, sem login obrigatório e com backup criptografado.

---

# 1. Estratégia de implementação

A preparação será dividida em oito entregas, com bloqueios obrigatórios entre as etapas.

### Princípios

1. Não adicionar novas cenas nem ampliar o escopo funcional da V1.
2. Não aceitar testes ignorados ou modificados apenas para deixar a CI verde.
3. Preservar todas as informações locais existentes durante atualizações.
4. Garantir que backup e restauração funcionem antes de disponibilizá-los publicamente.
5. Utilizar o mesmo código do release candidate na versão final, alterando apenas o que for necessário para identificação da publicação.
6. Não introduzir SDKs de analytics que transmitam a rotina pessoal do usuário.
7. Priorizar a integridade da Jornada sobre efeitos visuais ou desempenho secundário.

### Dependências principais

- **RELEASE-01** bloqueia a promoção de qualquer release candidate.
- **RELEASE-02** deve ser homologada em conjunto com **RELEASE-03**.
- **RELEASE-04** valida o pacote produzido por **RELEASE-03**.
- **RELEASE-05** precisa estar concluída antes de solicitar a publicação pública.
- **RELEASE-06** fornece as evidências de uso real para **RELEASE-07**.
- **RELEASE-08** somente poderá ocorrer depois da aprovação dos gates anteriores.

---

# 2. RELEASE-01 — Corrigir integralmente a CI

**Prioridade:** P0
**Objetivo:** garantir que cada commit candidato à produção seja validado por testes automatizados confiáveis.

## 2.1. Problemas encontrados

A última CI concluída no momento da revisão apresentou quatro falhas instrumentadas.

| Teste | Falha | Investigação / correção |
|---|---|---|
| `ScenePerformanceDeviceTest` | A sessão dos NPCs do Shopping foi recriada durante o teste de carinho | Verificar identidade, ciclo de vida e cache da sessão |
| `HoodieTouchInteractionTest` | Timeout na ação de carinho via acessibilidade | Validar semântica, foco e momento de disponibilidade da ação |
| `PlacePickerScreenTest` | Janela sem foco ao abrir o teclado | Revisar sincronização com a Activity e o IME |
| `SetupAndDemoUiTest` | `ComponentActivity` sem suporte à ViewModel Hilt | Atualizar host de teste e injeção da tela |

## 2.2. Correção 1 — Continuidade dos NPCs no Shopping

Investigar:

`app/src/main/java/com/hoodie/app/pixel/renderer/SceneRenderer.kt`

`app/src/main/java/com/hoodie/app/pixel/npc/venue/`

`app/src/androidTest/java/com/hoodie/app/pixel/ScenePerformanceDeviceTest.kt`

### Implementação

Separar claramente:

- Identidade da sessão do estabelecimento.
- Estado atual da animação.
- Estado transitório do Hoodie.
- Estado de objetos e interações.
- Dados necessários ao planejamento dos NPCs.

A identidade da sessão não deverá mudar apenas porque o Hoodie recebeu um carinho.

Implementar testes que contabilizem:

- Sessões criadas.
- Sessões descartadas.
- Reconstruções da timeline.
- Alterações legítimas de contexto.
- Continuidade de NPCs após interações.

**Aceite:** toque e carinho não recriam a sessão dos NPCs sem uma mudança legítima do estado que define a sessão.

## 2.3. Correção 2 — Acessibilidade do carinho

Investigar:

`HoodieTouchInteractionTest.kt`

`HoodieInteractionTarget.kt`

### Implementação

- Verificar a ação semântica de toque longo apresentada ao Android.
- Confirmar que seu nome é acessível ao TalkBack.
- Garantir que a ação possa ser identificada pelo serviço de acessibilidade.
- Sincronizar o teste com a apresentação efetiva da cena.
- Distinguir falha de foco, ausência da ação e demora na reação.

Não substituir o teste por uma espera arbitrariamente longa.

**Aceite:** a ação de carinho pode ser encontrada e executada de forma reproduzível por acessibilidade.

## 2.4. Correção 3 — Teclado do Place Picker

Investigar:

`PlacePickerScreenTest.kt`

`NewPlaceSaveSheet.kt`

Componentes de busca e edição de lugares.

### Implementação

- Confirmar que a janela esteja focada antes da interação.
- Validar o estado `RESUMED` da Activity.
- Solicitar a abertura do teclado no momento adequado.
- Monitorar os insets do IME.
- Garantir que o campo de nome e o botão Salvar continuem acessíveis.
- Testar teclado físico e virtual.

Separar erros do ambiente de teste de falhas reais de interface.

**Aceite:** a entrada do endereço e do nome funciona sem sobreposição ou perda de foco.

## 2.5. Correção 4 — Teste do Dia de Demonstração

Investigar:

`SetupAndDemoUiTest.kt`

`DemoReplayUiTest.kt`

`DemoReplayViewModel.kt`

### Implementação

- Substituir o host incompatível por um ambiente Hilt válido.
- Alternativamente, injetar explicitamente a ViewModel de teste.
- Atualizar as tags e asserções para a interface atual.
- Verificar abertura da demonstração, reprodução, pausa e saída.

Não alterar a arquitetura de produção para acomodar um teste desatualizado.

**Aceite:** o teste da demonstração passa sem dependências artificiais de uma Activity incompatível.

## 2.6. Melhorar o workflow

Modificar:

`.github/workflows/android-ci.yml`

`.github/scripts/connected-tests.sh`

Adicionar:

- Execução consistente de testes instrumentados.
- Coleta de Logcat quando houver falha.
- Captura de screenshots relevantes.
- Publicação dos relatórios JUnit/XML.
- Identificação explícita dos testes falhos.
- Upload de evidências antes do encerramento do emulador.
- Execução direcionada de testes suspeitos antes da suíte completa.

Quando houver falhas intermitentes, documentá-las e corrigir sua causa. Retentativas podem servir como diagnóstico, mas não devem transformar um teste consistentemente instável em aprovação automática.

## 2.7. Quality gates

Executar:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:connectedDebugAndroidTest
```

Recomendações adicionais:

- Proteger a branch `main`.
- Exigir aprovação dos quatro jobs.
- Bloquear merge com qualquer job obrigatório vermelho.
- Associar relatórios ao commit exato.
- Preservar resultados completos dos testes ignorados.

### Critério final RELEASE-01

- Todos os testes obrigatórios aprovados.
- Nenhuma falha instrumentada conhecida sem investigação.
- Nenhuma exclusão artificial de testes para conseguir aprovação.
- Relatórios e logs disponíveis no GitHub Actions.
- CI verde no commit que será utilizado para gerar o release candidate.

---

# 3. RELEASE-02 — Homologar e ativar o backup V1

**Prioridade:** P0
**Objetivo:** permitir a recuperação confiável dos dados ao trocar de celular.

## 3.1. Situação atual

Já implementados:

- `BackupCoordinator`
- `BackupCrypto`
- `BackupExporter`
- `BackupImporter`
- `BackupRestoreCommitter`
- `BackupRestoreJournal`
- `BackupRestoreRecovery`
- `BackupScreen`
- `BackupViewModel`

A implementação já suporta arquivos `.hoodiebackup`, senha de recuperação, importação validada, recifragem das coordenadas e rollback.

A configuração atual ainda é:

```kotlin
const val HOODIE_LOCAL_ENCRYPTED_BACKUP_V1 = false
```

## 3.2. Estratégia de ativação

### Fase A — Homologação interna

Habilitar a funcionalidade exclusivamente em uma variante ou compilação de homologação.

Não depender de alterar manualmente o valor da flag antes e depois de cada teste.

Criar uma configuração de build específica que permita testar o recurso integrado sem publicá-lo inadvertidamente.

### Fase B — Homologação funcional

Validar:

1. Exportar com histórico digital.
2. Exportar sem histórico digital.
3. Cancelar a exportação.
4. Selecionar destino externo.
5. Restaurar em instalação limpa.
6. Restaurar sobre uma instalação com dados existentes.
7. Digitar senha incorreta.
8. Selecionar arquivo inválido.
9. Simular arquivo truncado.
10. Interromper o processo durante a restauração.
11. Reabrir após rollback.
12. Restaurar a partir da tela de banco inacessível.

### Fase C — Teste entre dois aparelhos

**Aparelho A**

- Cadastrar Casa e Trabalho.
- Registrar lugares e deslocamentos.
- Criar atividades no Diário.
- Desbloquear memórias.
- Configurar a rotina.
- Exportar o backup.

**Aparelho B**

- Instalar o Hoodie sem dados anteriores.
- Importar o arquivo.
- Informar a senha.
- Verificar lugares, Jornada, memórias e configurações.
- Revalidar permissões.
- Reconstruir geofences.
- Criar uma nova atividade.

Confirmar que a nova atividade pertence ao histórico restaurado sem criar duplicações.

## 3.3. Testes adicionais de segurança

O teste entre dispositivos deve usar chaves Android Keystore realmente diferentes.

Também é necessário validar:

- Armazenamento de documentos local.
- Provedor externo de documentos.
- Pouco espaço em disco.
- Activity recriada durante a exportação.
- Encerramento forçado antes e durante a ativação.
- Falha no banco preparado.
- Cancelamento antes do ponto irreversível.
- Recuperação do último estado íntegro.

A derivação PBKDF2 com 600.000 iterações apresentou mediana documentada de aproximadamente 4 segundos no emulador API 37. Medir novamente em aparelhos físicos de entrada e intermediários.

## 3.4. Compatibilidade futura

Preservar permanentemente:

`backup-v1-minimal.hoodiebackup`

Criar uma regra de regressão para que novas versões do aplicativo continuem importando esse arquivo.

Alterações no Room ou no DataStore deverão exigir revisão explícita do contrato de backup.

## 3.5. Publicação da funcionalidade

Somente após homologação, habilitar:

`HOODIE_LOCAL_ENCRYPTED_BACKUP_V1`

A opção deve aparecer em:

- Configurações → Dados e segurança.
- Primeiro acesso.
- Recuperação de banco inacessível.

### Critério final RELEASE-02

- Backup criado e verificado.
- Restauração concluída em outro aparelho físico.
- Todas as relações históricas preservadas.
- Nenhuma chave antiga necessária.
- Sem perda de dados após interrupção.
- Permissões reconstruídas corretamente.
- Funcionalidade habilitada no release candidate.
- Testes de backup incluídos na CI.

---

# 4. RELEASE-03 — Pipeline de AAB assinado

**Prioridade:** P0
**Objetivo:** produzir um pacote distribuível, seguro, reproduzível e identificável.

## 4.1. Criar novo workflow

Arquivo:

`.github/workflows/android-release.yml`

Responsabilidades:

1. Validar a origem do build.
2. Confirmar qualidade e testes obrigatórios.
3. Compilar o código com R8.
4. Gerar o Android App Bundle.
5. Assinar o AAB com a chave de upload.
6. Verificar certificado e integridade.
7. Validar bibliotecas nativas.
8. Gerar APKs de teste a partir do AAB.
9. Publicar os artefatos e relatórios.
10. Aguardar aprovação explícita antes do envio a uma faixa da Play Store.

## 4.2. Assinatura

Utilizar **Play App Signing**.

Separar:

- Chave de assinatura gerenciada para distribuição pela Google Play.
- Chave de upload utilizada pelo pipeline.

Configurar os segredos de assinatura exclusivamente em GitHub Actions Secrets ou GitHub Environments com proteção.

Segredos necessários, conforme a configuração adotada:

- Keystore da chave de upload.
- Senha do keystore.
- Alias.
- Senha da chave.
- Credencial de publicação, somente quando a integração com a Play Developer API for habilitada.

Não incluir:

- `.jks` no Git.
- Senhas em `gradle.properties` versionado.
- Dados de assinatura em logs.
- Chaves privadas em artefatos de build.

Restringir o uso de credenciais a tags ou branches autorizadas.

## 4.3. Workflow proposto

```text
TAG / APROVAÇÃO
       |
       v
VERIFICAR COMMIT E VERSÃO
       |
       v
QUALITY GATES
       |
       v
BUILD RELEASE + R8
       |
       v
ASSINAR AAB
       |
       v
VERIFICAR PACOTE E CERTIFICADO
       |
       v
TESTAR APKs GERADOS DO AAB
       |
       v
VALIDAR 16 KB + SQLCIPHER
       |
       v
PUBLICAR ARTEFATOS
       |
       v
APROVAÇÃO HUMANA
       |
       v
GOOGLE PLAY — TESTE INTERNO/FECHADO
```

## 4.4. Comandos de build

Adicionar à pipeline:

```bash
./gradlew :app:lintRelease
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

O AAB gerado não é instalado diretamente no Android.

Para homologação local, gerar APKs a partir do AAB utilizando `bundletool`, com assinatura apropriada para o ambiente de teste.

Validar:

- Instalação limpa.
- Atualização de uma versão anterior.
- Inicialização com R8.
- Abertura do Room/SQLCipher.
- Carregamento das animações.
- Navegação entre Home, Diário e Configurações.
- Exportação e importação de backup.

## 4.5. Controle de versão

O projeto atualmente utiliza:

`HOODIE_VERSION_CODE=5`

`HOODIE_VERSION_NAME=0.2.0-dev`

Criar política de versionamento:

- Desenvolvimento: sufixo `-dev`.
- Candidato: `1.0.0-rc1`.
- Publicação: `1.0.0`.
- Correções: `1.0.1`, `1.0.2` etc.

Incrementar `versionCode` a cada pacote enviado à Google Play, inclusive candidatos.

Não reutilizar um código de versão já publicado em qualquer faixa.

## 4.6. Relatórios de distribuição

O pipeline deve produzir:

- AAB assinado.
- Identificador do commit.
- VersionName e versionCode.
- Impressão digital do certificado.
- Hash SHA-256 do arquivo.
- Relatório de bibliotecas nativas.
- Resultado dos testes de release.
- Mapeamento de ofuscação R8.
- Lista de validações aprovadas.

### Critério final RELEASE-03

- AAB gerado e assinado.
- Assinatura verificada.
- Pacote aceito pela faixa interna da Google Play.
- APKs derivados do AAB instalados e testados.
- R8 não quebra SQLCipher, Hilt, Room ou componentes visuais.
- Build associado a commit e versão únicos.

---

# 5. RELEASE-04 — Compatibilidade SQLCipher com páginas de 16 KB

**Prioridade:** P0
**Objetivo:** impedir crashes, erros de carregamento e incompatibilidades em dispositivos Android modernos.

## 5.1. Situação atual

O projeto utiliza:

- Android Gradle Plugin `8.13.2`.
- SQLCipher `4.19.1`.
- Android API 36 como target.

O AGP já está acima da versão mínima recomendada para o alinhamento do empacotamento. Porém, isso não comprova que todos os binários nativos entregues pelo aplicativo estejam compatíveis.

## 5.2. Inventário de bibliotecas nativas

Extrair as bibliotecas `.so` do AAB/APKs.

Inspecionar:

- Todas as variantes de `libsqlcipher.so`, conforme a distribuição existente.
- Bibliotecas nativas transitivas.
- Dependências JNI.
- Arquiteturas ARM e x86 incluídas no pacote.
- Alinhamento dos segmentos ELF.
- Alinhamento ZIP das bibliotecas não compactadas.

Utilizar `llvm-readelf` para examinar os segmentos `LOAD` e o alinhamento ELF.

O alinhamento necessário para 16 KB deverá ser verificado em cada biblioteca entregue.

## 5.3. Validar o App Bundle

Utilizar:

```bash
bundletool dump config --bundle=app-release.aab
```

Confirmar a configuração de alinhamento de 16 KB quando aplicável.

Validar também os APKs produzidos a partir do bundle com:

```bash
zipalign -c -P 16 -v 4 app-release.apk
```

O comando deverá ser executado sobre o APK efetivamente gerado para o teste, não sobre o arquivo AAB.

## 5.4. Teste no Android

Configurar emulador ou dispositivo com páginas de memória de 16 KB.

Verificar:

```bash
adb shell getconf PAGE_SIZE
```

Resultado esperado:

```text
16384
```

Executar:

1. Instalação do APK gerado a partir do AAB.
2. Primeira abertura.
3. Inicialização de SQLCipher.
4. Criação do banco.
5. Inserção de lugares.
6. Consulta de histórico.
7. Reinício do processo.
8. Reabertura do banco.
9. Migração de versão anterior.
10. Exportação e restauração de backup.
11. Fechamento e reabertura após restauração.

## 5.5. Falhas a prevenir

- `UnsatisfiedLinkError`.
- Falha em `System.loadLibrary`.
- Crashes nativos.
- Bibliotecas não alinhadas.
- Banco inacessível após atualização.
- Falha em migrações.
- Problemas de empacotamento específicos do AAB.

Se alguma dependência nativa não for compatível, priorizar uma versão oficial corrigida. Não recompilar uma biblioteca de segurança manualmente sem controle de origem, revisão e testes.

## 5.6. Automação

Criar:

`.github/scripts/verify-native-16kb.sh`

Adicionar o script como gate da pipeline de release.

Separar:

- Validação estática de alinhamento.
- Teste funcional em runtime de 16 KB.

Ambos são necessários.

### Critério final RELEASE-04

- Todas as bibliotecas nativas compatíveis.
- AAB/APKs com empacotamento correto.
- SQLCipher funcionando em runtime 16 KB.
- Teste de criação, migração e restauração aprovado.
- Nenhum crash nativo conhecido no release candidate.

---

# 6. RELEASE-05 — Documentação e conformidade Google Play

**Prioridade:** P0
**Objetivo:** garantir transparência sobre dados pessoais e preparar as declarações de publicação.

## 6.1. Política de privacidade completa

O arquivo atual:

`docs/privacy-policy.md`

precisa ser ampliado.

Conteúdo mínimo:

1. Identificação do aplicativo e do responsável.
2. Informações armazenadas pelo Hoodie.
3. Finalidades de cada categoria de dado.
4. Localização em primeiro e segundo plano.
5. Reconhecimento de atividade.
6. Estatísticas de uso dos aplicativos.
7. Processamento local e funcionamento offline.
8. Serviços externos usados para mapas e busca de endereços.
9. Criptografia e armazenamento.
10. Backup e restauração manual.
11. Retenção e exclusão.
12. Compartilhamento iniciado pelo usuário.
13. Segurança e limitações.
14. Canal de suporte e exercício de direitos.
15. Data da última atualização.

Publicar a política em uma página HTTPS estável, acessível sem login.

Adicionar link dentro do próprio aplicativo, em Configurações → Privacidade.

## 6.2. Declaração de localização em segundo plano

O Hoodie solicita:

`ACCESS_BACKGROUND_LOCATION`

A declaração deverá explicar uma funcionalidade central, de forma objetiva:

**Reconhecimento de chegada e saída dos lugares cadastrados para construir automaticamente a Jornada do dia, mesmo quando o aplicativo não está aberto.**

Preparar:

- Divulgação destacada antes da permissão.
- Demonstração da funcionalidade.
- Vídeo mostrando configuração e uso em segundo plano.
- Explicação do benefício.
- Política de privacidade compatível.
- Formulário de permissões sensíveis na Play Console.

A divulgação deve corresponder exatamente ao comportamento real do aplicativo.

## 6.3. Segurança dos Dados

Preparar uma matriz de fluxo de dados.

| Dado | Origem | Armazenamento | Uso | Transmissão |
|---|---|---|---|---|
| Lugares cadastrados | Usuário/localização | Room cifrado | Jornada e reconhecimento | Avaliar serviços externos somente quando utilizados |
| Localização | Android | Processamento local e dados derivados | Reconhecimento de contexto | Não enviada ao servidor do Hoodie |
| Atividade física | Android | Estados e histórico locais | Mobilidade | Não enviada ao servidor do Hoodie |
| Uso de aplicativos | Android | Room cifrado, mediante ativação | Diário Digital | Não enviada ao servidor do Hoodie |
| Backup | Dados locais | Arquivo cifrado | Recuperação | Destino escolhido pelo usuário |
| Consulta de endereços e mapas | Serviços externos | Cache local quando aplicável | Busca e exibição | Requisições aos provedores |

Verificar o tráfego real do aplicativo e as bibliotecas utilizadas antes de preencher as respostas.

Não declarar “nenhuma informação é compartilhada” sem analisar todas as requisições de rede relevantes.

## 6.4. Outros requisitos

Preparar:

- Descrição curta e completa.
- Screenshots reais.
- Ícone e imagem promocional.
- Classificação indicativa.
- Categoria do aplicativo.
- Público-alvo.
- Política de anúncios, se aplicável.
- E-mail de suporte.
- Países de distribuição.
- Declarações de permissões especiais.
- Documentação de direitos sobre imagens, fontes e demais assets.

Como a V1 não possui conta, não será necessário desenvolver uma interface de exclusão de conta inexistente. A exclusão local dos dados, porém, deverá continuar acessível e corretamente documentada.

## 6.5. Testes de conformidade

- Abrir política de privacidade em navegador sem login.
- Verificar links na Play Store e no aplicativo.
- Confirmar declarações sobre dados com o tráfego observado.
- Validar fluxo de consentimento.
- Confirmar funcionamento quando uma permissão for negada.
- Verificar exclusão dos dados locais.
- Verificar desativação dos registros digitais.

### Critério final RELEASE-05

- Política publicada e revisada.
- Declarações preenchidas na Play Console.
- Vídeo e material da localização preparados.
- Textos consistentes com o comportamento real.
- Sem pendências críticas conhecidas de privacidade ou conformidade.

---

# 7. RELEASE-06 — Testes de bateria, localização e geofences

**Prioridade:** P0
**Objetivo:** validar a confiabilidade da proposta principal do Hoodie durante o uso real.

## 7.1. Infraestrutura existente

Reutilizar:

- `GeofenceManager`
- `GeofenceRegistrationPolicy`
- `MobilityEngine`
- `DayStateEngine`
- `WorkScheduler`
- `Developer Lab`
- `GEOFENCE_SCENARIOS.md`

Não criar outro serviço de localização contínua.

## 7.2. Matriz de dispositivos

Executar testes em aparelhos reais, preferencialmente:

| Grupo | Prioridade |
|---|---|
| Samsung intermediário | Obrigatório |
| Motorola intermediário | Obrigatório |
| Pixel ou Android próximo do AOSP | Obrigatório |
| Aparelho com Android mais antigo ainda suportado | Recomendado |
| Dispositivo com páginas de memória de 16 KB | Obrigatório para RELEASE-04 |

Cobrir diferenças de gerenciamento de bateria entre fabricantes.

## 7.3. Teste de sete dias

Executar uma campanha controlada com:

- Uso normal do celular.
- Deslocamentos reais.
- Períodos longos com o aplicativo fechado.
- Wi-Fi e dados móveis.
- Alternância de localização.
- Reinicialização do aparelho.
- Mudança de permissões.
- Períodos noturnos.
- Abertura do Hoodie após horas de inatividade.

### Cenários obrigatórios

| Cenário | Resultado esperado |
|---|---|
| Casa → Trabalho | Contexto correto sem visita duplicada |
| Trabalho → Restaurante | Mudança coerente de atividade |
| Restaurante → Trabalho | Retorno reconhecido |
| Trabalho → Casa | Jornada completa |
| Permanecer em Casa durante a noite | Sem deslocamentos fictícios |
| Ficar sem abrir o aplicativo | Histórico reconciliado corretamente |
| Reiniciar o celular | Registros e workers reconstruídos |
| Localização negada | Fallback e indicação adequada |
| Remover permissão de background | Não fingir monitoramento ativo |
| Ativar economia de bateria | Degradação tratada e documentada |
| GPS oscilando | Evitar múltiplas entradas e saídas fictícias |
| Alteração de fuso ou horário | Jornada sem intervalos impossíveis |

**Nota:** geofences e tarefas em background estão sujeitos ao agendamento do sistema operacional. O objetivo não é prometer detecção instantânea, mas consistência, tolerância a atrasos e recuperação segura.

## 7.4. Medir bateria

Comparar dois períodos equivalentes:

**A — Referência:** Hoodie instalado, recursos de monitoramento desativados.

**B — Monitoramento:** Hoodie com permissões e acompanhamento ativados.

Registrar:

- Consumo atribuído ao aplicativo.
- Tempo de execução em background.
- Acordadas do processo.
- Frequência dos workers.
- Eventos de localização.
- Ativações de Activity Recognition.
- Temperatura e memória.
- Eventos de ANR e crashes.

Usar ferramentas Android como `batterystats`, Perfetto e indicadores do Android vitals.

Estabelecer limites após medir a linha de base. Não escolher uma porcentagem arbitrária como garantia antes dos testes.

## 7.5. Melhorias condicionadas aos resultados

Se houver consumo elevado:

- Revisar frequência de reconciliação.
- Evitar geofences registradas repetidamente.
- Reduzir consultas redundantes.
- Reutilizar estados persistidos.
- Evitar leitura contínua de localização.
- Impedir workers concorrentes.
- Corrigir loops de tentativas após erro.
- Reduzir atividades de diagnóstico em builds públicos.

Se houver falhas de contexto:

- Revisar eventos de chegada e saída.
- Verificar estabilidade de modo de transporte.
- Conferir atrasos de background.
- Registrar causa da inferência de contexto.
- Melhorar a reconciliação sem criar visitas fictícias.

**Não tratar `force-stop` do Android como equivalente a remover o aplicativo dos recentes ou à morte normal do processo.** O sistema restringe a entrega de eventos após um encerramento forçado até que o usuário volte a abrir o aplicativo.

## 7.6. Diagnóstico sanitizado

Adicionar, se necessário, em Configurações → Diagnóstico:

- Versão instalada.
- Status das permissões.
- Estado dos workers.
- Quantidade de geofences.
- Último registro bem-sucedido.
- Erros técnicos recentes.
- Estado da criptografia.
- Condição de sincronização do contexto.
- Resultado de integridade local não sensível.

Não incluir coordenadas, endereços, mensagens ou histórico detalhado no relatório.

### Critério final RELEASE-06

- Campanha de uso prolongado concluída.
- Consumo de bateria comparado com linha de base.
- Jornadas reais revisadas.
- Sem perda sistemática de eventos.
- Sem duplicações relevantes não corrigidas.
- Permissões negadas tratadas corretamente.
- Nenhum crash ou ANR crítico reproduzível.

---

# 8. RELEASE-07 — Beta fechado

**Prioridade:** P1
**Objetivo:** validar o Hoodie com pessoas que não participaram do desenvolvimento.

## 8.1. Configuração inicial

Criar faixas de teste na Google Play:

1. Teste interno para desenvolvedores e validadores.
2. Teste fechado para usuários externos.
3. Produção somente depois da aprovação.

Caso a conta pessoal de desenvolvedor esteja sujeita às regras para contas criadas após 13/11/2023, manter pelo menos 12 testadores inscritos durante 14 dias consecutivos e solicitar posteriormente o acesso à produção.

## 8.2. Grupo de testes

Recomendo selecionar inicialmente de 15 a 25 participantes, com:

- Diferentes modelos Android.
- Pessoas que usam transporte público.
- Pessoas que usam carro.
- Pessoas que trabalham em casa.
- Pessoas que frequentam múltiplos lugares.
- Pessoas que não conhecem o projeto.

Essa diversidade será mais útil que concentrar todos os testes em aparelhos e rotinas semelhantes.

## 8.3. Roteiro para os participantes

**Primeiro dia**

- Instalar o aplicativo.
- Conhecer o Hoodie.
- Completar o onboarding.
- Cadastrar Casa.
- Configurar as permissões desejadas.
- Acessar a Home.

**Durante a semana**

- Utilizar o celular normalmente.
- Confirmar locais quando solicitado.
- Observar cenas e atividades.
- Consultar o Diário.
- Testar carinho e interações.
- Avaliar notificações.

**Final do teste**

- Revisar a Jornada de dias anteriores.
- Criar backup.
- Testar restauração em aparelho de teste, quando disponível.
- Informar problemas e dificuldades.

## 8.4. Classificação de defeitos

| Severidade | Exemplos | Consequência |
|---|---|---|
| P0 — Crítico | Perda de dados, corrupção de banco, falha de restauração, exposição sensível | Bloqueia lançamento |
| P1 — Alto | Crash reproduzível, Jornada incorreta de forma recorrente, monitoramento interrompido | Bloqueia lançamento |
| P2 — Médio | Animação incoerente, UI confusa, lentidão localizada | Corrigir conforme impacto |
| P3 — Baixo | Texto, detalhes visuais, pequenos ajustes | Pode entrar em atualização posterior |

## 8.5. Métricas de acompanhamento

Monitorar:

- Crashes.
- ANRs.
- Instalações e desinstalações.
- Inicialização com sucesso.
- Feedback sobre consumo de bateria.
- Consistência da Jornada.
- Problemas com permissões.
- Falhas de backup e restauração reportadas.
- Dificuldades de navegação.

Não utilizar analytics comportamental que envie a localização ou a rotina detalhada dos participantes sem necessidade e consentimento adequado.

## 8.6. Feedback e correções

Criar uma rotina de triagem:

- Registrar problemas com ID.
- Classificar severidade.
- Associar versão e aparelho.
- Reproduzir.
- Corrigir.
- Adicionar teste de regressão.
- Publicar novo build de beta.
- Solicitar validação da correção.

### Critério final RELEASE-07

- Requisitos de teste fechado atendidos.
- Nenhum defeito P0 aberto.
- Nenhum defeito P1 reproduzível sem mitigação.
- Feedback de onboarding e rotina analisado.
- Backup funcional com a variante distribuída.
- Release candidate aprovado para publicação gradual.

---

# 9. RELEASE-08 — Promover uma versão estável

**Prioridade:** P1
**Objetivo:** lançar a primeira V1 estável com rastreabilidade e plano de resposta a incidentes.

## 9.1. Congelamento de funcionalidades

Criar branch de estabilização:

`release/1.0.0`

Permitir apenas:

- Correções críticas.
- Ajustes de segurança.
- Correções de conformidade.
- Correções de testes.
- Ajustes estritamente necessários de publicação.

Não adicionar novas animações, cenas ou motores durante a estabilização.

## 9.2. Criar release candidate

Definir identificação:

`1.0.0-rc1`

Gerar novo `versionCode`, preservando a monotonicidade.

Publicar a versão candidata na faixa interna/fechada, executar regressão e registrar sua aprovação.

Não gerar um APK diferente da mesma fonte sem repetir os testes relevantes.

## 9.3. Release checklist final

Validar:

- CI verde.
- AAB assinado.
- Compatibilidade 16 KB.
- SQLCipher funcional.
- Migrações aprovadas.
- Backup e restauração aprovados.
- Manifest final revisado.
- Permissões explicadas.
- Política publicada.
- Segurança dos Dados revisada.
- Testes físicos aprovados.
- Testadores externos aprovados.
- Ícone, screenshots e descrição definitivos.
- Canal de suporte funcionando.
- Nenhum bug P0/P1 aberto.

Arquivar a evidência em:

`docs/validation/release-v1/`

Exemplos:

- `ci-results.md`
- `backup-device-matrix.md`
- `native-16kb-results.md`
- `battery-geofence-results.md`
- `play-compliance.md`
- `beta-summary.md`
- `release-approval.md`

## 9.4. Publicação gradual

Depois de aprovado na Play Console, iniciar com uma pequena parcela dos usuários elegíveis.

Estratégia operacional proposta, conforme disponibilidade de controles da faixa:

| Etapa | Percentual | Condição para avançar |
|---|---:|---|
| Inicial | 5% | Instalação e abertura sem incidentes críticos |
| Ampliação 1 | 20% | Crashes/ANRs e suporte dentro das metas |
| Ampliação 2 | 50% | Sem sinais de corrupção ou perda de dados |
| Geral | 100% | Métricas estáveis e aprovação da equipe |

Os percentuais são uma estratégia proposta, não uma configuração já existente.

Não avançar automaticamente se a amostra for pequena ou não houver evidências suficientes.

## 9.5. Plano de incidente

Definir procedimento para:

- Crash generalizado.
- Falha de SQLCipher.
- Falha de restauração.
- Problemas de permissões.
- Consumo excessivo de bateria.
- Inconsistência de Jornada.
- Rejeição pela Google Play.

Em caso de incidente:

1. Interromper a ampliação da publicação.
2. Avaliar suspensão da versão afetada na Play Console.
3. Registrar alcance e gravidade.
4. Preservar evidências técnicas sem dados sensíveis.
5. Corrigir.
6. Publicar hotfix com novo `versionCode`.
7. Validar a atualização sobre instalações já existentes.

**Não considerar que interromper a distribuição faz aparelhos já atualizados voltarem automaticamente à versão anterior.** O planejamento deve privilegiar correções seguras e preservação dos dados locais.

## 9.6. Monitoramento pós-lançamento

Acompanhar Android vitals e relatos de suporte.

Priorizar:

- Crashes.
- ANRs.
- Tempo de inicialização.
- Consumo de bateria.
- Falhas de instalação.
- Falhas de backup.
- Problemas de permissões.
- Casos de perda ou duplicação de dados.

### Critério final RELEASE-08

- Versão final identificada.
- AAB distribuído pela Google Play.
- Release aprovado.
- Publicação gradual concluída.
- Nenhum incidente crítico ativo.
- Evidências e documentação arquivadas.
- Procedimento de atualização definido.

---

# 10. Cronograma e dependências

A implementação deverá seguir esta sequência:

| Sprint | Entregas | Dependência |
|---|---|---|
| Sprint A — Qualidade | RELEASE-01 | Imediata |
| Sprint B — Segurança e distribuição | RELEASE-02, RELEASE-03, RELEASE-04 | CI estabilizada |
| Sprint C — Conformidade | RELEASE-05 | Pode avançar em paralelo |
| Sprint D — Homologação física | RELEASE-06 | Build e permissões estáveis |
| Sprint E — Beta | RELEASE-07 | Todas as validações P0 concluídas |
| Sprint F — Lançamento | RELEASE-08 | Beta aprovado |

O desenvolvimento da documentação da Google Play pode ocorrer em paralelo desde o início.

Os testes prolongados e o beta dependem de tempo real de observação, portanto não devem ser substituídos apenas por simulações aceleradas.

---

# 11. Gates obrigatórios antes da produção

## Gate A — Qualidade do código

- [ ] Unitários aprovados.
- [ ] Lint aprovado.
- [ ] Instrumentados aprovados.
- [ ] Migrações validadas.
- [ ] Sem falhas críticas conhecidas.

## Gate B — Dados e segurança

- [ ] Banco cifrado operacional.
- [ ] Exportação criptografada funcionando.
- [ ] Restauração em outro dispositivo.
- [ ] Rollback testado.
- [ ] Dados históricos íntegros.
- [ ] Backup liberado para o usuário.

## Gate C — Distribuição

- [ ] Build release assinado.
- [ ] AAB verificado.
- [ ] Teste de instalação e atualização.
- [ ] SQLCipher validado com 16 KB.
- [ ] Versionamento consistente.

## Gate D — Google Play

- [ ] Política de privacidade pública.
- [ ] Segurança dos Dados preenchida.
- [ ] Declaração de localização preparada.
- [ ] Vídeo demonstrativo aceito quando exigido.
- [ ] Informações da loja concluídas.

## Gate E — Uso real

- [ ] Testes de bateria realizados.
- [ ] Geofences verificadas com aplicativo fechado.
- [ ] Reinicialização homologada.
- [ ] Jornada real consistente.
- [ ] Permissões recusadas tratadas corretamente.

## Gate F — Publicação

- [ ] Beta fechado aprovado.
- [ ] Nenhum P0 ou P1 aberto.
- [ ] Release candidate aprovado.
- [ ] Publicação gradual habilitada.
- [ ] Procedimento de resposta a incidentes disponível.

---

# 12. Definição de pronto — Hoodie V1

A V1 será considerada pronta somente quando:

1. O usuário conseguir instalar e utilizar o Hoodie sem criar uma conta.
2. A rotina for reconhecida de forma consistente no uso normal.
3. O Diário permanecer íntegro após atualizações e períodos de inatividade.
4. O banco local estiver protegido.
5. O usuário conseguir salvar e recuperar seus dados em outro aparelho.
6. As permissões forem apresentadas e utilizadas de maneira transparente.
7. O aplicativo funcionar corretamente no pacote release distribuível.
8. A Google Play aceitar as declarações e o pacote.
9. O beta não apresentar defeitos críticos.
10. Existir um processo definido para atualização, suporte e correções.

**Resultado final esperado:** um Hoodie V1 que mantenha a qualidade visual e a personalidade atuais, mas que tenha como principal diferencial de produção a confiabilidade do registro da rotina, a proteção das memórias do usuário e a continuidade da Jornada.
