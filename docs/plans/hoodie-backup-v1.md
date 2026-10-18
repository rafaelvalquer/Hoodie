
# Plano de Desenvolvimento — HOODIE-BACKUP-V1

**Projeto:** Hoodie Android

**Repositório:** https://github.com/rafaelvalquer/Hoodie

**Referência técnica:** `main`, banco Room v12

**Prioridade:** P0 — Preparação para produção
**Stack:** Kotlin, Jetpack Compose, Room, SQLCipher, DataStore, Android Keystore e Hilt

---

## 1. Escopo da primeira versão

O Hoodie continuará funcionando integralmente sem conta, internet ou servidor.

Adicionar em **Configurações → Dados e segurança**:

- Exportar backup.
- Restaurar backup.
- Informações sobre o último backup criado neste aparelho.
- Explicação sobre proteção por senha e armazenamento.
- Acesso à restauração também pela tela de recuperação do banco de dados.

A V1 terá backup manual. Não haverá login Google, Firebase, sincronização automática nem envio de dados para servidores do Hoodie.

### Experiência esperada

**Exportação**

1. Usuário acessa Configurações → Dados e segurança.
2. Seleciona **Criar backup**.
3. O aplicativo informa quais dados serão incluídos.
4. Usuário cria e confirma uma senha de recuperação.
5. Escolhe onde salvar o arquivo pelo seletor do Android.
6. Hoodie gera o arquivo criptografado.
7. O sistema verifica a integridade do arquivo.
8. Mostra a confirmação de sucesso.

**Restauração**

1. Usuário instala ou abre o Hoodie em outro aparelho.
2. Seleciona **Restaurar backup**.
3. Escolhe o arquivo `.hoodiebackup`.
4. Informa a senha definida na exportação.
5. O sistema valida o arquivo e apresenta seu conteúdo resumido.
6. Usuário confirma a substituição dos dados locais.
7. Hoodie restaura os dados e reinicia a aplicação.
8. O aplicativo verifica as permissões e reconstrói os serviços necessários.

---

# 2. Quais dados devem ser salvos?

O banco atual possui 29 entidades Room. Nem todas representam dados pessoais que precisam acompanhar o usuário.

A V1 deverá diferenciar informações permanentes de estados temporários do sistema.

## 2.1. Dados obrigatórios

| Categoria | Informações |
|---|---|
| Perfil do Hoodie | Nome, preferências, configurações visuais e sonoras |
| Lugares | Casa, trabalho, academia, restaurantes e demais locais cadastrados |
| Coordenadas | Posições dos lugares, protegidas dentro do backup |
| Rotina | Horários, dias de trabalho, sono e exceções |
| Jornada | Contextos, visitas, horários e eventos da linha do tempo |
| Atividades | Histórico das atividades realizadas pelo Hoodie |
| Memórias | Conquistas e memórias desbloqueadas |
| Mobilidade | Histórico de deslocamentos, segmentos e meios de transporte |
| Aprendizado | Padrões de rotina e transporte aprendidos |
| Correções | Alterações históricas já confirmadas pelo usuário |
| Personalização | Preferências relevantes salvas no DataStore |

## 2.2. Diário Digital

Como o Hoodie também armazena informações sobre o uso do celular, o backup deve oferecer uma escolha explícita.

| Opção | Conteúdo |
|---|---|
| Backup completo | Inclui o histórico digital armazenado no Hoodie |
| Sem histórico digital | Exclui estatísticas de aplicativos, sessões de uso e informações digitais |

A opção completa poderá ser a padrão, desde que o conteúdo seja informado claramente antes da exportação.

Preservar, quando incluídos, os registros correspondentes a:

- `daily_device_usage`
- `daily_app_usage`
- `daily_context_app_usage`
- `daily_screen_hourly`
- `daily_context_usage`
- `daily_phone_timeline`
- `phone_app_sessions`
- `app_category_overrides`

Não realizar uma nova leitura do uso do celular para construir o backup. Exportar somente os dados já armazenados.

