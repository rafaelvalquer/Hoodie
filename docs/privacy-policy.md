# Privacidade — armazenamento e backup manual

O Hoodie guarda os dados pessoais no aparelho e funciona sem conta e sem servidor do Hoodie. A Jornada, os lugares, suas coordenadas, as memórias e os registros digitais não são enviados a servidores do Hoodie pelo mecanismo de backup.

O recurso de backup manual, quando liberado, cria um arquivo protegido por uma senha definida pela pessoa. Ela escolhe o destino no seletor de documentos do Android. O arquivo pode conter lugares e coordenadas, histórico de Jornada, atividades, memórias, rotinas, aprendizados, correções e preferências. A inclusão do histórico digital de aplicativos e sessões é uma escolha explícita. A exportação usa apenas registros que já estavam salvos no aplicativo; não faz uma nova leitura do uso do celular.

A pessoa é responsável por guardar o arquivo e a senha. O Hoodie não tem uma conta, senha mestra ou mecanismo para recuperar um arquivo perdido ou uma senha esquecida. Chaves locais do Android Keystore e a senha interna do SQLCipher não são exportadas.

Provedores externos escolhidos pela pessoa, inclusive serviços de nuvem mostrados pelo Android, administram o armazenamento e a disponibilidade desses arquivos conforme suas próprias políticas. O Hoodie não opera nem administra esse armazenamento.

A restauração exige senha, validação integral e confirmação explícita da substituição dos dados locais. Os arquivos de preparação do banco permanecem cifrados no armazenamento privado. Podem permanecer cópias locais de gerações anteriores para recuperação; o comando Apagar todos os dados também remove essas gerações locais retidas. Permissões Android precisam ser verificadas novamente no aparelho de destino.
