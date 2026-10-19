Descreva o problema, o comportamento final e a validação relevante.

Se alterar Room, entidades, migrações, DataStore ou o contrato de backup:

- [ ] Revisar o mapeamento lógico V1 e documentar a compatibilidade.
- [ ] Importar o fixture permanente `backup-v1-minimal.hoodiebackup`.
- [ ] Preservar dados e relações de instalações anteriores.
- [ ] Registrar evidências no commit que será promovido.

A CI exige uma revisão em `docs/backup/contract-reviews/<alteracao>.md` ou uma atualização técnica de `docs/backup/FORMAT-V1.md` para mudanças em Room, DataStore ou serialização do backup. Registre campos afetados, compatibilidade V1, adapters necessários e evidência de importação. O fixture original não pode ser substituído.

Não marcar homologação física, beta ou aprovação Play sem a evidência correspondente.
