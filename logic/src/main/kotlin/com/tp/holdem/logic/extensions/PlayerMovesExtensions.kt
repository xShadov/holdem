package com.tp.holdem.logic.extensions

import com.tp.holdem.model.Player

fun Player.allIn(): Player {
    return this.copy(
            allIn = true,
            betAmountThisPhase = availableChips()
    )
}

fun Player.fold(): Player {
    return this.copy(
            folded = true
    )
}

fun Player.bet(bet: Int): Player {
    if (availableChips() < bet)
        throw IllegalArgumentException("Player does not have enough chips: ${availableChips()} vs. $bet")

    val newBetAmount = betAmountThisPhase + bet
    return this.copy(
            betAmountThisPhase = newBetAmount,
            allIn = availableChips() - newBetAmount == 0
    )
}