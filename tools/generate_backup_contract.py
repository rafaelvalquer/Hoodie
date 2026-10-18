"""One-time V1 contract authoring tool. Runtime NEVER reads Room schemas.
Review and freeze the generated contract; future Room changes require explicit adapters.
"""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
schema = json.loads((root / 'app/schemas/com.hoodie.app.core.database.HoodieDatabase/12.json').read_text())['database']
out = root / 'app/src/main/java/com/hoodie/app/engine/backup'
excluded = {'hoodie_state', 'day_state', 'context_questions', 'correction_undo', 'notification_delivery'}
digital = {'daily_device_usage', 'daily_app_usage', 'daily_context_app_usage', 'daily_screen_hourly', 'daily_context_usage', 'daily_phone_timeline', 'phone_app_sessions', 'app_category_overrides'}
names = {'places':'Place','routines':'Routine','context_events':'ContextEvent','memories':'Memory','mobility_sessions':'MobilitySession'}
lines = ['package com.hoodie.app.engine.backup', '', '/** Frozen logical V1 DTOs. Adapt explicitly when the internal Room schema changes. */', 'sealed interface BackupRecordV1 { fun json(): org.json.JSONObject }', '']
specs=[]
for e in schema['entities']:
    table=e['tableName']
    if table in excluded: continue
    fields=[dict(f) for f in e['fields'] if not (table=='places' and f['fieldPath']=='encryptedCoordinates')]
    if table=='places':
        fields += [dict(fieldPath=n,columnName=n,affinity='REAL',notNull=True) for n in ('latitude','longitude')]
    for f in fields: f.setdefault('notNull', False)
    model='Backup'+names.get(table, ''.join(w.title() for w in table.split('_')))+'V1'
    lines.append(f'data class {model}(')
    for f in fields:
        typ={'INTEGER':'Long','TEXT':'String','REAL':'Double'}[f['affinity']] + ('' if f['notNull'] else '?')
        lines.append(f'    val {f["fieldPath"]}: {typ},')
    lines += [') : BackupRecordV1 {', '    override fun json() = org.json.JSONObject().apply {']
    for f in fields: lines.append(f'        put("{f["fieldPath"]}", {f["fieldPath"]} ?: org.json.JSONObject.NULL)' if not f['notNull'] else f'        put("{f["fieldPath"]}", {f["fieldPath"]})')
    lines += ['    }', '}', '']
    fs = ', '.join(f'BackupField("{f["fieldPath"]}", "{f["columnName"]}", BackupValueType.{f["affinity"]}, {str(f["notNull"]).lower()})' for f in fields)
    keys=', '.join('"'+x+'"' for x in e['primaryKey']['columnNames'])
    specs.append(f'        BackupDataset("{table}", listOf({fs}), listOf({keys}), {str(table in digital).lower()}),')
(out/'BackupModelsV1.kt').write_text('\n'.join(lines), encoding='utf-8')
(out/'BackupSchemaV1.kt').write_text('''package com.hoodie.app.engine.backup

enum class BackupValueType { INTEGER, REAL, TEXT }
data class BackupField(val name: String, val column: String, val type: BackupValueType, val required: Boolean)
data class BackupDataset(val name: String, val fields: List<BackupField>, val primaryKeys: List<String>, val digital: Boolean, val table: String = name)

/** Frozen V1 wire schema, deliberately independent from the current SQLite schema. */
object BackupSchemaV1 {
    val datasets = listOf(
'''+ '\n'.join(specs)+'''
    )
    val reconstructed = setOf("hoodie_state", "day_state")
    val excluded = setOf("context_questions", "correction_undo", "notification_delivery")
    val localEncryptedFields = setOf("places.encryptedCoordinates", "context_questions.encryptedCoordinates")
}
''', encoding='utf-8')
doc=['# HOODIE-BACKUP-V1 — cobertura Room v12', '', '| Entidade | Tratamento |', '|---|---|']
for e in schema['entities']:
    t=e['tableName']
    action='Reconstruída no destino' if t in {'hoodie_state','day_state'} else 'Excluída: estado temporário' if t in excluded else 'Exportada, conforme opção de histórico digital' if t in digital else 'Exportada'
    if t=='places': action+='; coordenadas decifradas em memória e recifradas no destino'
    if t in {'context_events','mobility_sessions','mobility_segments','transport_evidence'}: action+='; intervalos abertos encerrados no corte do snapshot'
    doc.append(f'| `{t}` | {action} |')
doc += ['', 'Perguntas são temporárias; respostas confirmadas permanecem em context_confirmations e diary_corrections.', 'TransportEvidence preserva agregados e descarta reservas/IDs de WorkManager. O estado da mobilidade vira ARRIVED.', 'Os campos dependentes de chave local são places.encryptedCoordinates e context_questions.encryptedCoordinates; o segundo não é exportado.', '', 'O contrato é lógico e congelado em BackupSchemaV1/BackupModelsV1. Não serializa o arquivo SQLite nem lê seu schema como contrato em runtime. Novas colunas exigem revisão explícita do adaptador e do teste de cobertura.']
(root/'docs/validation/hoodie-backup-v1-coverage.md').write_text('\n'.join(doc)+'\n', encoding='utf-8')
