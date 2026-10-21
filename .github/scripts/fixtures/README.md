# Saídas reais de diagnóstico

Os arquivos `upgrade-api*-package.txt`, `upgrade-api*-uid.txt`, `upgrade-api*-launch.txt` e `upgrade-api*-keyboard-*.txt` preservam saídas de comandos dos AVDs descartáveis da CI. Os relatórios em `docs/validation/release-v1/` vinculam os arquivos aos artefatos e hashes originais.

Somente CRLF foi convertido para LF. Espaços finais e linhas vazias finais das saídas de `dumpsys window` são intencionais e permanecem para preservar a evidência. Esses dois arquivos de janela são excluídos individualmente da verificação local de whitespace; o código e os demais arquivos continuam verificados. Não são scripts executáveis nem baselines visuais.