## 2.3. Dados que não devem ser restaurados como estado ativo

| Informação | Tratamento |
|---|---|
| Permissões Android | Verificar novamente no aparelho de destino |
| Geofences registradas no sistema | Recriar após a restauração, se autorizadas |
| WorkManager e tarefas agendadas | Reagendar |
| Notificações pendentes | Descartar e recalcular |
| Perguntas temporárias de contexto | Não restaurar como pendentes |
| Operações temporárias de Desfazer | Não restaurar |
| Sessão de mobilidade em andamento | Reconciliar no novo aparelho |
| Estado provisório do dia | Recalcular |
| Caches de mapas e imagens | Regenerar |
| Dados do Dia de Demonstração | Não exportar, pois são fictícios e recriados em memória |
| Chaves antigas do Android Keystore | Nunca exportar |
| Senha interna do SQLCipher | Nunca exportar |

Uma sessão histórica concluída deve ser preservada. Uma sessão ainda aberta no momento do backup não pode reaparecer no novo celular como se o usuário continuasse no mesmo deslocamento.

**Requisito:** documentar uma matriz de cobertura para todas as 29 entidades Room, classificando cada uma como exportada, reconstruída ou excluída, com testes que impeçam omissões acidentais em versões futuras.

---

# 3. Formato do arquivo de backup

Criar extensão própria:

`.hoodiebackup`

Exemplo de nome:

`Hoodie_Backup_2026-10-18.hoodiebackup`

Tipo MIME sugerido:

`application/vnd.hoodie.backup`

Utilizar `application/octet-stream` como alternativa no seletor de documentos quando necessário.

## 3.1. Estrutura lógica

O arquivo conterá:

| Componente | Conteúdo |
|---|---|
| Cabeçalho público mínimo | Identificador do formato, versão e parâmetros criptográficos |
| Área criptografada | Manifesto, registros e configurações |
| Autenticação | Tags criptográficas de integridade |
| Marcador de encerramento | Confirma que o backup terminou corretamente |

O manifesto protegido conterá:

- `formatVersion`
- `appVersion`
- `databaseSchemaVersion`
- `createdAt`
- `timezone`
- `backupId`
- Categorias incluídas
- Quantidade de registros por categoria
- Versão de cada conjunto de dados
- Informações necessárias para validação e compatibilidade

Não colocar nomes de lugares, horários, coordenadas ou informações de uso do celular no cabeçalho público.

## 3.2. Independência do banco interno

Não serializar simplesmente o arquivo físico `hoodie.db`.

Criar modelos de exportação versionados, independentes das entidades Room.

Exemplos:

- `BackupPlaceV1`
- `BackupRoutineV1`
- `BackupContextEventV1`
- `BackupMemoryV1`
- `BackupMobilitySessionV1`
- `BackupSettingsV1`

Essa decisão permite que um arquivo produzido com o banco na versão 12 seja importado numa versão futura do Hoodie, mesmo que o esquema interno tenha mudado.

Não depender dos nomes das colunas e da organização física do SQLCipher como contrato permanente do backup.

---

# 4. Criptografia e senha de recuperação

**Prioridade: P0 — Requisito de segurança**

## 4.1. Algoritmos

Para a V1, utilizar:

| Finalidade | Tecnologia |
|---|---|
| Derivação da chave a partir da senha | PBKDF2-HMAC-SHA256 |
| Custo inicial | No mínimo 600.000 iterações, ajustado por benchmark |
| Chave de criptografia | 256 bits |
| Criptografia autenticada | AES-256-GCM |
| Salt | Aleatório, no mínimo 16 bytes |
| Nonce | Único para cada unidade criptografada |
| Aleatoriedade | `SecureRandom` |

A derivação da chave deverá ser executada fora da thread principal.

O formato do arquivo deve registrar os parâmetros utilizados para permitir futuras mudanças de algoritmo.

Não implementar algoritmos criptográficos manualmente. Utilizar APIs e bibliotecas criptográficas consolidadas.

## 4.2. Senha do usuário

Na exportação:

