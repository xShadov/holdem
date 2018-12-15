package com.tp.holdem.model.game;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class HandRank {
	private int playerNumber;
	private Hands hand;
	private List<Card> cardsThatMakeDeck;
}