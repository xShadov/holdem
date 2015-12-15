package com.tp.holdem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;

public class KryoServer implements Runnable {
	
	private int port = 54555;
	private Server server;
	public boolean running = true;
	private volatile boolean gameStarted = false;
	private List<Player> players = new ArrayList<Player>();
	private List<Player> playersWithHiddenCards;
	private int playersCount; //ustalona ilosc graczy przy stole
	private int botsCount;
	private int fixedChips;
	private int fixedRaise;
	private String limitType; // zasada stolu ("no-limit", "fixed-limit", "pot-limit")
	private Strategy botStrategy;
	private int numPlayers = 0;
	private int lastToBet = 0;
	private long startTimer, endTimer;
	private int turnPlayer = 0; 
	private int smallBlindPlayer = 0;
	private int bigBlindPlayer = 0;
	private int betPlayer = 0;
	private List<String> possibleOptions;
	private boolean bidingOver = false;
	private SampleRequest request;
	private boolean flopTime = false;
	private int handsCounter = 0;
	private int bidingCount = 0;
	private boolean turnTime = false;
	private boolean riverTime = false;
	private boolean waitingForPlayerResponse = false;
	private Deck deck = null;
	private PokerTable pokerTable = null;
	private boolean bidingTime = false;
	private int startingSmallBlindAmount;
	private int smallBlindAmount;
	private int bigBlindAmount;
	private int maxBetOnTable = 0;
	private int playersChips;
	private boolean newHand = false;
	private SampleResponse response;
		
	public KryoServer(){
		
	}
	
	public KryoServer(final int playersCount, int botsCount, String limitType, Strategy botStrategy,int blindA,int playersChips, int fixedChips,int fixedRaise) throws Exception {
		
	  this.playersCount=playersCount;
	  this.botsCount=botsCount;
	  this.limitType=limitType;
	  this.botStrategy=botStrategy;
	  this.startingSmallBlindAmount=blindA;
	  this.playersChips=playersChips;
	  this.fixedChips=fixedChips;
	  this.fixedRaise=fixedRaise;
	  this.smallBlindAmount = Integer.valueOf(startingSmallBlindAmount);
	  this.bigBlindAmount = smallBlindAmount*2;

      server = new Server();

      Thread gameThread = new Thread(this);
  
      Kryo kryo = server.getKryo();
      kryo.register(SampleResponse.class);
      kryo.register(SampleRequest.class);
      kryo.register(ArrayList.class);
      kryo.register(List.class);
      kryo.register(Player.class);
      kryo.register(Card.class);
      kryo.register(Deck.class);
      kryo.register(PokerTable.class);
      
      
      server.addListener(new Listener() { 
          public synchronized void received (Connection connection, Object object) { 
        	  handleReceived(object); 
          }
          
          public synchronized void connected(Connection con){
        	  if(players.size()<playersCount){
          		  handleConnected(con);
          	  }
          	  else
          	  {
          		  con.close();
          	  }
          }

          public synchronized void disconnected(Connection con){
        	  handleDisconnected(con);
          }
          
      });
      
      server.bind(port);
      server.start();
      gameThread.start();
      // keep server running
      while (running) {
         Thread.sleep(100);
      }
	}
	
