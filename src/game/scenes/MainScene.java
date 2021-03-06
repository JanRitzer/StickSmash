package game.scenes;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import entities.BesiusInstance;
import entities.CarlosInstance;
import entities.Entity;
import entities.Player;
import entities.SmashInstance;
import entities.StickFigureInstance;
import entities.WaddlesInstance;
import entities.backgrounds.MainBackground;
import game.Game;
import game.Ledge;
import graphics.Camera;
import graphics.SpriteLoader;
import input.Input;
import input.ai.ComputerInput;
import math.Rect;
import math.Seg;
import math.Vec;

public class MainScene extends Scene {

	private Input[] inputs;
	private int[] selectedCharacters;
	private static final int maxGamOverCounter=60;

	private boolean[] showHighlights;
	private int[] teams;
	private Vec[] spawnPoints;
	private int gameOverCounter=0;
	private int oldUpdatesPerSecond;
	private boolean gameOver=false;
	private Scene oldChooseCharacterScene;
	private int minPlayersAliveToEnd;
	private boolean[] isCPU;
	
	public MainScene(Input[] inputs, boolean[] showHighlights, Scene oldChooseCharacterScene, int[] selectedCharacters, boolean[] isCPU, int[] teams) {
		this.inputs=inputs.clone();
		this.showHighlights=showHighlights;
		this.oldChooseCharacterScene=oldChooseCharacterScene;
		this.selectedCharacters=selectedCharacters;
		this.isCPU=isCPU;
		this.teams=teams;
		
		Game.force120=true;
	}
	
	public void init() {
		new MainBackground();
		spawnPoints=new Vec[4];
		spawnPoints[0]=new Vec(-400, 300);
		spawnPoints[1]=new Vec(400, 300);
		spawnPoints[2]=new Vec(0, 500);
		spawnPoints[3]=new Vec(0, 100);
		int numPlayers=0;
		for (int i:selectedCharacters)
			if (i!=-1)
				numPlayers++;
		int playerNum=0;
		for (int i=0; i<inputs.length; i++) {
			if (selectedCharacters[i]==-1) continue;
			
			ComputerInput cpuInput=null;
			if (isCPU[i]) {
				cpuInput=new ComputerInput();
				inputs[i]=new Input(cpuInput);
			}
			Player created=null;
			double percentAcross=numPlayers==1?0.5:(playerNum/(double)(numPlayers-1));
			if (selectedCharacters[i]==0||selectedCharacters[i]==5)
				created=new Player(inputs[i], spawnPoints[i], teams[i], percentAcross, showHighlights[i], new StickFigureInstance(teams[i]));
			if (selectedCharacters[i]==1)
				created=new Player(inputs[i], spawnPoints[i], teams[i], percentAcross, showHighlights[i], new BesiusInstance(teams[i]));
			if (selectedCharacters[i]==2)
				created=new Player(inputs[i], spawnPoints[i], teams[i], percentAcross, showHighlights[i], new SmashInstance(teams[i]));
			if (selectedCharacters[i]==3)
				created=new Player(inputs[i], spawnPoints[i], teams[i], percentAcross, showHighlights[i], new CarlosInstance(teams[i]));
			if (selectedCharacters[i]==4)
				created=new Player(inputs[i], spawnPoints[i], teams[i], percentAcross, showHighlights[i], new WaddlesInstance(teams[i]));
			
			if (cpuInput!=null)
				cpuInput.setPlayer(created);
			
			playerNum++;
		}
		Camera.getInstance().setWorldWidth(3000);
		Camera.getInstance().setPosition(Vec.zero);
		minPlayersAliveToEnd=playerNum==1?0:1;
	}
	
	public Scene update() {
		updateEntities();
		HashSet<Integer> aliveTeams=new HashSet<>();
		for (Entity e:getEntities()) {
			if (e.isAlive())
				aliveTeams.add(e.getTeam());
		}
		if (!gameOver) {
			if (aliveTeams.size()<=minPlayersAliveToEnd) {
				gameOver=true;
				oldUpdatesPerSecond=Game.updatesPerSecond;
				Game.updatesPerSecond=20;
			}
		}
		else {
			gameOverCounter++;
			if (gameOverCounter>=maxGamOverCounter) {
				Game.updatesPerSecond=oldUpdatesPerSecond;
				return oldChooseCharacterScene;
			}
		}
		return this;
	}
	
	private void updateEntities() {
		ArrayList<Entity> toUpdate=getEntities();
		for (Entity e:toUpdate)
			e.update();
	}
	
	public BufferedImage render() {
		Camera cam=Camera.getInstance();
		cam.preRender();
		renderEntites();
		cam.pushState();
		cam.setPosition(Vec.zero);
		cam.setWorldWidth(1000);
		if (gameOver) {
			SpriteLoader.gameOverText.drawAlphaAndSize(Vec.zero, 1, 0.5, 0.5);
		}
		cam.popState();
		BufferedImage result=cam.postRender();
		return result;
	}
	
	private void renderEntites() {
		ArrayList<Entity> toRender=getEntities();
		Collections.sort(toRender, (a, b)->{return Integer.compare(a.getRenderOrder(), b.getRenderOrder());});
		for (Entity e:toRender)
			e.render();
		for (Rect rect:getCollisionBoxes())
			rect.render();
		for (Seg s:getPlatforms())
			s.render();
		for (Ledge v:getHangPositions())
			v.render();
		
		//then render the UI
		for (Entity e:toRender)
			e.renderUI();
	}
	
	public Rect getBoundingBox() {
		return new Rect(new Vec(-1500, -1000), new Vec(1500, 1400));
	}
	
	public Vec[] getSpawnPoints() {
		return spawnPoints;
	}
	
}
