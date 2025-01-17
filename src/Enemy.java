import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;

class EnemyManager {
    Tile crackingGFXTile;

    ArrayList<Tile> allSpawnTiles = new ArrayList<>();
    ArrayList<Spawn> openedSpawns = new ArrayList<>();

    class Spawn {
        public final double spawnSpawnTime = 5;
        protected double openedTimestamp;
        protected Tile tile;

        public double nextMobSpawn = 0;

        Spawn(Tile t) {
            this.openedTimestamp = Game.now();
            this.tile = t;
            this.nextMobSpawn = Game.now() + this.spawnSpawnTime + Math.random() * 10.0;
        }

        public void Spawned() {
            this.nextMobSpawn = Game.now() + 2.0 + Math.random() * 10.0;
        }
    };

    private double nextSpawn = 0;

    public EnemyManager() {
        crackingGFXTile = Game.gfxManager.GetGFX("gfx_crack");
        crackingGFXTile.animationControl = true;

        allSpawnTiles = Game.currentMap.GetMapTilesByTag("spawn", Game.currentMap.GetGroundLayer());

        nextSpawn = Game.now() + Math.random() * 5.0;
    }
    
    public void Update(double deltaTime) {
        if (Game.now() >= this.nextSpawn) {
            if (this.allSpawnTiles.size() > 0) {
                int randomIndex = (int)(Math.random() * this.allSpawnTiles.size());
                this.openedSpawns.add(new Spawn(this.allSpawnTiles.remove(randomIndex)));
            }
            nextSpawn = 30.0 + Game.now() + Math.random() * 25.0;
        }
    }

    public void Draw(Graphics2D g) {
        for (Spawn s : this.openedSpawns) {
            Tile t = s.tile;

            Vector2 tilePosition = Game.currentMap.LocalToWorldVectorPositional(new Vector2(t.x, t.y));
            Vector2 tileSize = Game.currentMap.LocalToWorldVectorScalar(new Vector2(t.w, t.h)).sub(new Vector2(1));
            
            GFX crack = new GFX(crackingGFXTile.Clone(), tilePosition, false);
            crack.position = tilePosition;
            crack.size = tileSize;

            
            double deltaSpawn = Game.now() - s.openedTimestamp;
            if ((deltaSpawn) <= s.spawnSpawnTime) {
                crack.tile.animCurrentFrame = (int)((deltaSpawn / s.spawnSpawnTime) * 3);
            } else {
                crack.tile.animCurrentFrame = 3;

                if (Game.now() > s.nextMobSpawn) {
                    Enemy enemyToSpawn;

                    double random = Math.random() * 100;

                    if (random > 90) {
                        enemyToSpawn = new Rat();
                    } else if (random > 80) {
                        enemyToSpawn = new Enemy(HumanoidType.OGRE);
                    } else if (random > 70) {
                        enemyToSpawn = new Bomber();
                    } else {
                        enemyToSpawn = new HAR();
                    }

                    enemyToSpawn.position = tilePosition.add(tileSize.scale(0.5)).sub(new Vector2(-20, -50));

                    Game.humanoids.add(enemyToSpawn);

                    s.Spawned();
                }
            }
            
            Game.currentMap.RenderResponsibly(crack);
        }
    }
}

public class Enemy extends Humanoid {
    private double lookAtVectorAssignedTime = 0;
    private double lookAtGoToTimeout = 6;
    private double roamingStoodStillTill = 0;
    private Vector2 roamingStartPosition = null;
    private double eyeSight = 1920.0;
    public boolean canShoot = false;

