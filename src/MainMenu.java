import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

enum MenuState {
    Play,
    Quit
}

public class MainMenu {
    
    MenuState state = null;
    
    public void Update(double deltaTime) {
        // Update the MainMenu
    }
    
    public void Draw(Graphics2D g) {
        double padding = 120;

        Panel menuPanel = new Panel();
        Vector2 size = new Vector2(Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT).sub(new Vector2(padding*2));
        
        size.x = Math.min(size.x, 600);
        size.y = Math.max(size.y, 300);
        
        menuPanel.Begin(g, new Vector2(Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT).scale(0.5).sub(size.scale(0.5)), size);

        menuPanel.Name("Wendigo Main Menu");

        menuPanel.EntryBegin("Play Game");
        if (menuPanel.EntryButton("Press to Play")) {
            this.state = MenuState.Play;
        }
        menuPanel.EntryEnd();

        menuPanel.EntryBegin("Quit");
        if (menuPanel.EntryButton("Press to Quit")) {
            this.state = MenuState.Quit;
        }
        menuPanel.EntryEnd();

        menuPanel.EntryBegin("High Score: " + Game.currentMap.highScore);
        menuPanel.EntryEnd();

        menuPanel.EntryBegin("Controls");
        menuPanel.EntryEnd();

        menuPanel.ListBegin("menuControls", new Vector2(), new Vector2(1.0, 1.0));
            menuPanel.Button("Movement: W A S D", new Vector2(), new Vector2(1.0, 0.0));
            menuPanel.LayoutVertBAdded(0);

            menuPanel.Button("Melee: Q", new Vector2(), new Vector2(1.0, 0.0));
            menuPanel.LayoutVertBAdded(0);

            menuPanel.Button("Shoot: RMB", new Vector2(), new Vector2(1.0, 0.0));
            menuPanel.LayoutVertBAdded(0);

            menuPanel.Button("Dash: Space", new Vector2(), new Vector2(1.0, 0.0));
            menuPanel.LayoutVertBAdded(0);

            menuPanel.Button("Editor: E", new Vector2(), new Vector2(1.0, 0.0));
            menuPanel.LayoutVertBAdded(0);
        menuPanel.ListEnd();

        menuPanel.End();
    }
}