package com.tp.holdem.model


import com.tp.holdem.common.model.Honour
import com.tp.holdem.common.model.Suit
import io.vavr.collection.List

data class Deck(
        val cards: List<Card> = List.of(*Honour.values())
                .flatMap { honour ->
                    List.of(
                            Card.from(Suit.HEART, honour),
                            Card.from(Suit.CLUB, honour),
                            Card.from(Suit.DIAMOND, honour),
                            Card.from(Suit.SPADE, honour)
                    )
                }
                .shuffle()
) {
    companion object {
        @JvmStatic
        fun brandNew(): Deck {
            return Deck()
        }
    }
}
