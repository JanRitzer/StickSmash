package entities;

import java.util.ArrayList;

import entities.particles.Particle;
import game.Game;
import game.Ledge;
import graphics.Sprite;
import graphics.SpriteLoader;
import input.InputType;
import math.Rect;
import math.Seg;
import math.Vec;

public class Player extends Entity {

	private Vec position;
	private Vec velocity=Vec.zero;
	private Rect collisionBox;
	private Rect hangBoxLeft, hangBoxRight;
	private InputType inputType;

	//Constants
	private static final Vec gravity=new Vec(0, -0.2), fastGravity=gravity.scale(2);
	private static final double moveGroundSpeed=1, moveAirSpeed=0.2;
	private static final double jumpPower=10, doubleJumpPower=15;
	private static final double xGroundedFriction=0.8, xAirFriction=0.95, yFriction=0.98;
	private static final double minSpeedToRun=0.1;
	private static final int numJumpFrames=30;
	private static final double maxShieldScale=0.8;
	private static final int maxShield=120*4*3;
	private static final int stunFrameAfterBrokenShield=240;
	private static final double rollVelocity=6;
	
	//animation constants
	private static final int runningAnimLen=40;
	private static final int rollingAnimLen=50;
	private static final int spotDodgeAnimLen=50;
	private static final int airDodgeAnimLen=50;
	private static final int hangImmunityLen=50;
	private static final int framesBetweenHangs=60;

	//Player states
	private boolean grounded=false;
	private boolean facingRight=true;
	private boolean hasDoubleJump=false;
	private PlayerState state=PlayerState.AIRBORN;
	private int animationCounter=0;
	private int jumpFramesLeft=0;
	private int shield=maxShield;
	private int stunCounter=0;
	private int framesUntilNextHang=0;
	private Ledge hangingOn=null;
	
	
	public Player(InputType inputType) {
		collisionBox=new Rect(new Vec(-40, -70), new Vec(40, 70));
		double hangCloseX=20;
		double hangLowY=30;
		double hangFarX=105;
		double hangHighY=110;
		hangBoxRight=new Rect(new Vec(hangCloseX, hangLowY), new Vec(hangFarX, hangHighY));
		hangBoxLeft=new Rect(new Vec(-hangFarX, hangLowY), new Vec(-hangCloseX, hangHighY));
		position=new Vec(0, 500);
		this.inputType=inputType;
	}
	
	public void update() {
		applyGravity();
		applyFriction();
		checkForInputAndMovement();
		
		moveToCollision();
		updateGrounded();
		
		collisionBox.setDrawOffeset(position);
		hangBoxLeft.setDrawOffeset(position);
		hangBoxRight.setDrawOffeset(position);
	}
	
	private void updateGrounded() {
		grounded=wouldBeInGround(position.add(Vec.down.scale(0.1)));
		if (!hittingPlatform(position)&&hittingPlatform(position.add(Vec.down.scale(0.1))))
			grounded=true;
	}
	
	private void applyFriction() {
		if (grounded) {
			velocity=new Vec(velocity.x()*xGroundedFriction, velocity.y()*yFriction);
		}
		else {
			velocity=new Vec(velocity.x()*xAirFriction, velocity.y()*yFriction);
		}
	}
	
	private void applyGravity() {
		if (!grounded) {
			if (inputType.downMovementHeld())
				velocity=velocity.add(fastGravity);
			else
				velocity=velocity.add(gravity);
		}
	}
	
