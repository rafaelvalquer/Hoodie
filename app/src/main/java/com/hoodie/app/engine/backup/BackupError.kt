package com.hoodie.app.engine.backup

enum class BackupError(val message: String) {
    PASSWORD("Não foi possível desbloquear o backup. Confira a senha."),
    INVALID_FILE("Este arquivo não é um backup válido do Hoodie."),
    CORRUPTED("O arquivo está corrompido ou foi modificado."),
    INCOMPATIBLE("Este backup precisa de uma versão mais recente do Hoodie."),
    NO_SPACE("Não há espaço suficiente para restaurar os dados com segurança."),
    STORAGE("Não foi possível ler ou salvar o arquivo."),
    INCOMPLETE_DOCUMENT("Não foi possível concluir o backup. Descarte o arquivo incompleto no destino escolhido."),
    CANCELLED("Operação cancelada. Seus dados atuais não foram alterados."),
    VALIDATION("O backup contém dados inconsistentes e não foi restaurado."),
    COORDINATES("Não foi possível recuperar as coordenadas de um lugar. O backup não foi criado."),
    ACTIVATION("A restauração não terminou. O Hoodie tentará recuperar o estado anterior."),
    DATABASE("Os dados locais estão indisponíveis. Você ainda pode restaurar um backup.");

    companion object {
        fun from(exception: Exception): BackupError = when {
            exception is BackupException -> exception.error
            exception is java.io.IOException || exception is SecurityException -> STORAGE
            exception.javaClass.simpleName == "SQLiteFullException" -> NO_SPACE
            else -> VALIDATION
        }
    }
}

class BackupException(val error: BackupError) : Exception(error.message)
internal fun requireBackup(ok: Boolean, error: BackupError = BackupError.VALIDATION) {
    if (!ok) throw BackupException(error)
}
