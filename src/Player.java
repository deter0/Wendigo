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
        // Check if the player can dash again
        Tile currentAnimatedTile = this.animations.get(this.state);
        Vector2 size = new Vector2(100, 100);

        Vector2 lookAtDelta = this.lookAtPoint.sub(this.position);

        if (currentAnimatedTile != null) {
            size = Game.currentMap.LocalToWorldVectorScalar(new Vector2(currentAnimatedTile.w, currentAnimatedTile.h));

            currentAnimatedTile.Draw(g, this.position.x, this.position.y, size.x, size.y, lookAtDelta.x < 0 ? true : false);

            // System.out.println(lookAtDelta.x);
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
            Game.gfxManager.PlayGFXOnce("smoke_cloud", this.position.add(this.size.scale(0.5)), 1.5);

            if (this.velocity.magnitude() > 0) {
                this.velocity = this.velocity.add(this.velocity.normalize().scale(SPEED * 24.0));
            }
        }

        if (Game.IsKeyPressed(KeyEvent.VK_R)) {
            Game.gfxManager.PlayGFXOnce("real_rah", this.position.add(this.size.scale(0.5)).sub(new Vector2(0, this.size.y)), 1.5);
        }

        if (Game.IsKeyPressed(KeyEvent.VK_F)) {
            Game.gfxManager.PlayGFXOnce("gfx_star_spin", this.position.add(this.size.scale(0.5)), 1.5);
        }

        HumanoidUpdate(deltaTime);
    }
}
