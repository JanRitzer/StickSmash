package entities.backgrounds;

import entities.Entity;
import game.Game;
import game.Ledge;
import graphics.SpriteLoader;
import math.Rect;
import math.Seg;
import math.Vec;

public class MainBackground extends Entity {

	private static Rect colisionBox=new Rect(new Vec(-860, -300), new Vec(860, -10));
	private static Seg topPlatform=new Seg(new Vec(-180, 455), new Vec(140, 455));
	private static Seg leftPlatform=new Seg(new Vec(-600, 200), new Vec(-600+260, 200));
	private static Seg rightPlatform=new Seg(new Vec(365, 200), new Vec(365+260, 200));
	private static Vec leftHangPos=new Vec(-860, -10);
	private static Vec rightHangPos=new Vec(860, -10);
	
	public MainBackground() {
		Game.addCollitionBox(colisionBox);
		Game.addPlatform(topPlatform);
		Game.addPlatform(leftPlatform);
		Game.addPlatform(rightPlatform);
		Game.addHangPos(new Ledge(leftHangPos));
		Game.addHangPos(new Ledge(rightHangPos));
	}
	
	public int getRenderOrder() {
		return -100;
	}
	
	public void render() {
		SpriteLoader.backgroundSprite.draw(Vec.zero, true);
	}
	
}
