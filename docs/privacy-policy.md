# Política de privacidade — Hoodie

Última atualização: 19 de outubro de 2026.

Contato do responsável pelo Hoodie e suporte: **rafael.valquer@gmail.com**. Solicitações sobre privacidade, correção, exclusão e demais direitos podem ser enviadas a esse endereço. Não envie senhas de backup ou arquivos contendo sua rotina no primeiro contato.

## Dados e finalidades

O Hoodie permite registrar e compreender a rotina sem criar uma conta. Armazena no aparelho os lugares cadastrados (nomes, tipos, endereços e coordenadas), chegadas e saídas, deslocamentos e atividades, Diário/Jornada, memórias, configurações de rotina, correções e aprendizados locais. Esses dados permitem compor a Jornada, reconhecer contextos e personalizar o personagem.

A localização aproximada ou precisa é usada, com permissão, para escolher lugares e reconhecer contexto. A localização em segundo plano permite reconhecer chegada e saída dos lugares cadastrados e construir a Jornada mesmo com o aplicativo fechado. Geofences e tarefas estão sujeitas às restrições do Android e podem apresentar atrasos. É possível negar ou revogar permissões nos ajustes do sistema; o registro automático fica limitado e as funções manuais e o modo de demonstração continuam disponíveis.

O reconhecimento de atividade, quando permitido, ajuda a inferir deslocamentos e modos de transporte. Notificações são opcionais e podem apresentar informações de contexto na tela do aparelho; a pessoa controla sua exibição no Hoodie e no Android.

O acesso especial às estatísticas de uso é opcional e depende de autorização específica no Android e da ativação dos registros digitais. Pode registrar aplicativos usados, duração, sessões e agregados de uso da tela, relacionando-os ao contexto local. Não lê o conteúdo de mensagens nem o conteúdo das telas de outros aplicativos. É possível desativar os registros digitais e apagar o histórico digital em Configurações; revogar o acesso especial no Android impede novas leituras.

## Processamento local e serviços externos

O Hoodie guarda os dados pessoais no aparelho e funciona sem conta e sem servidor do Hoodie. A Jornada, os lugares, suas coordenadas, as memórias e os registros digitais não são enviados a servidores do Hoodie pelo mecanismo de backup.

O registro e a consulta dos dados salvos funcionam offline. Mapas e busca de endereços podem precisar de conexão. O mapa usa os servidores de tiles do OpenStreetMap por meio de osmdroid; solicitações de imagens revelam ao provedor o endereço IP e a região do mapa consultada. O texto digitado na busca de endereço e as coordenadas usadas na busca reversa são enviados ao serviço de geocodificação configurado no Android. A disponibilidade e o tratamento desses serviços dependem do sistema e de seus provedores. Consulte as políticas do [OpenStreetMap](https://osmfoundation.org/wiki/Privacy_Policy) e do provedor de serviços do seu aparelho. As APIs de geofences e reconhecimento de atividade também dependem dos serviços Android/Google Play presentes no dispositivo.

O Hoodie não integra SDK de anúncios ou analytics comportamental para transmitir sua rotina. Informações enviadas voluntariamente por e-mail ao suporte são recebidas pelo serviço de e-mail e utilizadas para responder à solicitação; evite incluir dados sensíveis desnecessários. A Google Play pode processar informações de distribuição e diagnósticos conforme os ajustes do Android e suas próprias políticas.

## Armazenamento e proteção

O banco local é cifrado com SQLCipher. Chaves locais são protegidas pelo Android Keystore; coordenadas também recebem proteção local específica. Configurações permitidas ficam no armazenamento privado do aplicativo. O backup automático de dados do Android está desabilitado para o Hoodie. A proteção depende também do bloqueio de tela, das condições do sistema e da segurança do aparelho; não há garantia absoluta contra um dispositivo comprometido ou acesso enquanto ele está desbloqueado.

## Backup manual e restauração

O recurso de backup manual, quando liberado, cria um arquivo protegido por uma senha definida pela pessoa. Ela escolhe o destino no seletor de documentos do Android. O arquivo pode conter lugares e coordenadas, histórico de Jornada, atividades, memórias, rotinas, aprendizados, correções e preferências. A inclusão do histórico digital de aplicativos e sessões é uma escolha explícita. A exportação usa apenas registros que já estavam salvos no aplicativo; não faz uma nova leitura do uso do celular.

A pessoa é responsável por guardar o arquivo e a senha. O Hoodie não tem uma conta, senha mestra ou mecanismo para recuperar um arquivo perdido ou uma senha esquecida. Chaves locais do Android Keystore e a senha interna do SQLCipher não são exportadas.

Provedores externos escolhidos pela pessoa, inclusive serviços de nuvem mostrados pelo Android, administram o armazenamento e a disponibilidade desses arquivos conforme suas próprias políticas. O Hoodie não opera nem administra esse armazenamento.

A restauração exige senha, validação integral e confirmação explícita da substituição dos dados locais. Os arquivos de preparação do banco permanecem cifrados no armazenamento privado. Podem permanecer cópias locais de gerações anteriores para recuperação; o comando Apagar todos os dados também remove essas gerações locais retidas. Permissões Android precisam ser verificadas novamente no aparelho de destino.

O arquivo manual usa criptografia autenticada e derivação de chave a partir da senha. A proteção do arquivo depende da força e do sigilo dessa senha. A disponibilidade do recurso pode variar conforme a versão ou variante; enquanto estiver em homologação, não aparece nas versões públicas.

## Retenção, exclusão e escolhas

Os dados permanecem localmente enquanto necessários ao uso, até sua exclusão pelo usuário ou pela remoção/limpeza dos dados do aplicativo no Android. Em Configurações, Apagar todos os dados remove o banco, chaves, preferências e gerações locais de recuperação. A exclusão do histórico digital tem comando próprio. Não existe conta do Hoodie a excluir.

Eventos brutos de entrada e saída usados pelo reconhecimento de lugares estão sujeitos à limpeza local após 30 dias. Sessões individuais de uso digital e mobilidade estão sujeitas à limpeza local após 365 dias; a execução dessas limpezas depende do agendamento do Android. A Jornada e os agregados diários podem permanecer até a exclusão pelo usuário. O aprendizado de rotina é recalculado a partir das observações recentes. Cópias manuais de backup conservam os dados que continham na data da exportação até que esses arquivos sejam apagados separadamente; depois da restauração, os dados voltam a seguir as políticas de manutenção do aplicativo.

Arquivos de backup salvos fora do armazenamento privado não são apagados ao desinstalar ou limpar o aplicativo. A pessoa deve excluí-los no gerenciador de arquivos e nos serviços escolhidos, incluindo lixeira, cópias e retenções do provedor. Compartilhar um arquivo ou encaminhar informações ao suporte é uma decisão da pessoa; o destinatário e o provedor passam a tratar os dados conforme suas condições.

Para revisar ou corrigir registros, use as opções do aplicativo. Para dúvidas sobre tratamento ou exercício de direitos, escreva para o contato acima. Como o Hoodie não mantém uma cópia central da rotina, o suporte não consegue recuperar dados ou senhas perdidos. Mudanças nesta política serão publicadas nesta página com a data atualizada.
