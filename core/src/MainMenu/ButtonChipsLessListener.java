package MainMenu;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

class ButtonChipsLessListener implements ActionListener
{
	private MainMenu menu;
	public ButtonChipsLessListener(MainMenu menu)
	{
		this.menu=menu;
	}
	public void actionPerformed(ActionEvent e)
	{
		if(menu.getChipsAmount()>1000)
		{
			menu.setChipsAmount(menu.getChipsAmount()-250);
			menu.chipsAmount.setText("Starting chips: "+String.valueOf(menu.getChipsAmount()));
		}
	}
	
};