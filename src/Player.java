import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.util.ArrayList;

public class Player extends GameObject {
    public int x = 0, y = 0;
    private BufferedImage idleSpriteSheet, runSpriteSheet;
    private BufferedImage[] idleFrames, runFrames;
    private BufferedImage[] currentFrames; // Active frame set
    public long lastDashed; //check how long its been sicne player has dashed
    public boolean canDash = true; //check if player can dash
    private int currentFrame = 0;
    protected int frameWidth;
    protected int frameHeight;
    private double dx = 0;
    private double dy = 0;
    public boolean reflect;
    private int speed = 500;
    private long lastFrameTime = 0;
    private final int FRAME_DELAY = 100; // 100ms between frames
    private boolean isMoving = false;
    public int health;
    public boolean alive = true;

    private double scale = 2.5;

    // Afterimage data
    private ArrayList<AfterimageData> afterimages = new ArrayList<>();
    private final int AFTERIMAGE_LIFESPAN = 300; // Milliseconds
    private final int MAX_AFTERIMAGES = 5;
    private final int DASH_DISTANCE = 500;

    public Player(int health, int maxHealth) {
        this.health = health;
        try {
            // Load idle sprite sheet
            idleSpriteSheet = ImageIO.read(new File("res/PlayerIdle.png"));
            frameWidth = idleSpriteSheet.getWidth() / 5; // 5 columns
            frameHeight = 48;//idleSpriteSheet.getHeight(); // 1 row
            idleFrames = new BufferedImage[5];
            for (int i = 0; i < 5; i++) {
                idleFrames[i] = idleSpriteSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
            }

            // Load run sprite sheet
            runSpriteSheet = ImageIO.read(new File("res/PlayerRun.png"));
            runFrames = new BufferedImage[6];
            for (int i = 0; i < 6; i++) {
                runFrames[i] = runSpriteSheet.getSubimage(i * frameWidth, 0, frameWidth, frameHeight);
            }

            // Start with idle animation
            currentFrames = idleFrames;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void Draw(Graphics2D g) {
        // Check if the player can dash again
        if (lastDashed != 0 && System.currentTimeMillis() - lastDashed > 3000) {
            canDash = true;
        }
        
        // Draw afterimages
        long currentTime = System.currentTimeMillis();
        for (AfterimageData afterimage : afterimages) {
            if (currentTime - afterimage.timestamp < AFTERIMAGE_LIFESPAN) {
                float alpha = 1.0f - (float) (currentTime - afterimage.timestamp) / AFTERIMAGE_LIFESPAN; // Fade effect
                AffineTransform transform = new AffineTransform();
                transform.translate(afterimage.x, afterimage.y); // Regular position
                transform.scale(2.5, 2.5); // Scale up
                transform.scale(afterimage.reflect ? -1.0 : 1.0, 1.0);
                transform.translate(-frameWidth/2.0, -frameHeight/2.0);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g.drawImage(afterimage.image, transform, null);
            }
        }
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // Reset alpha

        // Calculate the center of the drawn image
        int centerX = x + frameWidth / 2;

        // Check if the mouse is to the left or right of the center of the image
        reflect = Game.worldMousePos.x < centerX;

        // Create a transform for reflection if needed
        AffineTransform transform = new AffineTransform();

        transform.translate(x, y); // Regular position
        transform.translate(-40, 0);

        transform.scale(this.reflect ? -1.0 : 1.0, 1.0);
        if (this.reflect) {
            transform.translate(-frameWidth*scale, 0);
        }

        transform.scale(scale, scale); // Scale up

        // Safeguard against null or invalid frames
        if (currentFrames == null) {
            return;
        }
        if (currentFrame < 0 || currentFrame >= currentFrames.length) {
            //System.err.println("Error: CurrentFrame index is out of bounds! Index: " + currentFrame);
            currentFrame = 0; // Reset to avoid crash
        }
        if (currentFrames[currentFrame] == null) {
            System.err.println("Error: Current frame is null at index: " + currentFrame);
            return;
        }

        // Draw the current frame with the transform
        
        g.drawImage(currentFrames[currentFrame], transform, null);

        // Update to the next frame if enough time has passed
        currentTime = System.currentTimeMillis(); // Use milliseconds for simpler timing
        if (currentTime - lastFrameTime > FRAME_DELAY) {
            currentFrame = (currentFrame + 1) % currentFrames.length;
            lastFrameTime = currentTime;
        }
    }

    public void Update(double deltaTime) {
        x = (int)position.x;
        y = (int)position.y;

        dx = 0;
        dy = 0;

        if (alive){
        if (Game.IsKeyDown(KeyEvent.VK_W)) {
            dy -= 1;
        }
        if (Game.IsKeyDown(KeyEvent.VK_S)) {
            dy += 1;
        }
        if (Game.IsKeyDown(KeyEvent.VK_A)) {
            dx -= 1;
        }
        if (Game.IsKeyDown(KeyEvent.VK_D)) {
            dx += 1;
        }
    }

        if (Game.IsKeyPressed(KeyEvent.VK_SPACE) && (dx != 0 || dy != 0) && canDash) {
            canDash = false;
            lastDashed = System.currentTimeMillis();
            // Add afterimages
            Vector2 offset = new Vector2(dx, dy).normalize(); // Make sure dashing is even on diagonals
            for (int i = 0; i < MAX_AFTERIMAGES; i++) {
                afterimages.add(new AfterimageData(
                    (int)(x + offset.x * DASH_DISTANCE * (0.2 * i)), 
                    (int)(y + offset.y * DASH_DISTANCE * (0.2 * i)), 
                    currentFrames[currentFrame], 
                    reflect, 
                    System.currentTimeMillis()
                ));
            }

            x += DASH_DISTANCE * offset.x;
            y += DASH_DISTANCE * offset.y;
        }

        // Normalize the movement vector
        double length = Math.sqrt(dx * dx + dy * dy);
        isMoving = length != 0; // Determine if the player is moving

        if (length != 0) {
            dx /= length;
            dy /= length;
        }

        // Apply movement scaled by speed and deltaTime
        x += dx * speed * deltaTime;
        y += dy * speed * deltaTime;

        // Switch between animations based on movement
        currentFrames = isMoving ? runFrames : idleFrames;

        this.position = new Vector2(x, y);
        // this.velocity = new Vector2(dx * speed, dy * speed);
        this.size = new Vector2(this.frameWidth * scale * 0.4, this.frameHeight * scale * 0.8);
    }

    // Data structure for afterimages
    private class AfterimageData {
        int x, y;
        BufferedImage image;
        boolean reflect;
        long timestamp;

        AfterimageData(int x, int y, BufferedImage image, boolean reflect, long timestamp) {
            this.x = x;
            this.y = y;
            this.image = image;
            this.reflect = reflect;
            this.timestamp = timestamp;
        }
    }
}