	private void checkForInputAndMovement() {
		if (state!=PlayerState.STUNNED&&state!=PlayerState.ROLLING&&state!=PlayerState.SPOT_DODGING&&state!=PlayerState.HANGING&&
				inputType.jumpMovementPressed()&&grounded) {
			velocity=new Vec(velocity.x(), jumpPower);
			jumpFramesLeft=numJumpFrames;
		}
		if (state!=PlayerState.HANGING&&inputType.jumpMovementHeld()) {
			if (!grounded&&jumpFramesLeft>0) {
				jumpFramesLeft--;
				velocity=new Vec(velocity.x(), jumpPower);
			}
		}
		else {
			jumpFramesLeft=0;
		}
		
		//double jump
		if (state!=PlayerState.AIR_DODGING&&!grounded&&state!=PlayerState.HANGING&&inputType.jumpMovementPressed()&&hasDoubleJump) {
			hasDoubleJump=false;
			velocity=new Vec(velocity.x(), doubleJumpPower);
			Particle.createDoubleJumpParticle(position);
		}
		
		//movement
		if (state!=PlayerState.STUNNED&&state!=PlayerState.SHIELDING&&state!=PlayerState.ROLLING&&state!=PlayerState.HANGING) {
			if (inputType.leftMovementHeld()) {
				if (grounded) {
					if (state!=PlayerState.SPOT_DODGING)
						velocity=velocity.add(Vec.left.scale(moveGroundSpeed));
				}
				else
					velocity=velocity.add(Vec.left.scale(moveAirSpeed));
			}
			if (inputType.rightMovementHeld()) {
				if (grounded) {
					if (state!=PlayerState.SPOT_DODGING)
						velocity=velocity.add(Vec.right.scale(moveGroundSpeed));
				}
				else
					velocity=velocity.add(Vec.right.scale(moveAirSpeed));
			}
		}

		animationCounter++;
		if (state!=PlayerState.SHIELDING) {
			shield++;
			shield=Math.min(shield, maxShield);
		}
		framesUntilNextHang=Math.max(0, framesUntilNextHang-1);
		switch(state) {
			case AIRBORN:
				if (grounded) {
					onLand();
				}
				else if (inputType.shieldHeld()) {
					setAnimation(PlayerState.AIR_DODGING);
				}
				else {
					if (framesUntilNextHang<=0)
						tryToHang();
				}
				break;
			case IDLE:
				if (!grounded)
					setAnimation(PlayerState.AIRBORN);
				else if (Math.abs(velocity.x())>=minSpeedToRun)
					setAnimation(PlayerState.RUNNING);
				else if (inputType.shieldHeld())
					setAnimation(PlayerState.SHIELDING);
				break;
			case RUNNING:
				if (animationCounter>=runningAnimLen)
					animationCounter=0;
				
				
				if (Math.abs(velocity.x())<minSpeedToRun)
					setAnimation(PlayerState.IDLE);
				else if (!grounded)
					setAnimation(PlayerState.AIRBORN);
				else {
					boolean oldFacingRight=facingRight;
					facingRight=velocity.x()>0;
					if (oldFacingRight^facingRight)
						createRunTurnParticle();
				}
				break;
			case SHIELDING:
				if (!grounded) {
					setAnimation(PlayerState.AIRBORN);
					break;
				}
				shield=Math.max(0, shield-3);
				if (shield==0) {
					setAnimation(PlayerState.STUNNED);
					stunCounter=stunFrameAfterBrokenShield;
				}
				else if (!inputType.shieldHeld()) {
					setAnimation(PlayerState.IDLE);
				}
				else if (inputType.leftMovementHeld()) {
					facingRight=false;
					setAnimation(PlayerState.ROLLING);
				}
				else if (inputType.rightMovementHeld()) {
					facingRight=true;
					setAnimation(PlayerState.ROLLING);
				}
				else if (inputType.downMovementHeld()) {
					setAnimation(PlayerState.SPOT_DODGING);
				}
				break;
			case STUNNED:
				stunCounter--;
				if (stunCounter<=0) {
					setAnimation(PlayerState.IDLE);
				}
				else if (stunCounter%10==0) {
					Vec offset=new Vec((Math.random()*2-1)*40, (Math.random()*2-1)*50+15);
					Particle.createStunParticle(position.add(offset));
				}
				break;
			case ROLLING:
				velocity=(facingRight?Vec.right:Vec.left).scale(rollVelocity);
				if (animationCounter>rollingAnimLen)
					state=PlayerState.IDLE;
				break;
			case SPOT_DODGING:
				if (animationCounter>=spotDodgeAnimLen) {
					setAnimation(PlayerState.IDLE);
				}
				break;
			case AIR_DODGING:
				if (grounded) {
					onLand();
				}
				else if (animationCounter>=airDodgeAnimLen) {
					setAnimation(PlayerState.AIRBORN);
				}
				break;
			case HANGING:
				velocity=Vec.zero;
				framesUntilNextHang=framesBetweenHangs;
				if (inputType.downMovementHeld()||(inputType.leftMovementHeld()&&facingRight)||(inputType.rightMovementHeld()&&!facingRight)) {
					//drop from the ledge
					hasDoubleJump=true;
					setAnimation(PlayerState.AIRBORN);
					hangingOn.occupied=false;
					hangingOn=null;
					if (facingRight)
						velocity=velocity.add(Vec.left.scale(3.5));
					else
						velocity=velocity.add(Vec.right.scale(3.5));
				}
				else if (inputType.upMovementHeld()) {
					//jump up
					hasDoubleJump=true;
					setAnimation(PlayerState.AIRBORN);
					hangingOn.occupied=false;
					hangingOn=null;
					velocity=Vec.up.scale(jumpPower*2);
					if (facingRight) {
						position=position.add(Vec.left.scale(30));
						velocity=velocity.add(Vec.right.scale(6));
					}
					else {
						position=position.add(Vec.right.scale(30));
						velocity=velocity.add(Vec.left.scale(6));
					}
				}
				else if (inputType.shieldHeld()) {
					//roll
					setAnimation(PlayerState.ROLLING);
					if (facingRight) {
						position=hangingOn.getPos().sub(collisionBox.corners()[1]).add(new Vec(50, .1));
					}
					else {
						position=hangingOn.getPos().sub(collisionBox.corners()[0]).add(new Vec(-50, .1));
					}
					hangingOn.occupied=false;
					hangingOn=null;
				}
				else if (animationCounter>=hangImmunityLen&&(inputType.leftMovementHeld()||inputType.rightMovementHeld())) {
					//just climb up
					setAnimation(PlayerState.IDLE);
					if (facingRight) {
						position=hangingOn.getPos().sub(collisionBox.corners()[1]).add(new Vec(50, .1));
					}
					else {
						position=hangingOn.getPos().sub(collisionBox.corners()[0]).add(new Vec(-50, .1));
					}
					hangingOn.occupied=false;
					hangingOn=null;
				}
				else {
					if (facingRight)
						position=hangingOn.getPos().sub(hangBoxRight.center());
					else
						position=hangingOn.getPos().sub(hangBoxLeft.center());
				}
				break;
		}
	}
	