	@Override
	public synchronized void run() {
		while(true){
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if(gameStarted){
				if(newHand){
					initiateNewHand(players);
				}
				
				if(bidingTime){

					if(!waitingForPlayerResponse && !bidingOver){
						if(everyoneFoldedExceptBetPlayer(players)){
							timeToCheckWinner(players, pokerTable);
							resetAfterRound(players);
						}
						else{
							response = new SampleResponse("T", pokerTable);
							server.sendToAllTCP(response);
							sendBetResponse(betPlayer, players);
							if(botsCount>0)
							{
								if(betPlayer > playersCount-1) // bots turn
								{
									if(!players.get(betPlayer).isFolded())
									{
										players.get(betPlayer).getStrategy().whatDoIDo(this,players.get(betPlayer).getHand(),players.get(betPlayer).getBetAmountThisRound(),players.get(betPlayer).getChipsAmount());		
									}
								}
							}
						}
					}
					else if(waitingForPlayerResponse){
						endTimer = System.nanoTime();
						if((endTimer-startTimer)/1000000000>25){
							request = new SampleRequest("FOLD", betPlayer);
							handleReceived(request);
						}
					}
					if(bidingOver){
						maxBetOnTable = 0;
						pokerTable.setRaiseCount(0);
						for(int i=0; i<players.size();i++){
							players.get(i).setBetAmountThisRound(0);
						}
						bidingTime = false;
						bidingCount++;
						if(bidingCount-1==1) {flopTime = true; System.out.println("flop");}
						else if(bidingCount-1==2) {turnTime = true; System.out.println("turn");}
						else if(bidingCount-1==3) {riverTime = true; System.out.println("river");}
						else if(bidingCount-1==4) {
							timeToCheckWinner(players, pokerTable); 
							System.out.println("koniec rundy");
							resetAfterRound(players);
							}
					}
				}
				
				for(int i=0; i<players.size(); i++){
					playersWithHiddenCards.get(i).setAllProperties(players.get(i));
				}
				response = new SampleResponse("R", playersWithHiddenCards);
				server.sendToAllTCP(response);
				
				//flop - 3 karty na stol
				if(flopTime){
					flopTime = false;
					pokerTable.addCard(deck.drawCard());
					pokerTable.addCard(deck.drawCard());
					pokerTable.addCard(deck.drawCard());
					bidingTime = true;
					bidingOver = false;
					setFirstAndLastToBet(players);
				}
				//turn
				if(turnTime){
					turnTime = false;
					pokerTable.addCard(deck.drawCard());
					bidingTime = true;
					bidingOver = false;
					setFirstAndLastToBet(players);
				}
				//river
				if(riverTime){
					riverTime = false;
					pokerTable.addCard(deck.drawCard());
					bidingTime = true;
					bidingOver = false;
					setFirstAndLastToBet(players);
				}
				//TableInfo.cardsOnTable=table.getCardList();
				response = new SampleResponse("T", pokerTable);
				server.sendToAllTCP(response);
			}
		}
	}

	private boolean everyoneFoldedExceptBetPlayer(List<Player> players) {
		int countFolded = 0;
		for(int i=0; i<players.size();i++){
			if(players.get(i).isFolded()) countFolded++;
		}
		return countFolded==players.size()-1;
	}

