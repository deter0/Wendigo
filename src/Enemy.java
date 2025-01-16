import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.util.Random;

public class Enemy extends GameObject {
    private Random interval = new Random();
    public int x, y;
    private final int RADIUS = 25; // Radius of the red circle
    private int speed = 200; // Speed of the enemy
    public static final int ENEMY_RADIUS = 100; // Radius of the enemy's movement compared to each other.
    public static final int ALTER_SPEED = 50; // Speed of the enemy when they are close to each other.
    public static final int PLAYER_RADIUS = 50; // Radius of the player's movement compared to the enemy.
    private double targetDX = 0; // Target direction X
    private double targetDY = 0; // Target direction Y
    private boolean direction; //false = right, true = left
    private BufferedImage runSpritesheet;
    private BufferedImage attackSpritesheet;
    private int currentRunFrame = 0;
    private int currentAttackFrame = 0;
    private boolean isAttacking = false;
    private double frameTimer = 0; // Timer for switching frames
    private double attackTimer = 0; // Timer for triggering attacks
    private final int RUN_FRAMES = 7;
    private final int ATTACK_FRAMES = 5;
    private final double FRAME_DURATION = 0.2; // 0.2 seconds per frame
    private final double ATTACK_INTERVAL = interval.nextDouble(); // 3 seconds between attacks;

    public Enemy(int startX, int startY) {
        this.x = startX;
        this.y = startY;

        try {
            runSpritesheet = ImageIO.read(new File("res/enemyrun.png"));
            attackSpritesheet = ImageIO.read(new File("res/enemyattack.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void Draw(Graphics2D g) {
        BufferedImage currentFrame;
        if (isAttacking) {
            currentFrame = attackSpritesheet.getSubimage(
                (currentAttackFrame % ATTACK_FRAMES) * (attackSpritesheet.getWidth() / ATTACK_FRAMES),
                0,
                attackSpritesheet.getWidth() / ATTACK_FRAMES,
                attackSpritesheet.getHeight()
            );
        } else {
            currentFrame = runSpritesheet.getSubimage(
                (currentRunFrame % RUN_FRAMES) * (runSpritesheet.getWidth() / RUN_FRAMES),
                0,
                runSpritesheet.getWidth() / RUN_FRAMES,
                runSpritesheet.getHeight()
            );
        }
    
        double scaleFactor = 1.4; // Adjust the factor as needed for the desired size

        // Create an AffineTransform for scaling and flipping
        AffineTransform transform = new AffineTransform();
        
        // Translate to center the image
        transform.translate(x, y);
        transform.translate(-20, -currentFrame.getHeight()/2.0);
        
        // Check if the image should be flipped
        // if (x > Game.player.x) {
        //     // Flip the image horizontally from its center
        //     transform.translate(RADIUS * scaleFactor, 0); // Move origin for horizontal flip
        //     transform.scale(-scaleFactor, scaleFactor);  // Flip horizontally and scale
        //     transform.translate(-RADIUS * scaleFactor, 0); // Move origin back
        //     direction = true;
        // } else if (x < Game.player.x) {
        //     // Normal scaling
        //     transform.scale(scaleFactor, scaleFactor);
        //     direction = false;
        // } else if (x == Game.player.x && !direction) {
        //     // Normal scaling
        //     transform.scale(scaleFactor, scaleFactor);
        // } 
        // else if (x == Game.player.x && direction) {
        //     // Flip the image horizontally from its center
        //     transform.translate(RADIUS * scaleFactor, 0); // Move origin for horizontal flip
        //     transform.scale(-scaleFactor, scaleFactor);  // Flip horizontally and scale
        //     transform.translate(-RADIUS * scaleFactor, 0); // Move origin back
        // }
        direction = x > Game.player.x;
        if (direction) {
            transform.translate(currentFrame.getWidth() * scaleFactor, 0);
            transform.scale(-1.0, 1.0);
        }
        transform.scale(scaleFactor, scaleFactor);
        
        
        g.setColor(Color.RED);
        g.drawOval(x, y, ENEMY_RADIUS, ENEMY_RADIUS);
        // Draw the image
        g.drawImage(currentFrame, transform, null);

        // size.x = currentFrame.getWidth() * 0.8;
        // size.y = currentFrame.getHeight();
    }

    public void Update(double deltaTime) {
        x = (int)position.x;
        y = (int)position.y;

        attackTimer += deltaTime;
        frameTimer += deltaTime;
    
        if (Math.sqrt(Math.pow(Game.player.x - x, 2) + Math.pow(Game.player.y - y, 2)) < PLAYER_RADIUS || x == Game.player.x) {
            // Start attacking
            isAttacking = true;
            if (attackTimer >= ATTACK_INTERVAL) {
                attackTimer = 0;
                currentAttackFrame = 0;
            }
            Game.player.health --;
        }
    
        if (isAttacking) {
            if (frameTimer >= FRAME_DURATION) {
                frameTimer = 0;
                currentAttackFrame++;
                if (currentAttackFrame >= ATTACK_FRAMES) {
                    isAttacking = false; // End attack
                }
            }
            return; // Skip movement while attacking
        }
    
        // Update run animation frame
        if (frameTimer >= FRAME_DURATION) {
            frameTimer = 0;
            currentRunFrame++;
        }
    
        // Follow the player
        int playerX = Game.player.x;
        int playerY = Game.player.y;
    
        double dx = playerX - x;
        double dy = playerY - y;
    
        // Check for nearby enemies and adjust direction
        for (Enemy other : Game.enemies) { // Assuming Game.enemies is a list of all enemies
            if (other != this) {
                double distance = Math.sqrt(Math.pow(other.x - this.x, 2) + Math.pow(other.y - this.y, 2));
                if (distance < ENEMY_RADIUS) {
                    // Move away from the other enemy
                    double repelDX = this.x - other.x;
                    double repelDY = this.y - other.y;
                    double repelLength = Math.sqrt(repelDX * repelDX + repelDY * repelDY);
                    if (repelLength > 0) {
                        dx += (repelDX / repelLength) * ALTER_SPEED;
                        dy += (repelDY / repelLength) * ALTER_SPEED;
                    }
                }
            }
        }
    
        setTargetDirection(dx, dy);
    
        // Move the enemy
        x += targetDX * speed * deltaTime;
        y += targetDY * speed * deltaTime;

        System.out.println(x + ", " + y);
    }
    

    public void setTargetDirection(double dx, double dy) {
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length > 0) {
            this.targetDX = dx / length;
            this.targetDY = dy / length;
        } else {
            this.targetDX = 0;
            this.targetDY = 0;
        }
    }


    // Getter methods
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getRadius() {
        return RADIUS;
    }
}