- Solicitar criação e confirmação da senha.
- Exigir uma senha ou frase de recuperação forte.
- Permitir gerenciadores de senhas.
- Mostrar e ocultar a senha.
- Informar que ela será necessária para restaurar o backup.
- Não armazenar a senha no DataStore, Room ou logs.

**Mensagem obrigatória:**

“Guarde esta senha em um lugar seguro. Se você a esquecer, o Hoodie não conseguirá recuperar este backup.”

Não implementar recuperação por e-mail na V1, pois não existe conta ou servidor capaz de recuperar uma chave perdida.

## 4.3. Criptografia de arquivos grandes

Não carregar todo o backup na memória.

Utilizar um formato de criptografia autenticada em blocos, com:

- Nonce único por bloco.
- Número sequencial autenticado.
- Metadados do arquivo autenticados.
- Limites de tamanho.
- Marcador final autenticado.
- Verificação da sequência completa.

Não aplicar qualquer dado à instalação ativa até concluir a autenticação e validação de todo o arquivo.

Isso evita aceitar backups truncados ou adulterados.

## 4.4. Proteção contra arquivos maliciosos

A importação deverá rejeitar:

- Formatos desconhecidos.
- Versões incompatíveis.
- Arquivos maiores que o limite configurado.
- Parâmetros criptográficos fora dos limites aceitos.
- Quantidades excessivas de registros.
- Valores numéricos ou datas inválidas.
- Dados com relações inconsistentes.
- Blocos ausentes ou repetidos.
- Dados com falha de autenticação.

Utilizar limites de memória e processamento para evitar que um arquivo malicioso provoque travamento.

---

# 5. Portabilidade das coordenadas

**Este é um dos pontos mais importantes do desenvolvimento.**

O Hoodie utiliza `KeystoreCoordinateCipher`, que protege `PlaceEntity.encryptedCoordinates` com uma chave não exportável do Android Keystore.

Essa chave não acompanha o backup.

## 5.1. Durante a exportação

Para cada lugar:

1. Consultar o registro no banco.
2. Descriptografar as coordenadas usando a chave local.
3. Validar latitude e longitude.
4. Inserir as coordenadas no fluxo lógico do backup.
5. Criptografar esse fluxo utilizando a chave derivada da senha de recuperação.

As coordenadas só podem existir temporariamente em memória durante a transformação.

Não criar arquivos temporários com coordenadas em texto puro.

Se alguma coordenada obrigatória não puder ser decifrada, o backup deverá falhar com um erro identificável, sem produzir um arquivo aparentemente completo que perdeu locais silenciosamente.

## 5.2. Durante a importação

1. Descriptografar o arquivo com a senha do usuário.
2. Ler os lugares.
3. Validar as coordenadas.
4. Criar ou selecionar a chave de coordenadas do dispositivo de destino.
5. Criptografar novamente cada posição.
6. Inserir os valores protegidos no novo banco.

Assim:

- O backup pode viajar entre aparelhos.
- A chave antiga continua protegida.
- O novo celular utiliza sua própria proteção local.
- Nenhuma chave do aparelho antigo precisa ser exportada.

## 5.3. Outros dados cifrados

Criar uma etapa de auditoria para detectar outros campos dependentes de chave local, inclusive em novas entidades futuras.

O sistema deverá manter um registro explícito dos campos que exigem transformação criptográfica.

Nunca copiar indiscriminadamente valores criptografados pelo Keystore antigo.

---

# 6. Arquitetura de implementação

Criar:

`app/src/main/java/com/hoodie/app/engine/backup/`

| Arquivo | Responsabilidade |
|---|---|
| `BackupCoordinator.kt` | Orquestrar exportação e importação |
| `BackupManifest.kt` | Metadados e versão |
| `BackupFormat.kt` | Contrato do arquivo |
| `BackupCrypto.kt` | Criptografia autenticada |
| `BackupKeyDerivation.kt` | Derivar chave da senha |
| `BackupExporter.kt` | Construir os dados exportáveis |
| `BackupImporter.kt` | Interpretar os dados importados |
| `BackupValidator.kt` | Validar integridade e consistência |
| `BackupSchemaAdapter.kt` | Compatibilidade entre versões |
| `BackupRepository.kt` | Acesso aos dados persistentes |
| `BackupSettingsMapper.kt` | Exportação das preferências |
| `BackupState.kt` | Estados da operação |
| `BackupError.kt` | Erros identificáveis |

