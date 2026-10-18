# Formato HOODIE-BACKUP-V1

Contrato lógico congelado em `BackupSchemaV1.kt` e `BackupModelsV1.kt`. Não é uma cópia SQLite. Extensão `.hoodiebackup`, MIME `application/vnd.hoodie.backup` (leitura também aceita octet-stream).

## Cabeçalho público — 40 bytes, big endian

| Offset | Bytes | Conteúdo |
|---|---:|---|
| 0 | 8 | ASCII `HOODIEB1` |
| 8 | 4 | Versão do formato: 1 |
| 12 | 4 | Iterações PBKDF2-HMAC-SHA256: 600.000 a 1.200.000 |
| 16 | 16 | Salt aleatório |
| 32 | 8 | Prefixo aleatório do nonce |

A versão 1 fixa PBKDF2-HMAC-SHA256, AES-256-GCM, chave de 256 bits e tag de 128 bits. Uma mudança de algoritmos exige outra versão. Nenhuma informação pessoal é pública. O manifesto protegido registra versão do app, schema de origem, data de corte, fuso, UUID, escolha digital, contagens e versões dos datasets.

## Blocos autenticados

Cada bloco contém índice sequencial Int32, comprimento do texto claro Int32 e ciphertext seguido da tag de 16 bytes. O nonce é `prefixo[8] + índice[4]`. AAD é o cabeçalho inteiro seguido do índice e do comprimento, ambos big endian. Salt e prefixo novos vêm de SecureRandom a cada exportação.

Cada unidade contém um objeto JSON UTF-8: manifesto, preferências, registros agrupados na ordem dos datasets do contrato. O arquivo termina com um bloco vazio autenticado, com o próximo índice esperado. Não são aceitos EOF antecipado, índices repetidos/invertidos, registros após o encerramento ou bytes extras. Cada registro é autenticado antes de ser interpretado; nenhum dado chega à instalação ativa antes de todo o arquivo ser autenticado e validado.

Limites V1: 256 KiB por registro, 512 MiB por arquivo, 1 milhão de unidades e profundidade JSON de 12. Strings têm limite adicional de 65.536 caracteres. Não há compactação nem extração de caminhos vindos do arquivo. Parâmetros KDF são limitados antes da derivação. Senha/frase de exportação: 12 a 256 caracteres, com pelo menos 5 caracteres diferentes. O segredo não é persistido, nem colocado em SavedStateHandle, DataStore, Room ou logs.

O erro de autenticação do primeiro bloco não permite distinguir senha incorreta de adulteração do salt/tag; ambos são rejeitados. Mensagens não expõem dados, SQL ou detalhes criptográficos.

## Portabilidade e reconciliação

Coordenadas de places são decifradas com o alias local, validadas e exportadas como latitude/longitude somente dentro do payload criptografado. No destino são cifradas por um novo alias. Chaves SQLCipher, chaves Keystore e ciphertext dependente do dispositivo nunca entram no contrato portátil.

Intervalos abertos de contexto, sessão, segmento e evidência terminam na data de corte do snapshot. Sessões são importadas em ARRIVED, sem movimento pendente ou reserva de worker. Estados atuais do Hoodie e do dia são reconstruídos; perguntas, desfazer e fila de notificações não são exportados. Registros históricos concluídos conservam seus horários e IDs.

O modelo atual permite que IDs de lugares históricos sobrevivam à exclusão de um lugar. Essas referências históricas são preservadas como tal e não reconstruídas como locais ativos. Relações obrigatórias de segmentos e evidências com sessões são verificadas, incluindo foreign_key_check. A unicidade é validada pelas chaves e índices Room do banco preparado. Novos campos, entidades e campos cifrados quebram os testes de cobertura até receberem decisão explícita.

## Atomicidade de restauração

Banco, arquivo de chave, aliases de coordenadas e DataStore possuem um namespace por geração. A geração legacy conserva exatamente os nomes da instalação existente. A importação cria uma geração nova com SQLCipher e chaves novas. Preferências completas são persistidas nessa geração antes da ativação.

O journal usa arquivo temporário sincronizado em disco (fsync), renomeação atômica e releitura de confirmação: PREPARED → COMMITTING → ACTIVATED → COMPLETED. A troca de um único ponteiro seleciona banco e preferências juntos. Qualquer interrupção antes de COMPLETED retorna à geração anterior; COMPLETED seleciona a nova. A recuperação roda em Application.attachBaseContext, antes da injeção de Hilt e antes de DatabaseGate/DataStore. Se não for possível reparar o journal/ponteiro, o banco fica bloqueado e a tela de recuperação permanece disponível, sem adivinhar uma geração ou criar chaves novas. O ponto anterior é retido; gerações de preparação abandonadas são eliminadas na inicialização. Apagar todos os dados remove também as gerações retidas.

Workers são cancelados, reconhecimento e geofences são suspensos, notificações são descartadas, o gate é suspenso e o Room antigo é fechado antes de trocar o ponteiro. A aplicação reinicia para reconstruir singletons; no destino, geofences dependem das permissões atuais, workers são reagendados e a análise digital começa desativada até nova autorização/configuração.

O fixture `backup-v1-minimal.hoodiebackup` é fictício e congelado para compatibilidade futura. Senha de teste: `hoodie recovery phrase`. Nunca regenerar o fixture apenas para fazer um leitor incompatível passar.