    public Enemy(HumanoidType type) {
        super(type.name().toLowerCase(), 100, 100);
        
        this.type = type;
        this.collisionLayers.add("enemy");
        this.state = State.ROAMING;
        this.roamingStartPosition = this.position.scale(1.0);

        /* Default healths. */
        switch (type) {
            case OGRE: {
                this.SetMaxHealth(200);
                this.maxBulletsPerSecond = 0.3;
                this.movementSpeed = 30;
                this.meleeRange = 40;
                this.canShoot = true;
                this.eyeSight = 600;
                this.damageMultiplier = 0.8;
            } break;
            case RAT: {
                this.SetMaxHealth(150);
                this.meleeRange = 50;
                this.movementSpeed = 40;
                this.eyeSight = 600;
                this.damageMultiplier = 0.8;
            } break;
            case HAR: {
                this.SetMaxHealth(80);
                this.meleeRange = 40;
                this.movementSpeed = 60;
                this.eyeSight = 900;
                this.damageMultiplier = 0.6;
            } break;
            case RAT_CHILD: {
                this.SetMaxHealth(50);
                this.meleeRange = 30;
                this.movementSpeed = 110;
                this.eyeSight = 400;
                this.damageMultiplier = 0.5;
            } break;
            default: {
            } break;
        }

        LoadAnimations();
    }

    public void Update(double deltaTime) {          
        boolean inRange = Game.player.position.distance(this.position) < this.eyeSight;
        /* Only do if in range to save performance. */
        Physics.RaycastResult raycast = inRange ? Game.physics.RayCast(this.position, Game.player.position.sub(this.position), new String[] {"enemy"}) : null;
        boolean raycastObstructed = !(raycast == null || raycast.hit == Game.player);

        if (this.state == State.ROAMING) {
            if (inRange && !raycastObstructed) {
                this.state = State.CHASING;
            }

            Vector2 newLookAtPoint = this.roamingStartPosition.add(new Vector2(Math.random()*700, Math.random()*700));

            if (this.position.distance(this.lookAtPoint) < 5 || (Game.now() - this.lookAtVectorAssignedTime) >= this.lookAtGoToTimeout) {
                this.lookAtPoint = newLookAtPoint;
                this.roamingStoodStillTill = Game.now() + Math.random()*2.5;
                this.lookAtVectorAssignedTime = Game.now();
            }

            if (Game.now() > roamingStoodStillTill) {
                Vector2 movementVector = this.lookAtPoint.sub(this.position);
                this.velocity = this.velocity.add(movementVector.normalize().scale(this.movementSpeed * 0.7));
            }
        } else if (this.state == State.CHASING) {
            if (!inRange || raycastObstructed) {
                this.roamingStartPosition = this.position.scale(1.0);
                this.lookAtVectorAssignedTime = Game.now() + 5.0;
                this.state = State.ROAMING;
            }

            this.lookAtPoint = Game.player.position;

            double noiseX = Game.ng.smoothNoise(Game.now(), this.randomSeed, 0.0);
            double noiseY = Game.ng.smoothNoise(Game.now(), this.randomSeed, 100.0);
            Vector2 noiseVector = new Vector2(noiseX, noiseY).scale(100);
            Vector2 lookAt = Game.player.position.add(noiseVector);

            Vector2 movementVector = lookAt.sub(this.position);

            this.velocity = this.velocity.add(movementVector.normalize().scale(this.movementSpeed));

            if (lookAt.distance(this.position) < this.meleeRange) {
                this.MeleeAttack();
            }

            if (this.canShoot) {
                ShootBullet(lookAt.sub(this.position));
            }
        }

        HumanoidUpdate(deltaTime);
    }

    public void Draw(Graphics2D g) {
        HumanoidDraw(g);
    }
}

class HAR extends Enemy {
    private double runAwayStartTime = 0;

    public HAR() {
        super(HumanoidType.HAR);
    }

    public void Update(double deltaTime) {
        super.Update(deltaTime);

        if (this.state == State.CHASING) {
            if (Game.player.position.distance(this.position) < 30 || (Game.now() - lastMelee) < 0.1) {
                this.lookAtPoint = this.position.add(new Vector2(Math.random()*1000 - 500, Math.random()*1000 - 500));

                this.state = State.HAR_RUNNING_AWAY;
                this.MeleeAttack();

                this.runAwayStartTime = Game.now();
            }
        }

        if (this.state == State.HAR_RUNNING_AWAY) {
            double noiseX = Game.ng.smoothNoise(Game.now(), this.randomSeed, 0.0);
            double noiseY = Game.ng.smoothNoise(Game.now(), this.randomSeed, 100.0);
            Vector2 noiseVector = new Vector2(noiseX, noiseY).scale(100);

            Vector2 lookAt = this.lookAtPoint.add(noiseVector);

            Vector2 movementVector = lookAt.sub(this.position);
            this.velocity = this.velocity.add(movementVector.normalize().scale(this.movementSpeed*1.5));

            if ((Game.now() - runAwayStartTime) > 5) {
                this.state = State.ROAMING;
            }
        }
    }