Criar também:

`core/backup/BackupDocumentStore.kt`

`core/backup/BackupStagingManager.kt`

`core/backup/BackupRestoreJournal.kt`

`core/backup/BackupRestoreRecovery.kt`

`core/backup/BackupRestoreCommitter.kt`

`core/backup/BackupOperationGate.kt`

A divisão acima evita colocar toda a lógica de segurança dentro de uma ViewModel ou composable.

## 6.1. Exportação por repositório

Criar uma interface que obtenha snapshots consistentes dos registros selecionados.

É permitido adicionar métodos DAO de leitura em lote para a exportação.

A operação deve utilizar uma visão transacional consistente do banco, para que o histórico não mude pela metade durante a leitura.

Em volumes grandes, ler registros de forma paginada, preservando a mesma visão lógica, e criptografá-los progressivamente.

## 6.2. Evitar migração desnecessária

A V1 pode ser implementada sem adicionar novas tabelas de backup ao banco principal.

Guardar apenas preferências não sensíveis de interface, como data da última exportação concluída, no DataStore.

Não salvar senha ou dados do conteúdo do backup nesse repositório.

---

# 7. Fluxo técnico da exportação

Estados:

`IDLE → PREPARING → READING → ENCRYPTING → WRITING → VERIFYING → SUCCESS`

Saídas alternativas:

`CANCELLED` ou `FAILED`.

### Passos

1. Verificar se o banco está disponível por meio do `DatabaseGate`.
2. Solicitar senha e categorias incluídas.
3. Permitir que o usuário escolha o destino com `ActivityResultContracts.CreateDocument`.
4. Criar o snapshot lógico dos dados.
5. Converter coordenadas para o formato portátil.
6. Criptografar os registros.
7. Concluir e autenticar o arquivo.
8. Validar o arquivo final.
9. Registrar somente a data de sucesso.
10. Limpar buffers e arquivos temporários.

Se a gravação falhar, não apresentar sucesso.

Uma exportação incompleta deverá ser eliminada quando o provedor de documentos permitir. Caso a remoção falhe, informar que o arquivo incompleto deve ser descartado.

### Regras de execução

- Executar processamento e I/O em `Dispatchers.IO`.
- Não bloquear a interface.
- Exibir progresso por etapas.
- Permitir cancelar.
- Não usar logs com dados sensíveis.
- Não criar notificações contendo nomes de lugares ou conteúdo do Diário.

---

# 8. Fluxo técnico da restauração

**Prioridade: P0 — Operação potencialmente destrutiva**

A V1 deverá oferecer somente o modo:

**Substituir os dados locais pelos dados do backup.**

Não desenvolver mesclagem de históricos nesta versão.

Mesclar dois bancos de Jornada exigiria resolver conflitos de IDs, contextos sobrepostos, correções históricas e padrões aprendidos. A substituição integral é mais previsível e segura.

## 8.1. Etapa de pré-validação

Estados:

`FILE_SELECTED → PASSWORD_REQUIRED → DECRYPTING → VALIDATING → PREVIEW_READY`

O sistema deverá verificar:

- Senha correta.
- Integridade criptográfica completa.
- Versão do formato.
- Compatibilidade do conteúdo.
- Esquema e tipos de dados.
- Integridade das relações.
- Ausência de IDs duplicados.
- Quantidades e intervalos dos registros.
- Espaço disponível no armazenamento.

A prévia mostrará, por exemplo:

| Informação | Exemplo ilustrativo |
|---|---|
| Data do backup | 18/10/2026 |
| Lugares | 14 |
| Jornadas | 92 dias |
| Memórias | 23 |
| Rotina | Incluída |
| Histórico digital | Incluído |
| Compatibilidade | Compatível |

