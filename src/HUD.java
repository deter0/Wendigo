import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.Buffer;

import javax.imageio.ImageIO;

public class HUD {

    private BufferedImage dash;
    private BufferedImage bullet;
    private BufferedImage health;
    private BufferedImage gameOver;
    private double elapsedTime = 0;
    //fixed position for the UI
    int fixedX = 100;
    int fixedY = 100;
    

    public HUD() {
        try {
            // Load UI elements
            dash = ImageIO.read(new File("res/dashUI.png"));
            bullet = ImageIO.read(new File("res/Bullet.png"));
            health = ImageIO.read(new File("res/health.png"));
            gameOver = ImageIO.read(new File("res/deathSkull.png"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void Update(double deltaTime) {
        // Update the UI
        elapsedTime += deltaTime;

    }

    public void Draw(Graphics2D g) {

        AffineTransform dashTransform = new AffineTransform();
        AffineTransform healthTransform = new AffineTransform();
        dashTransform.scale(2, 2);
        dashTransform.translate(45 , 80);
        healthTransform.scale(0.25, 0.25);
        healthTransform.translate(180 + 180 + 50, 180);



        //Set font for future text
        g.setColor(Color.WHITE);
        g.setFont(Game.font32);

        // Draw the dash image
        g.drawImage(dash, dashTransform, null);
        if (Game.player.CanDash()) {
            g.drawString("Dash: Ready", fixedX + 75, fixedY + 120);
        } else {
            g.drawString("Dash: Cooling Down " + (int)(((Game.now() - Game.player.lastTimeDashed) / Game.player.dashCooldown)*100) + "%", fixedX + 80, fixedY + 120);
        }
        //Draw bullet image and corrosponding things
        g.drawImage(bullet, fixedX, fixedY + 15, null);
        if (Game.gun.magazine > 0){
            g.drawString("Ammo x " + String.valueOf(Game.gun.magazine), fixedX + 75, fixedY + 45);}
        else{
            g.drawString(String.valueOf("Ammo Reloading " + ((double)(System.currentTimeMillis() - Game.gun.reloadStart) / 1000)), fixedX + 75, fixedY + 45);
        }

        if (Game.player.health > 0) {
            g.drawString("Health x " + Game.player.health, fixedX + 75, fixedY - 20);
        } else {
            g.drawString("Health x 0", fixedX + 75, fixedY - 20);
        }
        //Draw health image and corrosponding things
        g.drawImage(health, healthTransform, null);

        //player death screen once health reaches 0
        if (Game.player.health <= 0) {
            // Game.player.alive = false;
            g.setColor(Color.RED);
            g.setFont(Game.font64);
            g.drawImage(gameOver, Game.WINDOW_WIDTH / 2 - 300, Game.WINDOW_HEIGHT / 2 - 300, null);
            g.drawString("You died...", Game.WINDOW_WIDTH / 2 - 150, Game.WINDOW_HEIGHT / 2 + 150);
            //use this to display score when score functionality is added
            // g.drawString(Game.score + " points", Game.WINDOW_WIDTH / 2 - 150, Game.WINDOW_HEIGHT / 2 + 200);
        }
    }
}