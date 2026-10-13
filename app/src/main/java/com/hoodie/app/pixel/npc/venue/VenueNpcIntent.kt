package com.hoodie.app.pixel.npc.venue

/** Fase de um personagem de estabelecimento num instante (o que o renderer e os testes leem). */
enum class VenueNpcState {
    /** Atravessando a porta / chegando pela borda. */
    ENTERING,

    /** Andando pelo grafo da cena até um ponto. */
    NAVIGATING,

    /** Parado esperando: fila, pedido, alguém, a sessão começar. */
    WAITING,

    /** Fazendo a atividade do ponto: comer, ler, olhar a vitrine, repor a prateleira. */
    PERFORMING_ACTION,

    /** Atendendo ou sendo atendido, conversando, trocando a bola. */
    INTERACTING,

    /** Indo embora pela porta / borda. */
    EXITING,

    /** Fora de cena (antes de voltar, dentro de uma loja, no elevador). Não é desenhado. */
    OFFSTAGE,
}

/** Função fixa do personagem na cena. Funcionários têm posto; clientes vêm e vão. */
enum class VenueRole(val staff: Boolean = false) {
    // Padaria / cafeteria
    BARISTA(staff = true), CAFE_CUSTOMER, QUICK_BUYER, CAFE_READER,

    // Farmácia
    PHARMACY_CLERK(staff = true), PHARMACY_CUSTOMER,

    // Praia
    BEACH_WALKER, SUNBATHER, BALL_PLAYER, ICE_CREAM_VENDOR(staff = true),

    // Shopping
    MALL_SHOPPER, MALL_SOCIAL, MALL_CALM,

    // Cinema
    POPCORN_ATTENDANT(staff = true), MOVIEGOER, MOVIEGOER_SNACKER, MOVIEGOER_LATE,

    // Hotel
    RECEPTIONIST(staff = true), HOTEL_GUEST, HOTEL_VISITOR,
}

/**
 * Atividades que um personagem escolhe por peso (perfil) e restrições (ocupação, cooldown,
 * histórico). Cada cena usa só as que fazem sentido para ela.
 */
enum class VenueAction {
    // Comuns
    BROWSE, ORDER, CONSUME, READ, PHONE, REST, SOCIAL, OBSERVE, PHOTO, STROLL, EXIT,

    // Estabelecimentos com balcão
    SERVE, PREPARE, CLEAN, RESTOCK, PAY,

    // Shopping
    WINDOW_SHOP, ENTER_STORE,

    // Praia
    SUNBATHE, PLAY_BALL, BUY_ICE_CREAM,

    // Cinema / hotel
    WATCH, CHECK_IN, ELEVATOR, WAIT,
}

/** Eventos de objetos compartilhados emitidos em momentos definidos das ações (seção 2.4 do plano). */
enum class VenueEvent {
    ORDER_PLACED, ORDER_TAKEN, COFFEE_READY, CUP_PICKED, CUP_RETURNED, PASTRY_PICKED,
    SHOPPING_BAG_TAKEN, POPCORN_PICKED, SUITCASE_PICKED, SUITCASE_DOWN, KEYCARD_TAKEN,
    SEAT_RESERVED, SEAT_RELEASED, PRODUCT_TAKEN, SHELF_RESTOCKED, COUNTER_CLEANED,
    ICE_CREAM_SERVED, BALL_THROWN, BALL_CAUGHT, PHOTO_TAKEN, ELEVATOR_CALLED,
}