Esses dados devem ser lidos de dentro da área autenticada do arquivo.

## 8.2. Confirmação obrigatória

Se o aparelho já possuir dados:

“Restaurar este backup substituirá os dados atuais do Hoodie neste aparelho. Essa ação não pode ser desfeita pelo aplicativo após a conclusão. Recomendamos salvar um backup dos dados atuais antes de continuar.”

Botões:

- Cancelar.
- Criar backup dos dados atuais.
- Restaurar e substituir.

Não iniciar a substituição apenas porque a senha foi aceita.

## 8.3. Criar banco de preparação

Criar um banco temporário cifrado, isolado do banco ativo.

A importação deverá:

1. Preparar a nova base com o esquema compatível.
2. Inserir os registros na ordem correta.
3. Preservar os IDs históricos quando possível.
4. Validar referências entre lugares, contextos e eventos.
5. Recifrar os campos protegidos pelo Keystore.
6. Validar o conteúdo com Room.
7. Executar verificações de integridade SQLCipher.
8. Preparar as preferências restauradas.

**Não utilizar diretamente `Room.createFromFile()` com o backup recebido.** O arquivo é um formato lógico próprio, e não um SQLite pronto para ser instalado.

## 8.4. Substituição segura

Implementar uma transação de restauração em nível de aplicação.

A sequência deverá ser:

1. Bloquear novas operações de escrita do aplicativo.
2. Suspender workers, monitoramento e registros dependentes do banco.
3. Validar novamente o banco preparado.
4. Criar um ponto de recuperação do estado atual.
5. Fechar conexões Room e finalizar WAL/checkpoint.
6. Preparar a troca de geração de dados.
7. Ativar o novo banco cifrado.
8. Aplicar as preferências restauradas.
9. Registrar a conclusão.
10. Reiniciar o processo do aplicativo de forma controlada.

Como o banco e o DataStore são sistemas de armazenamento distintos, uma simples transação Room não torna a restauração inteira atômica.

### Mecanismo de recuperação recomendado

Implementar `BackupRestoreJournal`, persistido de forma durável, contendo estados como:

- `PREPARED`
- `COMMITTING`
- `ACTIVATED`
- `COMPLETED`
- `ROLLBACK_REQUIRED`

Preferir gerações de armazenamento isoladas para que o banco e as chaves antigos não sejam destruídos antes da ativação definitiva.

Na inicialização, o `BackupRestoreRecovery` deverá verificar esse registro **antes de o DatabaseGate abrir o banco**.

Se o processo tiver sido interrompido, concluir a ativação ou restaurar o estado anterior, conforme a fase registrada.

Nunca permitir que o aplicativo abra uma mistura de banco novo com preferências antigas.

### Atenção à arquitetura existente

O projeto utiliza nomes e aliases locais fixos em `HoodieDatabase`, `DatabaseKeyStore` e `KeystoreCoordinateCipher`.

A implementação de gerações de armazenamento exigirá adaptar esses contratos para que o novo banco e suas chaves possam ser preparados sem destruir os dados antigos.

Essa adaptação faz parte do escopo P0; não deve ser substituída por uma sequência simples de apagar banco e copiar arquivos.

---

# 9. Restauração em instalação nova ou banco inacessível

O recurso precisa funcionar mesmo quando o usuário perdeu o celular anterior.

## 9.1. Instalação nova

O Hoodie deverá permitir:

**Primeiro acesso → Restaurar meus dados**

Não exigir a conclusão do onboarding antes de restaurar.

Após sucesso, abrir a Home com o nome, a rotina e os registros restaurados.

## 9.2. Banco local inacessível

Modificar:

`presentation/screens/recovery/DatabaseRecoveryScreen.kt`

Adicionar:

**Restaurar um backup do Hoodie**

Essa opção precisa funcionar sem abrir `HoodieDatabase`, pois a tela também é utilizada quando a chave local não pode ser recuperada.