	private boolean wouldBeInGround(Vec newPos) {
		Rect newCollisionBox=collisionBox.offsetBy(newPos);
		for (Rect r:Game.getCollisionBoxes()) {
			if (newCollisionBox.intersects(r))
				return true;
		}
		return false;
	}
	
	private boolean hittingPlatform(Vec newPos) {
		if (inputType.downMovementHeld()) return false;
		Rect newCollusionBox=collisionBox.offsetBy(newPos);
		for (Seg s:Game.getPlatforms()) {
			if (newCollusionBox.intersectsSeg(s))
				return true;
		}
		return false;
	}
	
	private void moveToCollision() {
		//move y
		Vec toMove=Vec.j.scale(Vec.j.dot(velocity));
		while (toMove.mag()>Vec.EPS) {
			Vec nextStep;
			if (toMove.mag()>0.05)
				nextStep=toMove.unit().scale(0.05);
			else
				nextStep=toMove;
			
			//also, don't go through ground
			boolean tooFar=wouldBeInGround(position.add(nextStep));
			if (!hittingPlatform(position)&&hittingPlatform(position.add(nextStep))&&toMove.y()<0)
				tooFar=true;
			if (tooFar) {
				toMove=Vec.zero;
				velocity=new Vec(velocity.x(), 0);
			}
			else {
				position=position.add(nextStep);
				toMove=toMove.sub(nextStep);
			}
		}
		
		//move x
		toMove=Vec.i.scale(Vec.i.dot(velocity));
		while (toMove.mag()>Vec.EPS) {
			Vec nextStep;
			if (toMove.mag()>0.05)
				nextStep=toMove.unit().scale(0.05);
			else
				nextStep=toMove;
			
			if (wouldBeInGround(position.add(nextStep))) {
				toMove=Vec.zero;
				velocity=new Vec(0, velocity.y());
			}
			else {
				position=position.add(nextStep);
				toMove=toMove.sub(nextStep);
			}
		}
	}
	