	private List<Player> timeToCheckWinner(List<Player> players, PokerTable pokerTable) {
		List<HandRank> hands = new ArrayList<HandRank>();
		List<Player> winners = new ArrayList<Player>();
		for(int i=0; i<players.size(); i++){
			if(!players.get(i).isFolded() && players.get(i).isInGame()){
				hands.add(HandOperations.findHandRank(players.get(i).getNumber(), players.get(i).getHand(), pokerTable.getCardList()));
				players.get(i).setHandRank(HandOperations.findHandRank(players.get(i).getNumber(), players.get(i).getHand(), pokerTable.getCardList()));
			}
		}
		HandRankComparator handComparator = new HandRankComparator();
		Collections.sort(hands, handComparator);
		if(hands.size()>1){
			for(int i=hands.size()-1; i>0; i--){
				if(players.get(hands.get(i).getPlayerNumber()).getBetAmountThisRound()==maxBetOnTable){
					if(handComparator.compare(hands.get(i), hands.get(i-1))==1){
						for(int j=0; j<i; j++){
							hands.remove(0);
						}
						break;
					}
				}
			}
		}
		int currentPotNumber = 0;
		for(int i=0; i<hands.size(); i++){
			winners.add(0, players.get(hands.get(i).getPlayerNumber()));
		}
		Collections.sort(winners, new BetComparator());
		for(int i=0; i<winners.size(); i++){
			winners.get(i).setFromWhichPot(currentPotNumber);
			if(i<winners.size()-1){
				if(winners.get(i).getBetAmount()!=winners.get(i+1).getBetAmount()) currentPotNumber++;
			}
		}
		if(winners.size()==1){
			//OW - one winner
			players.get(winners.get(0).getNumber())
				.setChipsAmount(players.get(winners.get(0).getNumber()).getChipsAmount() + pokerTable.getPot());
			response = new SampleResponse("OW", winners.get(0).getNumber());
		}
		else{
			//MW - multiple winners
			int howManyInPreviousPots = 0;
			for(int i=0; i<=currentPotNumber; i++){
				int howManyPeopleInSamePot = howManyPeopleInSamePot(i, players);
				howManyInPreviousPots+=howManyPeopleInSamePot;
				int howToSplit = 0;
				for(int j=howManyInPreviousPots-howManyPeopleInSamePot; j<howManyInPreviousPots; j++){
					if(j<winners.size()-1){
						if(handComparator.compare(winners.get(j).getHandRank(), winners.get(j+1).getHandRank())==1){
							howToSplit++;
							break;
						} else {
							howToSplit++;
						}
					} else {
						if(handComparator.compare(winners.get(j).getHandRank(), winners.get(j-1).getHandRank())!=1){
							howToSplit++;
						}
					}
				}
				int howMuchPerOne = findAmountChipsForAllInPot(i, winners.get(howManyInPreviousPots-1).getBetAmount(), players)/howToSplit;
				for(int j=howManyInPreviousPots-howManyPeopleInSamePot; j<howManyInPreviousPots-howManyPeopleInSamePot+howToSplit; j++)
				{
					players.get(winners.get(j).getNumber())
						.setChipsAmount(players.get(winners.get(j).getNumber()).getChipsAmount() + howMuchPerOne);
				}
			}
			response = new SampleResponse("MW");
		}
		server.sendToAllTCP(response);
		try {
			Thread.sleep(2500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return players;
	}

	private int findAmountChipsForAllInPot(int potNumber, int betAmount, List<Player> players) {
		int wholeAmount = 0;
		for(int i=0; i<players.size(); i++){
			if(players.get(i).getFromWhichPot()>=potNumber || players.get(i).getFromWhichPot()==-1)
			{
				if(players.get(i).getBetAmount()<betAmount){
					wholeAmount+=players.get(i).getBetAmount();
					players.get(i).setBetAmount(0);
				} else {
					wholeAmount+=betAmount;
					players.get(i).setBetAmount(players.get(i).getBetAmount()-betAmount);
				}
			}
		}
		return wholeAmount;
	}
	
	private int howManyPeopleInSamePot(int fromWhichPot, List<Player> players) {
		int amount = 0;
		for(int i=0; i<players.size();i++){
			if(players.get(i).isInGame() && !players.get(i).isFolded()){
				if(players.get(i).getFromWhichPot()==fromWhichPot) amount++;
			}
		}
		return amount;
	}

	private void resetAfterRound(List<Player> players) {
		handsCounter++;
		if(handsCounter%10==0){
			smallBlindAmount+=startingSmallBlindAmount;
			bigBlindAmount=smallBlindAmount*2;
		}
		for(int i=0; i<players.size();i++){
			players.get(i).setFromWhichPot(-1);
			players.get(i).setBetAmount(0);
			players.get(i).setFolded(false);
			players.get(i).setAllIn(false);
			players.get(i).setHasBigBlind(false);
			players.get(i).setHasDealerButton(false);
			players.get(i).setHasSmallBlind(false);
			players.get(i).clearHand();
			if(players.get(i).getChipsAmount()==0 || !players.get(i).isInGame()){
				players.get(i).setInGame(false);
				players.get(i).setFolded(true);
			}
		}
		if(!notEnoughPlayers(players)){
			maxBetOnTable = Integer.valueOf(bigBlindAmount);
			newHand = true;	
		}
		else{
			gameStarted = false;
			response = new SampleResponse("GO");
			server.sendToAllTCP(response);
		}
	}

	private boolean notEnoughPlayers(List<Player> players) {
		int playersInGameCount = 0;
		for(int i=0; i<players.size();i++){
			if(players.get(i).isInGame()) playersInGameCount++;
		}
		return playersInGameCount<=1;
	}

	private List<Player> setFirstAndLastToBet(List<Player> players) {
		if(bidingCount==1){
			while(true){
				betPlayer = (turnPlayer+2)%numPlayers;
				if(!players.get(betPlayer).isInGame()) turnPlayer++;
				else break;
			}
			setPreviousAsLastToBet(players);
		}
		else{
			if(!everybodyAllIn(players)){
				betPlayer = Integer.valueOf(turnPlayer%numPlayers);
				int counter = Integer.valueOf(betPlayer);
				int helper = 0;
				while(helper<numPlayers){
					if(players.get(counter%numPlayers).isFolded() || players.get(counter%numPlayers).isAllIn() || !players.get(counter%numPlayers).isInGame()){
						betPlayer=(betPlayer+1)%numPlayers;
						counter++;
						helper++;
					}	
					else break;
				}
				setPreviousAsLastToBet(players);
			}
			else bidingOver = true;
		}
		return players;
	}

	private void setPreviousAsLastToBet(List<Player> players) {
		int counter;
		int helper;
		lastToBet = (numPlayers+betPlayer-1)%numPlayers;
		counter = Integer.valueOf(lastToBet);
		helper = 0;
		while(helper<numPlayers){
			if(players.get(counter).isFolded() || players.get(counter).isAllIn() || !players.get(counter).isInGame()){
				if(counter==0) counter=Integer.valueOf(numPlayers);
				lastToBet=(counter-1)%numPlayers;
			}	
			else break;
			counter--;
			helper++;
		}
	}

	private boolean everybodyAllIn(List<Player> players) {
		int countAllIn = 0;
		for(int i=0; i<players.size();i++){
			if(players.get(i).isAllIn() || players.get(i).isFolded() || !players.get(i).isInGame()) countAllIn++;
		}
		return countAllIn==players.size();
	}

	private void sendBetResponse(int numberToBet, List<Player> players) {
		if(!players.get(numberToBet).isFolded() && !players.get(numberToBet).isAllIn() && players.get(numberToBet).isInGame()){
			checkPossibleOptions(betPlayer, players);
			response = new SampleResponse("B", numberToBet, maxBetOnTable, possibleOptions);
			server.sendToAllTCP(response);
			waitingForPlayerResponse = true;
			startTimer = System.nanoTime();
		} else {
			if(!players.get(numberToBet).isInGame()){
				request = new SampleRequest("FOLD", betPlayer);
				handleReceived(request);
			}
		}
	}

    
    private void setNextAsBetPlayer(List<Player> players) {
		betPlayer=(betPlayer+1)%numPlayers;
		for(int i=betPlayer%numPlayers; i<players.size();i++){
			if(players.get(i).isFolded() || !players.get(i).isInGame() || players.get(i).isAllIn()){
				betPlayer=(betPlayer+1)%numPlayers;
			}
			else break;
		}
		waitingForPlayerResponse = false;
	}
    
    private void handleConnected(Connection con) {
    	players.add(new Player(numPlayers));
    	players.get(numPlayers).setChipsAmount(playersChips);
    	players.get(numPlayers).setConnectionId(con.getID());
    	players.get(numPlayers).setInGame(true);
    	response = new SampleResponse("N", numPlayers);
    	server.sendToTCP(con.getID(), response);
    	numPlayers++;
    	if(numPlayers==playersCount){
    		for(int i=0; i<botsCount;i++)
    		{
    			players.add(new Bot(numPlayers,"Bot"+String.valueOf(i),botStrategy));
    			players.get(numPlayers).setChipsAmount(playersChips);
    			players.get(numPlayers).setInGame(true);
    			numPlayers++;
    		}
    		playersWithHiddenCards = new ArrayList<Player>(players.size());
    		for(int i=0; i<players.size(); i++){
				playersWithHiddenCards.add(new Player());
			}

    		newHand=true;
    		gameStarted=true;
		}
    	else{
    		response = new SampleResponse("W", "Waiting for all players");
    		server.sendToTCP(con.getID(), response);
		}
    }
	
	private void handleDisconnected(Connection con) {
		for(Player player : players){
			if(player.getConnectionId()==con.getID()){ 
				player.setInGame(false);
				request = new SampleRequest("FOLD", player.getNumber());
				handleReceived(request);
				break;
    		}
	    }
		if(notEnoughPlayers(players)){
			gameStarted = false;
			response = new SampleResponse("GO");
			server.sendToAllTCP(response);
		}
	}
	
	//changed to public for bot to send request
	public void handleReceived(Object object) {
		if (object instanceof SampleRequest) {
    	  SampleRequest request = (SampleRequest) object;
		  if(bidingTime && !bidingOver){
			  if(request.getNumber()==betPlayer){
				  if(request.getTAG().equals("BET")){
					  if(optionPossible("BET")){
						  if(request.getBetAmount()>=maxBetOnTable){
							  players.get(request.getNumber()).setBetAmountThisRound(players.get(request.getNumber()).getBetAmountThisRound()+request.getBetAmount());
		    				  players.get(request.getNumber()).setBetAmount(players.get(request.getNumber()).getBetAmount()+request.getBetAmount());
		    				  players.get(request.getNumber()).setChipsAmount(players.get(request.getNumber()).getChipsAmount()-request.getBetAmount());
		    				  pokerTable.setPot(pokerTable.getPot()+request.getBetAmount());
		    				  if(request.getBetAmount()>maxBetOnTable){
		    					  maxBetOnTable = Integer.valueOf(request.getBetAmount());
		    				  }
		    				  if(players.get(request.getNumber()).getChipsAmount()==0) players.get(request.getNumber()).setAllIn(true);
		    				  setPreviousAsLastToBet(players);
						  }
					  }
				  }
				  else if(request.getTAG().equals("CALL")){
					  if(optionPossible("CALL")){
						  int requestBetAmount = maxBetOnTable - players.get(request.getNumber()).getBetAmountThisRound();
	    				  pokerTable.setPot(pokerTable.getPot()+requestBetAmount);
						  players.get(request.getNumber()).setBetAmount(players.get(request.getNumber()).getBetAmount()+requestBetAmount);
						  players.get(request.getNumber()).setBetAmountThisRound(maxBetOnTable);
	    				  players.get(request.getNumber()).setChipsAmount(players.get(request.getNumber()).getChipsAmount()-requestBetAmount);
						  if(players.get(request.getNumber()).getChipsAmount()==0) players.get(request.getNumber()).setAllIn(true);
					  }
				  }
				  else if(request.getTAG().equals("FOLD")){
					  players.get(request.getNumber()).setFolded(true);
				  }
				  else if(request.getTAG().equals("CHECK")){

				  }
				  else if(request.getTAG().equals("ALLIN")){
					  if(optionPossible("ALLIN")){
	    				  if(players.get(request.getNumber()).getChipsAmount()>maxBetOnTable){
	    					  maxBetOnTable = players.get(request.getNumber()).getChipsAmount()+players.get(request.getNumber()).getBetAmount();
	    					  setPreviousAsLastToBet(players);
	    				  }
						  players.get(request.getNumber()).setBetAmount(players.get(request.getNumber()).getBetAmount()
								  +players.get(request.getNumber()).getChipsAmount());
						  players.get(request.getNumber()).setBetAmountThisRound(players.get(request.getNumber()).getBetAmountThisRound()
								  +players.get(request.getNumber()).getChipsAmount());
	    				  pokerTable.setPot(pokerTable.getPot()+players.get(request.getNumber()).getChipsAmount());
	    				  players.get(request.getNumber()).setChipsAmount(0);
	    				  players.get(request.getNumber()).setAllIn(true);
					  }
				  }
				  else if(request.getTAG().equals("RAISE")){
					  if(optionPossible("RAISE")){
						  pokerTable.setRaiseCount(pokerTable.getRaiseCount()+1);
						  int requestBetAmount = maxBetOnTable - players.get(request.getNumber()).getBetAmountThisRound() + request.getBetAmount();
						  if(players.get(request.getNumber()).getBetAmountThisRound()+requestBetAmount>maxBetOnTable){
							  maxBetOnTable = Integer.valueOf(players.get(request.getNumber()).getBetAmountThisRound()+requestBetAmount);
							  setPreviousAsLastToBet(players);
							  players.get(request.getNumber()).setBetAmount(players.get(request.getNumber()).getBetAmount()+requestBetAmount);
							  players.get(request.getNumber()).setBetAmountThisRound(players.get(request.getNumber()).getBetAmountThisRound()+requestBetAmount);
		    				  players.get(request.getNumber()).setChipsAmount(players.get(request.getNumber()).getChipsAmount()-requestBetAmount);
		    				  pokerTable.setPot(pokerTable.getPot()+requestBetAmount);
		    				  if(players.get(request.getNumber()).getChipsAmount()==0) players.get(request.getNumber()).setAllIn(true);
						  }
					  }
				  }
				  if(optionPossible(request.getTAG())){
					  if(request.getNumber()==lastToBet){
						  bidingOver = true;
						  waitingForPlayerResponse = false;
						  if(everyoneFoldedExceptBetPlayer(players)){
							timeToCheckWinner(players, pokerTable);
							resetAfterRound(players);
						  }
					  } else{
	    				  setNextAsBetPlayer(players);
					  }
				  }
			  }
		  }
      }
	}
	
    private boolean optionPossible(String string) {
		for(int i=0; i<possibleOptions.size(); i++){
			if(possibleOptions.get(i).equals(string)) return true;
		}
		return false;
	}
    
    private void checkPossibleOptions(int playerToBet, List<Player> players){
    	possibleOptions = new ArrayList<String>();
    	if(players.get(playerToBet).getBetAmountThisRound()==maxBetOnTable){
    		if(maxBetOnTable==0) possibleOptions.add("BET");
    		else possibleOptions.add("RAISE");
    		possibleOptions.add("CHECK");
    	} else if(players.get(playerToBet).getBetAmountThisRound()<maxBetOnTable){
    		if(maxBetOnTable>=players.get(playerToBet).getChipsAmount())
			{
    			possibleOptions.add("ALLIN");
			} else { 
				possibleOptions.add("CALL");
				if(!pokerTable.getLimitType().equals("fixed-limit") || pokerTable.getRaiseCount()<pokerTable.getFixedRaiseCount()){
					possibleOptions.add("RAISE");
				}
			}
    	}
    	possibleOptions.add("FOLD");
    	possibleOptions.add("ALLIN");
    }

	private void initiateNewHand(List<Player> players) {
		deck = new Deck();
		pokerTable = new PokerTable();
		pokerTable.setLimitType(limitType);
		if(limitType.equals("fixed-limit")){
			pokerTable.setFixedLimit(fixedChips);
			pokerTable.setFixedRaiseCount(fixedRaise);
			pokerTable.setRaiseCount(0);
		}
		pokerTable.setBigBlindAmount(bigBlindAmount);
		pokerTable.setSmallBlindAmount(smallBlindAmount);
		deck.dealCards(2, players);
		for(Player player : players){
			//HCD - hand cards dealt
			response = new SampleResponse("HCD", player.getHand(), false);
			server.sendToTCP(player.getConnectionId(), response);
		}
		for(int i=turnPlayer%numPlayers; i<players.size()+turnPlayer; i++){
			if(!players.get(i%numPlayers).isInGame()){
				turnPlayer++;
			} else break;
		}
		newHand=false;
		players.get(turnPlayer%numPlayers).setHasDealerButton(true);	
		smallBlindPlayer = (turnPlayer+1)%numPlayers;
		for(int i=smallBlindPlayer; i<players.size()+smallBlindPlayer; i++){
			if(!players.get(i%numPlayers).isInGame()){
				smallBlindPlayer++;
			}  else break;
		}
		smallBlindPlayer=smallBlindPlayer%numPlayers;
		players.get(smallBlindPlayer).setHasSmallBlind(true);
		if(players.get(smallBlindPlayer).getChipsAmount()>smallBlindAmount){
			players.get(smallBlindPlayer).setChipsAmount(players.get(smallBlindPlayer).getChipsAmount()-smallBlindAmount);
			players.get(smallBlindPlayer).setBetAmount(smallBlindAmount);
			players.get(smallBlindPlayer).setBetAmountThisRound(smallBlindAmount);
		}
		else if(players.get(smallBlindPlayer).getChipsAmount()==smallBlindAmount){
			players.get(smallBlindPlayer).setChipsAmount(0);
			players.get(smallBlindPlayer).setBetAmount(smallBlindAmount);
			players.get(smallBlindPlayer).setBetAmountThisRound(smallBlindAmount);
			players.get(smallBlindPlayer).setAllIn(true);
		}
		else{
			players.get(smallBlindPlayer).setBetAmount(players.get(smallBlindPlayer).getChipsAmount());
			players.get(smallBlindPlayer).setBetAmountThisRound(players.get(smallBlindPlayer).getChipsAmount());
			players.get(smallBlindPlayer).setChipsAmount(0);
			players.get(smallBlindPlayer).setAllIn(true);
		}
		pokerTable.setPot(pokerTable.getPot()+players.get(smallBlindPlayer).getBetAmount());
		bigBlindPlayer = (turnPlayer+2)%numPlayers;
		for(int i=bigBlindPlayer; i<players.size()+bigBlindPlayer; i++){
			if(!players.get(i%numPlayers).isInGame() || players.get(i%numPlayers).isHasSmallBlind()){
				bigBlindPlayer++;
			}  else break;
		}
		bigBlindPlayer = bigBlindPlayer%numPlayers;
		players.get(bigBlindPlayer).setHasBigBlind(true);
		if(players.get(bigBlindPlayer).getChipsAmount()>bigBlindAmount){
			players.get(bigBlindPlayer).setBetAmount(bigBlindAmount);
			players.get(bigBlindPlayer).setBetAmountThisRound(bigBlindAmount);
			players.get(bigBlindPlayer).setChipsAmount(players.get(bigBlindPlayer).getChipsAmount()-bigBlindAmount);
		}
		else if(players.get(bigBlindPlayer).getChipsAmount()==bigBlindAmount)
		{
			players.get(bigBlindPlayer).setBetAmount(bigBlindAmount);
			players.get(bigBlindPlayer).setBetAmountThisRound(bigBlindAmount);
			players.get(bigBlindPlayer).setChipsAmount(0);
			players.get(bigBlindPlayer).setAllIn(true);
		}
		else{
			players.get(bigBlindPlayer).setBetAmount(players.get(bigBlindPlayer).getChipsAmount());
			players.get(bigBlindPlayer).setBetAmountThisRound(players.get(bigBlindPlayer).getChipsAmount());
			players.get(bigBlindPlayer).setChipsAmount(0);
			players.get(bigBlindPlayer).setAllIn(true);
		}
		for(int i=0; i<players.size(); i++){
			if(players.get(i).getBetAmountThisRound()>maxBetOnTable){
				maxBetOnTable = players.get(i).getBetAmountThisRound();
			}
		}
		pokerTable.setPot(pokerTable.getPot()+players.get(bigBlindPlayer).getBetAmount());
		response = new SampleResponse("T", pokerTable);
		server.sendToAllTCP(response);
		for(int i=0; i<players.size(); i++){
			playersWithHiddenCards.get(i).setAllProperties(players.get(i));
		}
		response = new SampleResponse("R", playersWithHiddenCards);
		server.sendToAllTCP(response);
		turnPlayer++;
		bidingTime = true;
		bidingOver = false;
		bidingCount = 1;
		setFirstAndLastToBet(players);
	}
	
	public String getLimitType()
	{
		return limitType;
	}
	
	public int getFixedChips()
	{
		return fixedChips;
	}
	
	public int getMaxBetOnTable()
	{
		return maxBetOnTable;
	}
	
	public PokerTable getTable()
	{
		return pokerTable;
	}
	
	public int getBetPlayer()
	{
		return betPlayer;
	}
	
	public int getBigBlind()
	{
		return bigBlindAmount;
	}
	
	public int getPlayersCount()
	{
		return playersCount;
	}
	
	public List<String> getPossibleOpitions()
	{
		return possibleOptions;
	}
	public static void main(String[] args) {
		try {
			new KryoServer(2, 0, "no-limit", null, 20, 1500,40,5);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Server getServer() {
		return server;
	}
}