Fluxo:

1. Escolher arquivo.
2. Informar senha.
3. Validar o backup integralmente.
4. Confirmar explicitamente a substituição.
5. Preparar um novo armazenamento cifrado e suas chaves.
6. Ativar a restauração.
7. Reiniciar o aplicativo.

Não apagar dados ou chaves existentes antes da validação e confirmação.

Se a chave antiga estiver irrecuperável, informar que os dados antigos não poderão ser recuperados por aquele mecanismo, mas não tratar automaticamente esse cenário como autorização para apagá-los.

---

# 10. Reconciliação após a importação

O aparelho novo não deve assumir automaticamente o estado operacional do celular anterior.

Após a restauração:

| Componente | Ação |
|---|---|
| Lugares cadastrados | Preservar |
| Coordenadas | Recifrar com a nova chave |
| Geofences | Registrar novamente se houver permissão |
| Histórico de visitas | Preservar |
| Contexto atualmente aberto no backup | Encerrar/reconciliar com limite temporal explícito |
| Sessões de mobilidade abertas | Não retomar como deslocamento atual |
| Rotina aprendida | Preservar |
| Modelo de localização e reconhecimento | Reavaliar permissões |
| Estado atual do Hoodie | Reconciliar com horário e contexto atuais |
| Memórias | Preservar |
| Workers | Reagendar |
| Notificações pendentes antigas | Não reenviar |
| Configurações que dependem de permissão | Revalidar no Android |

O fim de um contexto aberto restaurado não deve ser artificialmente movido até a data da restauração, pois isso criaria horas ou dias fictícios na Jornada.

Utilizar a data de corte do backup e regras específicas de reconciliação histórica.

---

# 11. Interface — Configurações

Criar:

`presentation/screens/backup/BackupScreen.kt`

`presentation/screens/backup/BackupViewModel.kt`

`presentation/screens/backup/BackupPasswordDialog.kt`

`presentation/screens/backup/BackupRestorePreview.kt`

`presentation/screens/backup/BackupProgressContent.kt`

Integrar a navegação existente.

## Proposta visual

**Dados e segurança**

“Seus dados ficam no seu aparelho. Crie um backup protegido para guardar suas memórias e restaurá-las em outro celular.”

**Criar backup**

“Salve lugares, Jornada, memórias e configurações em um arquivo criptografado.”

**Restaurar backup**

“Recupere seus dados usando um arquivo do Hoodie e sua senha.”

**Último backup**

“Criado em 18/10/2026” — apenas quando existir uma exportação concluída neste aparelho.

### Avisos obrigatórios

- O arquivo precisa ser guardado pelo usuário.
- A senha não pode ser recuperada pelo Hoodie.
- O backup não é automático.
- A restauração substitui os dados existentes.
- Não é necessário ter uma Conta Google.
- Se o usuário escolher salvar em um provedor de nuvem pelo seletor do Android, esse armazenamento será administrado pelo provedor escolhido, e não pelo Hoodie.

---

# 12. Estados de erro

Criar `BackupError` com erros específicos:

| Erro | Mensagem esperada |
|---|---|
| Senha incorreta | “Não foi possível desbloquear o backup. Confira a senha.” |
| Arquivo inválido | “Este arquivo não é um backup válido do Hoodie.” |
| Arquivo adulterado | “O arquivo está corrompido ou foi modificado.” |
| Versão incompatível | “Este backup precisa de uma versão mais recente do Hoodie.” |
| Espaço insuficiente | “Não há espaço suficiente para restaurar os dados com segurança.” |
| Falha de armazenamento | “Não foi possível ler ou salvar o arquivo.” |
| Cancelamento | “Operação cancelada. Seus dados atuais não foram alterados.” |
| Falha na validação | “O backup contém dados inconsistentes e não foi restaurado.” |
| Falha na ativação | “A restauração não terminou. O Hoodie tentará recuperar o estado anterior.” |

O erro não deve expor senha, coordenadas ou detalhes sensíveis em mensagens e logs.

---

# 13. Testes automatizados

