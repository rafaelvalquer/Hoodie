# RELEASE-05 — preparação da Google Play

Estado: **documentação preparada; envio e revisão da Console pendentes**. Conta ainda indisponível. Identificação de contato do responsável e suporte: rafael.valquer@gmail.com.

Em 19/10/2026, ao ser consultado sobre a identificação do responsável na política, o responsável reiterou `rafael.valquer@gmail.com`. Manter esse endereço como identificação de contato na política, conforme informado; a identificação cadastral da futura conta Play permanece a preencher na abertura da conta.

Política: [texto](../../privacy-policy.md). URL HTTPS de referência: https://github.com/rafaelvalquer/Hoodie/blob/main/docs/privacy-policy.md. Conferir após o push que a versão atual abre sem login, tanto no navegador quanto pelo botão Privacidade das Configurações. Se a Console exigir outro formato/endereço, publicar HTML HTTPS estável e atualizar ambos os links antes do envio.

## Segurança dos Dados — inventário para declaração

Não enviar esta matriz automaticamente como formulário. Validar tráfego da variante release e as definições atuais da Console, incluindo provedores externos e tratamento temporário.

| Categoria | Uso no Hoodie | Armazenamento/saída | Escolha da pessoa |
|---|---|---|---|
| Localização aproximada/precisa | Lugares, chegadas/saídas, Jornada | Local cifrado; mapa consulta tiles/região; geocodificação envia busca/coordenadas ao provedor Android | Permissão foreground/background; mapa/busca iniciados na UI |
| Atividade física | Inferir deslocamento/modo | Estado local; APIs Android/Play Services | Permissão de reconhecimento |
| Uso de apps/tela | Registros digitais opcionais | Local; backup somente se optado | Acesso especial + interruptor + apagar histórico |
| Atividades/rotina, lugares, memórias, preferências | Diário e personalização | Local; arquivo cifrado escolhido no SAF | Edição/exclusão/backup manual |
| IP e região de mapa | Tiles OpenStreetMap | Provedor externo de mapas | Ao abrir o mapa online |
| Texto de endereço/coordenadas | Geocoder Android | Provedor do sistema | Busca/reverso no seletor |
| Mensagem ao suporte | Responder solicitação | E-mail Gmail informado pelo usuário | Envio voluntário; não pedir dados sensíveis |

Não há login, servidor próprio de rotina, SDK de anúncios ou analytics comportamental. Isso não elimina a necessidade de avaliar os fluxos externos. Backup selecionado em um provedor de nuvem é compartilhamento iniciado pelo usuário; conferir tratamento declarado pelo provedor e definições da Play. A chave local não acompanha o backup; a senha de recuperação é necessária. Sem funcionalidade de exclusão de conta inexistente; exclusão local deve funcionar.

## Localização em segundo plano

Finalidade central proposta: “Reconhecer chegada e saída dos lugares cadastrados para construir automaticamente a Jornada do dia, mesmo quando o aplicativo não está aberto.” Solicitar somente após explicação clara e escolha da pessoa. Não prometer rastreamento instantâneo. Demonstrar no vídeo:

1. Abrir onboarding/configurações e ler divulgação destacada, antes da permissão.
2. Negar e comprovar uso manual/fallback sem fingir monitoramento ativo.
3. Conceder foreground e depois background no fluxo apropriado do Android.
4. Casa/Trabalho cadastrados, app fechado, chegada/saída real e Jornada resultante.
5. Revogar background e mostrar estado limitado; apagar dados locais.

Gravar em aparelho de teste com dados fictícios; não exibir endereços reais. Anexar URL de vídeo acessível aos revisores e registrar decisão da Console. O material ainda não foi gravado/aceito.

## Ficha da loja (rascunho)

Texto curto e descrição completa preparados em [ficha pt-BR](store-listing-pt-BR.md). A versão atual do texto público omite o backup; um parágrafo separado só pode entrar após homologação e ativação na variante enviada.

- Nome: Hoodie.
- Descrição curta proposta: “Seu companheiro de rotina, com Diário e memórias guardados no aparelho.”
- Descrição: personagem que acompanha contextos do dia, Jornada/Diário, lugares e permissões opcionais. Apresentar backup somente quando habilitado e homologado na versão enviada.
- Suporte: rafael.valquer@gmail.com.
- Categoria, público-alvo, classificação indicativa, países e política de anúncios: preencher e revisar na Console; não inferir classificação pela pixel art.
- Ícone, screenshots finais em aparelhos e licenças de imagens/fontes/sons: inventariar e aprovar; não usar capturas de testes ou materiais sem comprovação de direitos como aprovação de loja.

## Aceite a executar

Política sem login e link no app; tráfego observado comparado com matriz; consentimento e recusa; exclusão de todos os dados; desativação digital; manifesto final e permissões especiais; formulários preenchidos; assets/licenças; vídeo quando exigido; decisão da Play. Registrar versão/SHA, data e evidência de cada item.

Referências: [preparar revisão](https://support.google.com/googleplay/android-developer/answer/9859455?hl=pt-BR), [Segurança dos Dados](https://support.google.com/googleplay/android-developer/answer/10787469?hl=pt-BR), [localização em background](https://support.google.com/googleplay/android-developer/answer/9799150?hl=pt-BR).
