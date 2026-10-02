package com.hoodie.app.core.deviceusage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PACKAGE_USAGE_STATS não é um popup: o usuário liga "Acesso ao uso" numa tela
 * especial do sistema. Aqui só checamos o AppOp e montamos o Intent da tela.
 */
/** O que o repositório precisa saber da permissão (fake nos testes). */
fun interface UsageAccessChecker {
    fun isGranted(): Boolean
}

@Singleton
class UsageAccessManager @Inject constructor(@ApplicationContext private val context: Context) : UsageAccessChecker {
    private val _state = MutableStateFlow(check())
    val state: StateFlow<UsagePermissionState> = _state.asStateFlow()

    /** Relê o AppOp (chamar ao voltar das configurações). */
    fun refresh(): UsagePermissionState = check().also { _state.value = it }

    override fun isGranted(): Boolean = refresh() == UsagePermissionState.GRANTED

    fun appDetailsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Tela "Acesso ao uso", já apontando para o Hoodie quando o sistema aceita. */
    fun settingsIntent(): Intent {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (direct.resolveActivity(context.packageManager) != null) return direct
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun check(): UsagePermissionState {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return UsagePermissionState.UNAVAILABLE
        val mode = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION") // substituto só existe em APIs novas; o comportamento é o mesmo
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
        }.getOrElse { return UsagePermissionState.UNAVAILABLE }
        return if (mode == AppOpsManager.MODE_ALLOWED) UsagePermissionState.GRANTED else UsagePermissionState.DENIED
    }
}
