package com.hoodie.app.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hoodie.app.MainActivity
import com.hoodie.app.R
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.receiver.QuestionActionReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** O que as engines precisam para falar com o usuário (fake nos testes). */
interface Notifier {
    suspend fun event(text: String)
    suspend fun askYesNo(questionId: Long, text: String)
    suspend fun askInApp(questionId: Long, text: String)
    fun cancelQuestion(questionId: Long)
    fun cancelAll()
}

/** Poucas notificações e boas: chegadas, perguntas de confirmação e (no máximo 1/dia) vida própria. */
@Singleton
class HoodieNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : Notifier {
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_EVENTS, "Rotina do Hoodie", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Chegadas, saídas e o que o Hoodie decidiu fazer."
        })
        nm.createNotificationChannel(NotificationChannel(CH_QUESTIONS, "Confirmações", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Perguntas rápidas para o Hoodie entender sua rotina."
        })
    }

    private suspend fun allowed(): Boolean {
        if (!settings.current().notificationsEnabled) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun openApp(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun event(text: String) {
        if (!allowed()) return
        val n = NotificationCompat.Builder(context, CH_EVENTS)
            .setSmallIcon(R.drawable.ic_stat_hoodie)
            .setContentTitle(settings.current().catName)
            .setContentText(text)
            .setContentIntent(openApp(1))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_EVENT, n)
    }

    /** Pergunta Sim/Não respondível direto da notificação. */
    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun askYesNo(questionId: Long, text: String) {
        if (!allowed()) return
        fun action(yes: Boolean) = PendingIntent.getBroadcast(
            context, (questionId * 2 + if (yes) 1 else 0).toInt(),
            Intent(context, QuestionActionReceiver::class.java)
                .putExtra(QuestionActionReceiver.EXTRA_QUESTION, questionId)
                .putExtra(QuestionActionReceiver.EXTRA_YES, yes),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CH_QUESTIONS)
            .setSmallIcon(R.drawable.ic_stat_hoodie)
            .setContentTitle(settings.current().catName)
            .setContentText(text)
            .setContentIntent(openApp(2))
            .addAction(0, "Sim", action(true))
            .addAction(0, "Não", action(false))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(questionNotificationId(questionId), n)
    }

    /** Pergunta que precisa de escolha (lugar novo): abre o app. */
    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun askInApp(questionId: Long, text: String) {
        if (!allowed()) return
        val n = NotificationCompat.Builder(context, CH_QUESTIONS)
            .setSmallIcon(R.drawable.ic_stat_hoodie)
            .setContentTitle(settings.current().catName)
            .setContentText(text)
            .setContentIntent(openApp(3))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(questionNotificationId(questionId), n)
    }

    override fun cancelQuestion(questionId: Long) = NotificationManagerCompat.from(context).cancel(questionNotificationId(questionId))

    /** Delete Everything: nenhuma notificação pendente pode sobreviver aos dados. */
    override fun cancelAll() = NotificationManagerCompat.from(context).cancelAll()

    private fun questionNotificationId(questionId: Long) = 10_000 + (questionId % 50_000).toInt()

    companion object {
        const val CH_EVENTS = "hoodie_events"
        const val CH_QUESTIONS = "hoodie_questions"
        private const val ID_EVENT = 1
    }
}
