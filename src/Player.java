import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

enum AnimationState {
    IDLE,
    WALK
};

public class Player extends GameObject {
    protected String name;

    public int health;
    protected int maxHealth;
    
    protected AnimationState state = AnimationState.IDLE;
    protected HashMap<AnimationState, Tile> animations = new HashMap<>();
    
    public Vector2 lookAtPoint = new Vector2();
    public Vector2 acceleration = new Vector2();

    public double dashCooldown = 2.0;
    protected double lastTimeDashed = 0;

    public Player(String name, int health, int maxHealth) {
        this.health = health;
        this.name = name;
        this.maxHealth = maxHealth;
    }

    public void LoadAnimations() {
        LoadAnimations(this.name);
    }

    public void LoadAnimations(String prefix) {
        if (Game.currentMap == null) return;

        for (AnimationState state : AnimationState.values()) {
            String stateString = state.name().toLowerCase();
            String tag = prefix.toLowerCase() + "_" + stateString;

            Tile t = Game.currentMap.GetSheetTileByTag(tag);
            if (t == null) {
                new Message("Couldn't find animation: `" + tag + "`");
            } else {
                System.out.println("[LOG] Loaded animation: " + tag);
                this.animations.put(state, t);
            }
        }

    }

    private void HumanoidDraw(Graphics2D g) {
        Tile currentAnimatedTile = this.animations.get(this.state);
        Vector2 size = new Vector2(100, 100);

        Vector2 lookAtDelta = this.lookAtPoint.sub(this.position);

        if (currentAnimatedTile != null) {
            size = Game.currentMap.LocalToWorldVectorScalar(new Vector2(currentAnimatedTile.w, currentAnimatedTile.h));

            double transparency = 1.0;
            double deltaDash = Game.now() - this.lastTimeDashed;

            if (deltaDash < this.dashCooldown) {
                transparency = (Math.sin(Game.now()*24)/2.0 + 0.5)*0.3 + 0.5;
            }

            currentAnimatedTile.Draw(
                g,
                this.position.x,
                this.position.y,
                size.x,
                size.y,
                lookAtDelta.x < 0 ? true : false,
                transparency
            );
        }

        this.size = size;
    }

    public void Draw(Graphics2D g) {
        HumanoidDraw(g);
    }

    public void HumanoidUpdate(double deltaTime) {
        this.state = this.velocity.magnitude() > 1 ? AnimationState.WALK : AnimationState.IDLE;

        this.velocity = this.velocity.add(this.acceleration.scale(deltaTime));
        this.acceleration = this.acceleration.scale(0.75);
    }

    public void MeleeAttack() {
        Vector2 lookAtDelta = this.lookAtPoint.sub(this.position);

        Game.gfxManager.PlayGFXOnce("gfx_slash", this.size.scale(0.5), 2.0, lookAtDelta.x < 0 ? true : false, this);
    }

    public void Update(double deltaTime) {
        final double SPEED = 100;

        Vector2 movementVector = new Vector2();
        if (Game.IsKeyDown(KeyEvent.VK_W)) {
            movementVector.y = -1;
        } else if (Game.IsKeyDown(KeyEvent.VK_S)) {
            movementVector.y = 1;
        }
        
        if (Game.IsKeyDown(KeyEvent.VK_A)) {
            movementVector.x = -1;
        } else if (Game.IsKeyDown(KeyEvent.VK_D)) {
            movementVector.x = 1;
        }

        this.velocity = this.velocity.add(movementVector.scale(SPEED));

        this.lookAtPoint = Game.worldMousePos;

        if (Game.IsKeyPressed(KeyEvent.VK_SPACE)) {
            double deltaDashed = Game.now() - lastTimeDashed;

            if (deltaDashed > dashCooldown && this.velocity.magnitude() > 0) {
                Game.gfxManager.PlayGFXOnce("smoke_cloud", this.position.add(this.size.scale(0.5)), 1.5);

                this.velocity = this.velocity.add(this.velocity.normalize().scale(SPEED * 36.0));
                this.lastTimeDashed = Game.now();
            }
        }

        if (Game.IsKeyPressed(KeyEvent.VK_Q)) {
            MeleeAttack();
        }

        if (Game.IsKeyPressed(KeyEvent.VK_R)) {
            Game.gfxManager.PlayGFXOnce("real_rah", this.position.add(this.size.scale(0.5)).sub(new Vector2(0, this.size.y)), 1.5);
        }

        HumanoidUpdate(deltaTime);
    }

    public boolean CanDash() {
        double deltaDashed = Game.now() - lastTimeDashed;
        if (deltaDashed > dashCooldown) {
            return true;
        }
        return false;
    }
}
