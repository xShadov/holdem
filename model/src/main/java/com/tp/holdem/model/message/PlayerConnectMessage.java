package com.tp.holdem.model.message;

import com.tp.holdem.model.message.dto.CurrentPlayerDTO;
import com.tp.holdem.model.message.dto.PlayerDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerConnectMessage implements ServerMessage {
	private boolean success;
	private CurrentPlayerDTO player;

	public static PlayerConnectMessage success(CurrentPlayerDTO player) {
		return new PlayerConnectMessage(true, player);
	}

	public static PlayerConnectMessage failure() {
		return new PlayerConnectMessage(false, null);
	}
}