	private void createRunTurnParticle() {
		Particle.createRunTurnSprite(position.add(Vec.down.scale(45)).add((facingRight?Vec.left:Vec.right).scale(10)), facingRight);
	}
	
	private void setAnimation(PlayerState newAnimation) {
		animationCounter=0;
		state=newAnimation;
	}
	
	private void onLand() {
		hasDoubleJump=true;
		if (Math.abs(velocity.x())>=minSpeedToRun)
			setAnimation(PlayerState.RUNNING);
		else
			setAnimation(PlayerState.IDLE);
		facingRight=velocity.x()>0;
		createRunTurnParticle();
		facingRight^=true;
		createRunTurnParticle();
		facingRight^=true;
	}
	
	private void tryToHang() {
		ArrayList<Ledge> hangPositions=Game.getHangPositions();
		for (Ledge l:hangPositions) {
			if (l.occupied) continue;
			
			if (hangBoxRight.offsetBy(position).contains(l.getPos())) {
				//hang to right
				setAnimation(PlayerState.HANGING);
				hangingOn=l;
				l.occupied=true;
				facingRight=true;
				position=l.getPos().sub(hangBoxRight.center());
			}
			else if (hangBoxLeft.offsetBy(position).contains(l.getPos())) {
				//hang to left
				setAnimation(PlayerState.HANGING);
				hangingOn=l;
				l.occupied=true;
				facingRight=false;
				position=l.getPos().sub(hangBoxLeft.center());
			}
		}
	}
	
	
	public void render() {
		Sprite toDraw=null;
		boolean drawAtFullAlpha=true;
		switch(state) {
			case AIRBORN:
				if (velocity.y()>=0)
					toDraw=SpriteLoader.stickFigureAirUp;
				else
					toDraw=SpriteLoader.stickFigureAirDown;
				break;
			case IDLE:
				toDraw=SpriteLoader.stickFigureIdle;
				break;
			case RUNNING:
				if (animationCounter>=runningAnimLen/2)
					toDraw=SpriteLoader.stickFigureRunning1;
				else
					toDraw=SpriteLoader.stickFigureRunning2;
				break;
			case SHIELDING:
				toDraw=SpriteLoader.stickFigureIdle;
				break;
			case STUNNED:
				toDraw=SpriteLoader.stickFigureIdle;
				break;
			case ROLLING:
				if (animationCounter<rollingAnimLen/2)
					toDraw=SpriteLoader.stickFigureRolling1;
				else
					toDraw=SpriteLoader.stickFigureRolling2;
				break;
			case SPOT_DODGING:
				drawAtFullAlpha=false;
				toDraw=SpriteLoader.stickFigureIdle;
				break;
			case AIR_DODGING:
				drawAtFullAlpha=false;
				if (velocity.y()>=0)
					toDraw=SpriteLoader.stickFigureAirUp;
				else
					toDraw=SpriteLoader.stickFigureAirDown;
				break;
			case HANGING:
				toDraw=SpriteLoader.stickFigureHang;
				drawAtFullAlpha=animationCounter>=hangImmunityLen;
				break;
			default:
				throw new Error("invalid render state: "+state);	
		}
		collisionBox.render();
		hangBoxLeft.render();
		hangBoxRight.render();
		toDraw.drawAlpha(position, facingRight, drawAtFullAlpha?1:0.2);
		
		if (state==PlayerState.SHIELDING) {
			double shieldScale=maxShieldScale*(shield/(double)maxShield);
			SpriteLoader.shieldSprite.drawAlphaAndSize(position, 0.6, shieldScale, shieldScale);
		}
	}
	
}