    public void Draw(Graphics2D g) {
        super.Draw(g);
    }
}

class Bomber extends Enemy {
    private double explodingStartTime = 0;
    private final double explosionRange = 160;
    private final double timeToExplode = 1.0;

    public Bomber() {
        super(HumanoidType.BOMBER);
    }

    @Override
    public void Update(double deltaTime) {
        super.Update(deltaTime);

        if (this.state == State.CHASING) {
            if (this.position.distance(Game.player.position) <= explosionRange) {
                this.state = State.BOMBER_EXPLODING;
                this.explodingStartTime = Game.now();
            }
        }

        if (this.state == State.BOMBER_EXPLODING) {
            this.animState = AnimationState.EXPLODING;
            
            if (this.position.distance(Game.player.position) > explosionRange*3.0) {
                this.state = State.ROAMING;
            }
            
            if ((Game.now() - explodingStartTime) > this.timeToExplode) {
                Game.gfxManager.PlayGFXOnce("gfx_explode", this.position.add(this.size.scale(0.5)), 1.5);

                for (Humanoid h : Game.humanoids) {
                    if (h.type == HumanoidType.HUMAN) {
                        h.health -= 100;
                    }
                }

                this.health = 0;
            }
        }
    }
}

class Rat extends Enemy {
    private final double birthCooldown = 20; 
    private double lastBirthTime = 0;
    protected ArrayList<Enemy> children = new ArrayList<>();
    private int childrenThisBirth = 0;
    private final int maxChildren = 6;

    public Rat() {
        super(HumanoidType.RAT);
    }

    private void Birth() {
        this.lastBirthTime = Game.now();
        this.state = State.RAT_BIRTHING;
        this.childrenThisBirth = 0;
    }

    @Override
    public void Update(double deltaTime) {
        super.Update(deltaTime);

        Iterator<Enemy> iterator = this.children.iterator();
        while (iterator.hasNext()) {
            Enemy child = iterator.next();
            if (child.state == State.DEAD) {
                iterator.remove();
            } else if (child.state == State.ROAMING) {
                if (this.state == State.ROAMING) {
                    Vector2 center = this.position.add(this.size.scale(0.5));
    
                    child.lookAtPoint = center;
    
                    if (child.position.distance(center) < 40) {
                        child.health = Integer.MIN_VALUE;
                    }
                } else {
                    child.lookAtPoint = this.lookAtPoint;
                }

                // child.lookAtPoint = child.lookAtPoint.add(new Vector2(Math.random(), Math.random()).normalize().scale(1000));
            }

            if ((Game.now() - child.spawnTime > 15) || (child.position.distance(this.position) > 1920)) {
                child.health = Integer.MIN_VALUE;
            }
        }
        
        if (this.state == State.CHASING) {
            if (Game.now() - this.lastBirthTime >= this.birthCooldown) {
                Birth();
            }
        } else if (this.state == State.RAT_BIRTHING) {
            this.animState = AnimationState.BIRTHING;

            if ((Game.now() - this.lastBirthTime) > 3) {
                this.state = State.CHASING;
            }

            if (Game.now() - this.lastBirthTime > 1.0 && Math.random() < 0.1 && childrenThisBirth < maxChildren) {
                this.childrenThisBirth ++;

                Enemy child = new Enemy(HumanoidType.RAT_CHILD);

                child.position = this.position.add(new Vector2(Math.random() * this.size.x, Math.random() * this.size.y));

                this.children.add(child);
                Game.humanoids.add(child);
            }
        }
    }
}