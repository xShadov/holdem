package com.tp.holdem.model.common;

public enum Moves {
	BET(false),
	RAISE(false),
	FOLD(true),
	CHECK(false),
	CALL(false),
	ALLIN(true);

	private boolean goingToNextPhase;

	Moves(boolean goingToNextPhase) {
		this.goingToNextPhase = goingToNextPhase;
	}

	public boolean goingToNextPhase() {
		return goingToNextPhase;
	}
}
