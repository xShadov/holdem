package com.tp.holdem.logic.model

import com.tp.holdem.common.model.Moves
import io.vavr.collection.List

data class Player(
        internal val number: Int = -1,

        internal val minimumBet: Int = 0,
        internal val maximumBet: Int = 0,
        internal val betAmount: Int = 0,
        internal val betAmountThisPhase: Int = 0,
        internal val chipsAmount: Int = 0,

        internal val inGame: Boolean = false,
        internal val allIn: Boolean = false,
        internal val folded: Boolean = false,

        internal val name: String = "Player$number",
        internal val possibleMoves: List<Moves> = List.empty(),
        internal val hand: List<Card> = List.empty()
) {
    companion object {
        @JvmStatic
        fun numbered(number: Int): Player {
            return Player(
                    number = number
            )
        }
    }
}