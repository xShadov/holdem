package MainMenu;

import javax.swing.JButton;

public class ButtonChipsLess extends JButton {
	public ButtonChipsLess(MainMenu menu) {
		super("<");
		addActionListener(new ButtonChipsLessListener(menu));
	}
}