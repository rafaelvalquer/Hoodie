package com.hoodie.app.worker

/** Uma atualização diária persistida; falhas ainda podem ser recuperadas no mesmo dia. */
object GeofenceRegistrationPolicy {
    fun shouldRegister(hour: Int, today: Long, lastDay: Long, lastSucceeded: Boolean?): Boolean =
        lastSucceeded == false || (lastDay != today && (hour == 4 || lastSucceeded == null))
}
