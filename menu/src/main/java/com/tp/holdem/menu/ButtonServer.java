package com.tp.holdem.menu;

import javax.swing.JButton;

public class ButtonServer extends JButton {
	public ButtonServer(MainMenu menu) {
		super("START SERVER");
		addActionListener(new ButtonServerListener(menu));
	}
};