## 13.1. Testes unitários

Criar:

- `BackupCryptoTest`
- `BackupKeyDerivationTest`
- `BackupManifestTest`
- `BackupFormatCompatibilityTest`
- `BackupValidationTest`
- `BackupSettingsMapperTest`
- `BackupCoordinateMigrationTest`
- `BackupImportErrorTest`

Casos:

- Criptografar e descriptografar corretamente.
- Senha incorreta rejeitada.
- Salt aleatório entre exportações.
- Nonces nunca reutilizados no mesmo backup.
- Arquivo truncado rejeitado.
- Tag adulterada rejeitada.
- Cabeçalho adulterado rejeitado.
- Versão incompatível rejeitada.
- Valores inválidos e limites de tamanho rejeitados.
- Nenhum dado pessoal em metadados públicos.

## 13.2. Testes de integração Room

Criar:

- `BackupRoundTripIntegrationTest`
- `BackupDatabaseVersionTest`
- `BackupDataIntegrityTest`
- `BackupRestoreRollbackTest`
- `BackupRestoreCrashRecoveryTest`
- `BackupPlaceEncryptionTest`

### Teste principal

Criar uma base fictícia contendo:

- Casa.
- Trabalho.
- Academia.
- Restaurante.
- Histórico de 30 dias.
- Várias visitas ao mesmo lugar.
- Deslocamentos.
- Memórias desbloqueadas.
- Rotina personalizada.
- Histórico digital.
- Correções no Diário.

Executar:

**Exportar → Descriptografar → Validar → Restaurar em instalação isolada → Comparar.**

Comparar:

- Número de registros por categoria.
- IDs e relações.
- Coordenadas recuperadas.
- Horários e intervalos.
- Sequência dos eventos.
- Memórias.
- Rotina.
- Histórico digital.
- Coerência da Jornada.

O teste deve garantir que o resultado não contenha registros duplicados.

## 13.3. Testes entre dispositivos

Simular exportação com um conjunto de chaves e importação com outro.

Confirmar que:

- O segundo dispositivo não conhece as chaves antigas.
- As coordenadas continuam recuperáveis.
- O banco restaurado usa uma nova chave local.
- A Jornada está íntegra.
- O dispositivo antigo não é necessário.

## 13.4. Testes de interrupção

Simular falhas durante:

1. Leitura do banco.
2. Criptografia.
3. Gravação do arquivo.
4. Descriptografia.
5. Validação.
6. Construção do banco temporário.
7. Preparação da substituição.
8. Ativação do novo banco.
9. Aplicação das preferências.
10. Reinicialização.

**Critério obrigatório:** nenhuma falha poderá deixar a instalação em um estado parcialmente restaurado, silenciosamente inconsistente ou sem uma rota segura de recuperação.

## 13.5. Testes de interface

Criar:

- `BackupExportUiTest`
- `BackupImportUiTest`
- `BackupPasswordUiTest`
- `BackupRestoreConfirmationTest`
- `BackupRecoveryScreenTest`

Validar seletor de documentos, navegação, senha, cancelamento, progresso, TalkBack e recuperação após recriar a Activity.

## 13.6. Testes de segurança

- Não registrar senhas no Logcat.
- Não criar arquivos intermediários em texto puro.
- Não incluir chaves do Keystore no arquivo.
- Não exportar token ou segredo interno.
- Não permitir importação de estruturas inesperadas.
- Não aceitar arquivo apenas parcialmente autenticado.
- Limpar temporários mesmo após falhas.
- Verificar que `allowBackup=false` continua ativo.

---

# 14. Ordem de desenvolvimento

