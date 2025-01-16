import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class Enemy extends GameObject {
    private int x, y;
    private final int RADIUS = 25; // Radius of the red circle
    private final int SPEED = 200; // Speed of the enemy

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
    private final double FRAME_DURATION = 0.1; // 0.1 seconds per frame
    private final double ATTACK_INTERVAL = 3.0; // 3 seconds between attacks;

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
        transform.translate(x - (RADIUS * scaleFactor), y - (RADIUS * scaleFactor));
        
        // Check if the image should be flipped
        if (x > Game.player.x) {
            // Flip the image horizontally from its center
            transform.translate(RADIUS * scaleFactor, 0); // Move origin for horizontal flip
            transform.scale(-scaleFactor, scaleFactor);  // Flip horizontally and scale
            transform.translate(-RADIUS * scaleFactor, 0); // Move origin back
            direction = true;
        } else if (x < Game.player.x) {
            // Normal scaling
            transform.scale(scaleFactor, scaleFactor);
            direction = false;
        } else if (x == Game.player.x && !direction) {
            // Normal scaling
            transform.scale(scaleFactor, scaleFactor);
        } 
        else if (x == Game.player.x && direction) {
            // Flip the image horizontally from its center
            transform.translate(RADIUS * scaleFactor, 0); // Move origin for horizontal flip
            transform.scale(-scaleFactor, scaleFactor);  // Flip horizontally and scale
            transform.translate(-RADIUS * scaleFactor, 0); // Move origin back
        }
        
        
        g.setColor(Color.RED);
        g.fillRect(x, y, 10, 10);
        g.fillRect(Game.player.x, Game.player.y, 10, 10);
        // Draw the image
        g.drawImage(currentFrame, transform, null);
    }
    
    

    public void Update(double deltaTime) {
        attackTimer += deltaTime;
        frameTimer += deltaTime;

        if (attackTimer >= ATTACK_INTERVAL) {
            // Start attacking
            isAttacking = true;
            attackTimer = 0;
            currentAttackFrame = 0;
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
        setTargetDirection(dx, dy);

        x += targetDX * SPEED * deltaTime;
        y += targetDY * SPEED * deltaTime;
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
