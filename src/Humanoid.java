import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

enum AnimationState {
    IDLE,
    WALK,
    BIRTHING,
    EXPLODING,
    DYING
};

enum State {
    PLAYER,

    CHASING,
    ROAMING,
    
    RAT_BIRTHING,

    BOMBER_EXPLODING,

    HAR_RUNNING_AWAY,

    DYING,
    DEAD
}

enum HumanoidType {
    HUMAN,
    
    OGRE,
    
    RAT,
    RAT_CHILD,

    BOMBER,

    HAR
};

class Bullet extends GameObject {
    public boolean destroyed = false;
    private Tile animatedSprite;
    public double bulletSpeed = 400.0;
    private Vector2 initialPosition;
    private String shooter;

    public Bullet(Vector2 position, Vector2 velocity, String shooter) {
        Vector2 spriteSize = Game.currentMap.LocalToWorldVectorScalar(new Vector2(1,1));

        this.position = position.sub(spriteSize.scale(0.5));
        this.velocity = velocity;
        this.initialPosition = position.scale(1.0);
        this.shooter = shooter;

        this.animatedSprite = Game.currentMap.GetSheetTileByTag("bullet");

        Game.bm.bullets.add(this);
    }

    public void Update(double deltaTime) {
        Vector2 newPosition = this.position.add(this.velocity.normalize().scale(bulletSpeed).scale(deltaTime));
        Vector2 positionDifference = this.position.sub(newPosition);
        Vector2 direction = positionDifference.normalize().scale(positionDifference.magnitude() + 10.0);
        Vector2 bulletCenter = newPosition.add(this.size.scale(0.5));

        Physics.RaycastResult r = Game.physics.RayCast(newPosition, direction, new String[] {this.shooter});

        if (r != null) {
            Game.gfxManager.PlayGFXOnce("smoke_cloud", newPosition.sub(this.size.scale(0.5)));
            destroyed = true;

            for (Humanoid h : Game.humanoids) {
                if ((this.shooter == "player" && h.type != HumanoidType.HUMAN) || (this.shooter == "enemy" && h.type == HumanoidType.HUMAN)) {
                    Vector2 humanoidCenter = h.position.add(h.size.scale(0.5));
                    if (humanoidCenter.distance(bulletCenter) < 100) {
                        Vector2 humanDirection = humanoidCenter.sub(bulletCenter).normalize();

                        h.health -= 50;
                        h.velocity = h.velocity.add(humanDirection.scale(1500));
                    }
                }
            }
        }

        if (this.position.distance(this.initialPosition) > 2000) {
            destroyed = true;
        }

        this.position = newPosition;
    }

    public void Draw(Graphics2D g) {
        Vector2 spriteSize = Game.currentMap.LocalToWorldVectorScalar(new Vector2(1,1));
        this.animatedSprite.Draw(g, this.position.x, this.position.y, spriteSize.x, spriteSize.y);
    }
}

class BulletManager {
    ArrayList<Bullet> bullets = new ArrayList<>();
    
    public void Update(double deltaTime) {
        ArrayList<Integer> deleteIndicies = new ArrayList<>();
        for (int i = 0; i < this.bullets.size(); i++) {
            Bullet b = this.bullets.get(i);

            b.Update(deltaTime);

            if (b.destroyed) {
                deleteIndicies.add(i);
            }
        }

        for (int i : deleteIndicies) {
            this.bullets.remove(i);
        }
    }

    public void Draw(Graphics2D g) {
        for (Bullet b : this.bullets) {
            Game.currentMap.RenderResponsibly(b);
        }
    }
}

public class Humanoid extends GameObject {
    protected String name;
    protected HumanoidType type;
    
    public int health;
    protected int maxHealth;
    
    protected AnimationState animState = AnimationState.IDLE;
    protected HashMap<AnimationState, Tile> animations = new HashMap<>();
    
    public Vector2 lookAtPoint = new Vector2();

    public double movementSpeed = 100;
    public State state;

    public double dashCooldown = 0.6;
    protected double lastTimeDashed = 0;

    public double meleeRange = 70.0;

    private double dyingAnimationCurrentScale = 1.0;
    
    private double lastBulletShot = 0;
    public double maxBulletsPerSecond = 2;

    public double damageMultiplier = 1.0;

    protected double lastMelee = 0;
    public double maxMeleesPerSecond = 6;

    public int bulletFullMagazine = 5;
    public int bulletMagazine = bulletFullMagazine;
    public Double reloadTill = null;
    public double reloadTime = 3;
    
    protected double spawnTime = 0;
    protected int randomSeed;

    private boolean sizeFix;

