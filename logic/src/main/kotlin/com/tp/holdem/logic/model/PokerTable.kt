package com.tp.holdem.logic.model

import com.tp.holdem.common.lazyLogger
import com.tp.holdem.common.logger
import com.tp.holdem.logic.*
import com.tp.holdem.common.model.Moves
import com.tp.holdem.common.model.Phase
import io.vavr.collection.HashMap
import io.vavr.collection.List
import io.vavr.collection.Map
import io.vavr.control.Option
import java.util.concurrent.atomic.AtomicLong

data class PokerTable(
        val smallBlindAmount: Int = 0,
        val bigBlindAmount: Int = 0,
        val showdown: Boolean = false,
        val gameOver: Boolean = false,
        val deck: Deck = Deck.brandNew(),
        val phase: Phase = Phase.START,
        private val bettingPlayer: PlayerNumber = PlayerNumber.empty(),
        private val winnerPlayer: PlayerNumber = PlayerNumber.empty(),
        private val dealer: PlayerNumber = PlayerNumber.empty(),
        private val bigBlind: PlayerNumber = PlayerNumber.empty(),
        private val smallBlind: PlayerNumber = PlayerNumber.empty(),
        val allPlayers: List<Player> = List.empty(),
        val cardsOnTable: List<Card> = List.empty(),
        val movesThisPhase: Map<Int, Moves> = HashMap.empty()
) {
    private val log by lazyLogger()

    companion object {
        @JvmStatic
        fun withBlinds(bigBlindAmount: Int, smallBlindAmount: Int): PokerTable {
            return PokerTable(bigBlindAmount = bigBlindAmount, smallBlindAmount = smallBlindAmount)
        }
    }

    val isNotPlayable: Boolean
        get() = allPlayers
                .filter { it.inGame }
                .filter { player -> player.chipsAmount > 0 }
                .length() < 2

    fun addPlayer(player: Player): PokerTable {
        return this.copy(
                allPlayers = allPlayers.append(player)
        )
    }

    fun playerLeft(playerNumber: PlayerNumber): PokerTable {
        val foundPlayer = allPlayers.byNumber(playerNumber)

        val modifiedPlayers = allPlayers
                .replace(foundPlayer, foundPlayer.copy(inGame = false))

        return this.copy(
                allPlayers = modifiedPlayers
        )
    }

    fun highestBetThisPhase(): Int {
        return allPlayers.map { it.betAmountThisPhase }.max().getOrElse(0)
    }

    fun potAmount(): Int {
        return allPlayers.map { it.betAmount }.sum().toInt()
    }

    fun potAmountThisPhase(): Int {
        return allPlayers.map { it.betAmountThisPhase }.sum().toInt()
    }

    fun nextPhase(): PokerTable {
        val nextPhase = phase.nextPhase()
        if (nextPhase == Phase.PRE_FLOP)
            return goToPreFlopPhase()
        if (nextPhase == Phase.FLOP)
            return goToNextPhase(3)
        if (nextPhase == Phase.TURN || nextPhase == Phase.RIVER)
            return goToNextPhase(1)
        throw IllegalStateException("There is no next phase")
    }

    private fun goToPreFlopPhase(): PokerTable {
        val dealerPlayer = getDealer().assertFound()
        val bettingPlayer = dealerPlayer.firstBetInRound(this)

        return this.copy(
                phase = phase.nextPhase(),
                allPlayers = allPlayers.replace(dealerPlayer, bettingPlayer),
                bettingPlayer = PlayerNumber.of(bettingPlayer.number),
                movesThisPhase = HashMap.empty()
        )
    }

    private fun goToNextPhase(cards: Int): PokerTable {
        val preparedPlayers = allPlayers.map<Player> { it.prepareForNewPhase() }

        val updatedTable = this.copy(
                phase = phase.nextPhase(),
                allPlayers = preparedPlayers,
                cardsOnTable = cardsOnTable.appendAll(deck.drawCards(cards)),
                movesThisPhase = movesThisPhase.filterValues { it.goingToNextPhase() }
        )

        return updatedTable.nextPlayerToBetAfter(bigBlind.number)
    }

    fun phaseStatus(): PhaseStatus {
        val notAllInCount = allPlayers
                .filter { it.inGame }
                .count { player -> !player.allIn }

        val allPlayersMoved = movesThisPhase.size() == allPlayers.filter { it.inGame }.size()

        if (notAllInCount <= 1 && allPlayersMoved)
            return PhaseStatus.EVERYBODY_ALL_IN

        val notFoldedCount = allPlayers
                .filter { it.inGame }
                .count { player -> !player.folded }

        if (notFoldedCount == 1)
            return PhaseStatus.EVERYBODY_FOLDED

        val allBetsAreEqual = allPlayers
                .filter { it.playing() }
                .forAll { player -> player.allIn || player.betAmountThisPhase == highestBetThisPhase() }

        return if (allPlayersMoved && allBetsAreEqual) PhaseStatus.READY_FOR_NEXT else PhaseStatus.KEEP_GOING
    }

    fun nextPlayerToBet(): PokerTable {
        return nextPlayerToBetAfter(bettingPlayer.number)
    }

    private fun nextPlayerToBetAfter(playerNumber: Int): PokerTable {
        log.debug(String.format("Finding next to bet after: %d", playerNumber))

        val bettingPlayerIndex = allPlayers.indexOfNumber(playerNumber)
        var newBettingPlayer: Player
        var count = 1
        do {
            newBettingPlayer = allPlayers.get((bettingPlayerIndex + count++) % allPlayers.size())
        } while (newBettingPlayer.folded)

        val modifiedNewBettingPlayer = newBettingPlayer.betInPhase(this)

        return this.copy(
                allPlayers = allPlayers.replace(newBettingPlayer, modifiedNewBettingPlayer),
                bettingPlayer = PlayerNumber.of(modifiedNewBettingPlayer.number)
        )
    }

    fun roundOver(): PokerTable {
        val playersAfterRound = allPlayers.map<Player> { it.roundOver() }

        val updatedTable = this.copy(
                allPlayers = playersAfterRound
        )

        val winner = HandOperations.findWinner(playersAfterRound, updatedTable)

        val prizedWinner = winner.copy(
                chipsAmount = winner.chipsAmount + updatedTable.potAmount()
        )

        return this.copy(
                winnerPlayer = PlayerNumber.of(winner.number),
                allPlayers = updatedTable.allPlayers.replace(winner, prizedWinner),
                phase = Phase.OVER
        )
    }

    fun dealCards(): PokerTable {
        log.debug("Dealing cards to players")
        val playersWithCards = deck.dealCards(2, allPlayers)
        return this.copy(
                allPlayers = playersWithCards
        )
    }

    fun preparePlayersForNewGame(startingChips: Int): PokerTable {
        return this.copy(
                allPlayers = allPlayers.map { player -> player.prepareForNewGame(startingChips) }
        )
    }

    fun getBettingPlayer(): Option<Player> {
        return allPlayers.byNumberOption(bettingPlayer)
    }

    fun getWinnerPlayer(): Option<Player> {
        return allPlayers.byNumberOption(winnerPlayer)
    }

    fun getDealer(): Option<Player> {
        return allPlayers.byNumberOption(dealer)
    }

    fun getBigBlind(): Option<Player> {
        return allPlayers.byNumberOption(bigBlind)
    }

    fun getSmallBlind(): Option<Player> {
        return allPlayers.byNumberOption(smallBlind)
    }

    fun newRound(handCount: AtomicLong): PokerTable {
        log.debug("Preparing players for new round")
        val playersWithCleanBets = allPlayers.map { it.prepareForNewRound() }

        val smallBlindPlayer = playersWithCleanBets.get(((handCount.get() + 1) % playersWithCleanBets.size()).toInt())
        log.debug(String.format("Taking small blind from player: %d", smallBlindPlayer.number))
        val newSmallBlindPlayer = smallBlindPlayer.betSmallBlind(this)

        val dealerPlayer: Player
        if (allPlayers.size() == 2)
            dealerPlayer = newSmallBlindPlayer
        else
            dealerPlayer = playersWithCleanBets.get((handCount.get() % playersWithCleanBets.size()).toInt())

        val bigBlindPlayer: Player
        if (allPlayers.size() == 2)
            bigBlindPlayer = playersWithCleanBets.get((handCount.get() % playersWithCleanBets.size()).toInt())
        else
            bigBlindPlayer = playersWithCleanBets.get(((handCount.get() + 2) % playersWithCleanBets.size()).toInt())

        log.debug(String.format("Taking big blind from player: %d", bigBlindPlayer.number))
        val newBigBlindPlayer = bigBlindPlayer.betBigBlind(this)

        val updatedTable = this.copy(
                deck = Deck.brandNew(),
                cardsOnTable = List.empty(),
                phase = Phase.START,
                showdown = false,
                movesThisPhase = HashMap.empty(),
                allPlayers = playersWithCleanBets.replace(smallBlindPlayer, newSmallBlindPlayer).replace(bigBlindPlayer, newBigBlindPlayer),
                winnerPlayer = PlayerNumber.empty(),
                bettingPlayer = PlayerNumber.empty(),
                dealer = PlayerNumber.of(dealerPlayer.number),
                bigBlind = PlayerNumber.of(newBigBlindPlayer.number),
                smallBlind = PlayerNumber.of(newSmallBlindPlayer.number)
        )

        return updatedTable.dealCards()
    }

    fun playerMove(playerNumber: Int, move: Moves, betAmount: Int): PokerTable {
        val actionPlayer = allPlayers.byNumber(playerNumber)

        val playerAfterAction: Player

        when (move) {
            Moves.FOLD -> playerAfterAction = actionPlayer.fold()
            Moves.ALLIN -> playerAfterAction = actionPlayer.allIn()
            Moves.BET -> playerAfterAction = actionPlayer.bet(betAmount)
            Moves.CHECK -> playerAfterAction = actionPlayer
            Moves.CALL -> playerAfterAction = actionPlayer.bet(highestBetThisPhase() - actionPlayer.betAmountThisPhase)
            Moves.RAISE -> playerAfterAction = actionPlayer.bet(highestBetThisPhase() - actionPlayer.betAmountThisPhase).bet(betAmount)
            else -> throw IllegalArgumentException(String.format("Unsupported action type: %s", move))
        }

        return this.copy(
                allPlayers = allPlayers.replace(actionPlayer, playerAfterAction.bettingTurnOver()),
                movesThisPhase = movesThisPhase.put(actionPlayer.number, move)
        )
    }

    fun gameOver(): PokerTable {
        return this.copy(
                gameOver = true,
                phase = Phase.OVER,
                allPlayers = allPlayers.map { it.gameOver() }
        )
    }

    fun showdownMode(): PokerTable {
        return this.copy(
                showdown = true
        )
    }
}