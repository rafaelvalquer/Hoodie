package com.hoodie.app.core.deviceusage

enum class UsagePermissionState {
    /** "Acesso ao uso" liberado nas configurações do Android. */
    GRANTED,
    /** Ainda não liberado (ou revogado). */
    DENIED,
    /** Aparelho sem a tela de acesso ao uso (perfis restritos, alguns fabricantes). */
    UNAVAILABLE,
}