    public Humanoid(String name, int health, int maxHealth) {
        this.health = health;
        this.name = name;
        this.sizeFix = this.name == "dino";
        this.maxHealth = maxHealth;
        this.collisionLayers.add("humanoid");
        this.type = HumanoidType.HUMAN;
        this.state = State.PLAYER;
        this.randomSeed = (int)(Math.random() * 1_000_000);
        this.spawnTime = Game.now();
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
                System.out.println("Missing animation: `" + tag + "` for: `" + this.name + "`");
            } else {
                System.out.println("[LOG] Loaded animation: " + tag);
                this.animations.put(state, t);
            }
        }

    }

    protected void HumanoidDraw(Graphics2D g) {
        Tile currentAnimatedTile;

        switch (this.animState) {
            case WALK: {
                currentAnimatedTile = this.animations.get(AnimationState.WALK);
            } break;
            case BIRTHING: {
                currentAnimatedTile = this.animations.get(AnimationState.BIRTHING);
            } break;
            case EXPLODING: {
                currentAnimatedTile = this.animations.get(AnimationState.EXPLODING);
            } break;
            case DYING: {
                if (dyingAnimationCurrentScale <= 0) {
                    this.state = State.DEAD;
                } else {
                    dyingAnimationCurrentScale -= 6.0 * Game.deltaTime;
                }
            }
            default: {
                currentAnimatedTile = this.animations.get(AnimationState.IDLE);
            } break;
        }

        Vector2 size = new Vector2(0, 0);

        Vector2 lookAtDelta = this.lookAtPoint.sub(this.position);

        if (currentAnimatedTile != null) {
            size = Game.currentMap.LocalToWorldVectorScalar(new Vector2(currentAnimatedTile.w, currentAnimatedTile.h)).scale(dyingAnimationCurrentScale);

            double transparency = 1.0;
            double deltaDash = Game.now() - this.lastTimeDashed;

            if (deltaDash < this.dashCooldown) {
                transparency = (Math.sin(Game.now()*24)/2.0 + 0.5)*0.3 + 0.5;
            }

            currentAnimatedTile.Draw(
                g,
                this.position.x,
                this.position.y - (sizeFix ? size.y * 0.25 : 0),
                size.x,
                size.y,
                lookAtDelta.x < 0 ? true : false,
                transparency
            );
        }

        this.size = size;

        if (sizeFix) {
            size = size.mult(new Vector2(1.0, 0.75));
        }
    }

    public void Draw(Graphics2D g) {
        HumanoidDraw(g);
    }

    public void HumanoidUpdate(double deltaTime) {
        if (this.reloadTill != null && Game.now() > this.reloadTill) {
            this.bulletMagazine = bulletFullMagazine;
            this.reloadTill = null;
        }

        if (this.health <= 0) {
            this.state = State.DYING;
            this.animState = AnimationState.DYING;
        } else {
            this.animState = this.velocity.magnitude() > 1 ? AnimationState.WALK : AnimationState.IDLE;
        }
    }
    
    public void ShootBullet(Vector2 direction) {
        if (bulletMagazine > 0) {
            if ((Game.now() - this.lastBulletShot) > 1.0/this.maxBulletsPerSecond) {
                this.bulletMagazine -= 1;
                Game.bm.bullets.add(new Bullet(this.position.add(this.size.scale(0.5)), direction, this.type == HumanoidType.HUMAN ? "player" : "enemy"));
                this.lastBulletShot = Game.now();
            }
        } else if (this.reloadTill == null) {
            this.reloadTill = Game.now() + this.reloadTime;
        }
    }

    public void MeleeAttack() {
        if (!((Game.now() - this.lastMelee) > 1.0/this.maxMeleesPerSecond)) {
            return;
        }
        this.lastMelee = Game.now();

        Vector2 lookAtDelta = this.lookAtPoint.sub(this.position);
        boolean flipped = lookAtDelta.x < 0 ? true : false;

        Game.gfxManager.PlayGFXOnce("gfx_slash", this.size.scale(0.5), 2.0, flipped, this);

        for (Humanoid h : Game.humanoids) {
            if (h == this) continue;

            boolean checkDamage = false;

            if (this.type == HumanoidType.HUMAN && h.type != HumanoidType.HUMAN) {
                checkDamage = true;
            } 
            if (this.type != HumanoidType.HUMAN && h.type == HumanoidType.HUMAN) {
                checkDamage = true;
            }

            if (checkDamage) {
                Vector2 otherCentre = h.position.add(h.size.scale(0.5));

                Vector2 thisHitPos = this.position.add(new Vector2(0, this.size.y / 2.0));
                double size = this.size.x / 2.0;

                thisHitPos.x += flipped ? (-size) : (size);

                if (otherCentre.distance(thisHitPos) < this.meleeRange) {
                    Vector2 direction = h.position.sub(this.position).normalize();

                    // Knockback
                    h.velocity = h.velocity.add(direction.scale(1000));
                    
                    h.health -= 40 * this.damageMultiplier;
                }
            }
        }
    }
    
    public void Update(double deltaTime) {
        this.HumanoidUpdate(deltaTime);

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

        this.velocity = this.velocity.add(movementVector.scale(this.movementSpeed));

        this.lookAtPoint = Game.worldMousePos;

        if (Game.IsKeyPressed(KeyEvent.VK_SPACE)) {
            double deltaDashed = Game.now() - lastTimeDashed;

            if (deltaDashed > dashCooldown && this.velocity.magnitude() > 0) {
                Game.gfxManager.PlayGFXOnce("smoke_cloud", this.position.add(this.size.scale(0.5)), 1.0);

                this.velocity = this.velocity.add(this.velocity.normalize().scale(this.movementSpeed * 36.0));
                this.lastTimeDashed = Game.now();
            }
        }

        if (Game.IsKeyPressed(KeyEvent.VK_Q)) {
            MeleeAttack();
        }

        if (Game.IsMouseDown(MouseEvent.BUTTON1)) {
            ShootBullet(Game.worldMousePos.sub(this.position));
        }

        if (Game.IsKeyPressed(KeyEvent.VK_R)) {
            Game.gfxManager.PlayGFXOnce("real_rah", this.position.add(this.size.scale(0.5)).sub(new Vector2(0, this.size.y)), 1.5);
        }
    }

    public void SetMaxHealth(int newMaxHealth) {
        double healthAsPercentage = this.health / (double)this.maxHealth;

        this.maxHealth = newMaxHealth;
        this.health = (int)(healthAsPercentage * maxHealth);
    }

    public boolean CanDash() {
        double deltaDashed = Game.now() - lastTimeDashed;
        if (deltaDashed > dashCooldown) {
            return true;
        }
        return false;
    }
}
