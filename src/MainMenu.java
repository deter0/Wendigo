import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

public class MainMenu {

    private BufferedImage mainMenu;

    public MainMenu(){
        // Load the main menu
        try {
            mainMenu = ImageIO.read(new File("res/MainMenu.png"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }

    public void Update(double deltaTime) {
        // Update the MainMenu

    }

    public void Draw(Graphics2D g) {
        // Draw the MainMenu
        g.drawImage(mainMenu, 0, 0, null);
    }
}