| Etapa | Entrega | Prioridade |
|---|---|---|
| 1 | Inventário completo das entidades e configurações | P0 |
| 2 | Contrato versionado do formato `.hoodiebackup` | P0 |
| 3 | Criptografia, derivação de chave e testes | P0 |
| 4 | Exportação dos dados Room | P0 |
| 5 | Exportação das configurações DataStore | P0 |
| 6 | Tratamento portátil das coordenadas | P0 |
| 7 | Interface de exportação e seletor de arquivos | P0 |
| 8 | Validação e prévia da importação | P0 |
| 9 | Banco de preparação e restauração dos registros | P0 |
| 10 | Ativação segura, journal e rollback | P0 |
| 11 | Reconciliação de contexto, geofences e workers | P0 |
| 12 | Restauração pelo onboarding e tela de recuperação | P0 |
| 13 | Testes entre dispositivos e interrupções | P0 |
| 14 | Homologação Android e documentação | P0 |

Recomendo implementar exportação e restauração como entregas testáveis separadamente, mas **não disponibilizar o recurso em produção até ambos funcionarem**.

Um botão de backup sem uma restauração comprovadamente funcional criaria uma falsa sensação de segurança.

---

# 15. Feature flag

Adicionar em `HoodieConfig.kt`:

`HOODIE_LOCAL_ENCRYPTED_BACKUP_V1`

Inicialmente `false`.

Ativar somente depois da homologação.

Quando desativado:

- O Hoodie continua funcionando normalmente.
- Nenhuma permissão adicional é exigida.
- Os dados existentes permanecem intactos.
- O novo mecanismo não interfere no Diário ou no banco.

---

# 16. Critérios finais de aceite

- [ ] O aplicativo funciona sem login e sem internet.
- [ ] O usuário consegue criar um backup completo.
- [ ] O arquivo é criptografado com senha própria.
- [ ] O arquivo pode ser salvo fora do aplicativo.
- [ ] Não são exportadas chaves locais do Android Keystore.
- [ ] Coordenadas podem ser restauradas em outro dispositivo.
- [ ] Lugares e identidades são preservados.
- [ ] Jornada e memórias são preservadas.
- [ ] Rotinas e aprendizados são preservados.
- [ ] Histórico digital é incluído somente conforme a escolha do usuário.
- [ ] Arquivo incorreto ou adulterado é rejeitado.
- [ ] A restauração exige confirmação explícita.
- [ ] O banco atual permanece intacto até a validação do novo.
- [ ] Uma falha durante a substituição é recuperável.
- [ ] A restauração funciona em instalação nova.
- [ ] A restauração funciona pela tela de recuperação.
- [ ] Geofences e workers são reconstruídos corretamente.
- [ ] Permissões são verificadas novamente.
- [ ] Nenhum evento antigo é retomado como atividade atual.
- [ ] A restauração não cria visitas ou atividades fictícias.
- [ ] Todos os testes automatizados passam.
- [ ] O fluxo é validado em pelo menos dois dispositivos ou ambientes com chaves independentes.

---

# 17. Preparação final para produção

Além dos critérios acima, a liberação dependerá de:

1. CI completa aprovada, inclusive testes instrumentados.
2. Política de privacidade refletindo a exportação manual e a responsabilidade de armazenamento do usuário.
3. Documentação sobre senha perdida, arquivo perdido e troca de celular.
4. Verificação da assinatura de produção, R8 e compatibilidade dos APKs.
5. Testes de restauração a partir de backups produzidos pela primeira versão publicada.
6. Garantia de que atualizações futuras continuarão lendo backups antigos suportados.

## Resultado esperado

Com esta funcionalidade, o usuário poderá utilizar o Hoodie por meses ou anos, construir suas memórias e preservar sua Jornada sem depender permanentemente de um único celular.

Quando trocar de aparelho, poderá recuperar os dados com três elementos:

**O aplicativo Hoodie + o arquivo `.hoodiebackup` + a senha de recuperação.**

O aplicativo reconstruirá seu banco cifrado, recuperará o histórico e voltará a operar com as permissões e as chaves de segurança do novo dispositivo.

A base também ficará preparada para, futuramente, utilizar Google Drive como destino do mesmo arquivo criptografado, sem precisar redesenhar o formato do backup.

**Princípio central da V1: os dados pertencem ao usuário, continuam privados e podem ser recuperados sem depender de uma conta ou dos servidores do Hoodie.**
