/* Game.Java
* Author: Ibraheem Mustafa, Faseeh Ahmed
* Date Created: Dec 11th, 2024
* Date Last Edited: Dec 11th, 2024
*/

/* GamePanel class acts as the main "game loop" - continuously runs the game and calls whatever needs to be called
Child of JPanel because JPanel contains methods for drawing to the screen
Implements KeyListener interface to listen for keyboard input
Implements Runnable interface to use "threading" - let the game do two things at once
*/

import javax.swing.*;

import java.awt.event.*;
import java.awt.*;
import java.awt.Graphics2D;
import java.io.*;

public class Game extends JPanel implements Runnable, KeyListener {
    private Thread gameThread; // Thread game is ran on
    private JFrame parentJFrame; // Parent frame passed through constructor
    
    /*  Having keysDownLastFrame and keys down this frame so we can implement Game.isKeyDown
        that can be called at any time, instead of events.
    */
    public static final int MAX_KEYS = 256; // We track most keys 0->256
    public static boolean[] keysDownLastFrame = new boolean[MAX_KEYS]; // Last frame
    public static boolean[] keysDown = new boolean[MAX_KEYS]; // Current frame

    /*
     * Same as above
     */
    public static final int MAX_MOUSE_BUTTONS = 5;
    public static boolean[] mouseButtonsDown = new boolean[MAX_MOUSE_BUTTONS]; // LMB, MMB, RMB, etc.
    public static boolean[] mouseButtonsDownLastFrame = new boolean[MAX_MOUSE_BUTTONS];

    public static boolean gameRunning = true; // Game running, modifiable
    
    public static int WIDTH = 1600; // Variable Game size
    public static int HEIGHT = 900;

    static Vector2 mousePos = new Vector2(); // Mouse position so classes can access Game.mousePos
    
    // Last reported FPS
    public static double FPS;

    // All fonts
    public static Font font16;
    public static Font font32;
    public static Font font64;

    // Time stuff
    public static double gameStart = System.currentTimeMillis()/1000.0;
    public static double now() {
        return System.currentTimeMillis()/1000.0;
    }

    // Current cursor
    public static Cursor currentCursor = null;

    // Good Graphics
    public static GG gg = new GG();

    public static Player player = new Player(100, 100, 0);
    // Map Editor
    public static TileMapEditor mapEditor = new TileMapEditor();

    public Vector2 testPosition = new Vector2();

    public Game(JFrame parentFrame) {
        this.parentJFrame = parentFrame;
        
        this.setFocusable(true); // make everything in this class appear on the screen
        this.addKeyListener(this); // start listening for keyboard input
        
        Game game = this;
        addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
                int mouseButtonIndex = e.getButton();
                if (mouseButtonIndex >= 0 && mouseButtonIndex < Game.mouseButtonsDown.length) { // If we keep track of it
                    if (e.getID() == MouseEvent.MOUSE_PRESSED) { // If it's pressed store it in the array, otherwise reset it
                        Game.mouseButtonsDown[mouseButtonIndex] = true;
                    }
                }
			}
            public void mouseReleased(MouseEvent e) {
                int mouseButtonIndex = e.getButton();
                if (mouseButtonIndex >= 0 && mouseButtonIndex < Game.mouseButtonsDown.length) { // If we keep track of it
                    if (e.getID() == MouseEvent.MOUSE_RELEASED) { // If it's released store that info  in the array
                        Game.mouseButtonsDown[mouseButtonIndex] = false;
                    }
                }
            }
		});
        addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (Game.currentCursor != null) {
                    game.setCursor(Game.currentCursor);
                }
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                if (Game.currentCursor != null) {
                    game.setCursor(Game.currentCursor);
                }
            }
        });

        System.out.println("[LOG]: Loading fonts");
        // Try to load fonts from file or else print errors and load fallback font
        try {
            Game.LoadFontsFromFile();
            System.out.println("[LOG]: Loaded fonts succesfully.");
        } catch (IOException e) {
            System.out.println("[ERROR]: Encountered `IOException` while loading fonts: " + e.getLocalizedMessage());
            Game.SetFonts(null);
        } catch (FontFormatException e) {
            System.out.println("[ERROR]: Encountered `FontFormatException` while loading fonts: " + e.getLocalizedMessage());
            Game.SetFonts(null);
        }

        this.gameThread = new Thread(this);
        System.out.println("[LOG]: Starting game thread.");
        this.gameThread.start(); // Start game 

        // Maximize window
        this.parentJFrame.setExtendedState( this.parentJFrame.getExtendedState()|JFrame.MAXIMIZED_BOTH );

        TileMap testMap = new TileMap("foenam !", 20, 20);
        mapEditor.EditMap(testMap);
    }

    public void Update(double deltaTime) {
        Game.mapEditor.Update(deltaTime);
        Game.player.Update(deltaTime);
        if (Game.IsKeyDown(KeyEvent.VK_S)) {
            testPosition = testPosition.add(new Vector2(0, 100.0).scale(deltaTime));
        }

        System.out.println(Game.IsKeyDown(KeyEvent.VK_S));

        // System.out.println("Game tick! At " + 1.0/deltaTime + "TPS");
    }

    private void DrawFPS(Graphics2D g) {
        g.setFont(Game.font32);
        
        String text = Long.toString(Math.round(Game.FPS)) + " FPS";
        FontMetrics m = g.getFontMetrics();
        int textWidth = m.stringWidth(text);

        g.setColor(Color.GREEN);
        g.drawString(text, Game.WIDTH - textWidth - 10, 32 + 10);
    }

    public void Draw(Graphics2D g) {
        g.setColor(Color.WHITE);
        g.fill3DRect(0, 0, 100, 100, true);
        //draw player obj
        player.Draw(g);
        GG.fillOval(Math.sin(Game.now() * 2.0) * 100.0 + 200.0, 150.0, 50.0, 50.0);

        g.setFont(Game.font32);
        g.drawString("Press escape to exit 1234567890", 300, 400);

        if (Game.mapEditor.isDrawn) {
            Game.mapEditor.Draw(g);
        }

        g.setColor(Color.RED);
        GG.fillRect(testPosition.x, testPosition.y, 100, 100);

        this.DrawFPS(g);
    }

    @Override
    public void paint(Graphics gAbs) { // Override JPanel paint method
        super.paint(gAbs);
        Graphics2D g = (Graphics2D)gAbs;

        // Set good graphic's graphic context to the new graphic's context
        GG.g = g;


        // Set anti-aliasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON); 

        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        Game.currentCursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
        this.Draw(g);
        
        // Update mouse button down arrays
        for (int i = 0; i < Game.mouseButtonsDown.length; i++) {
            Game.mouseButtonsDownLastFrame[i] = Game.mouseButtonsDown[i];
        }
    }

    @Override
    public void run() {
        final double refreshRate = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getRefreshRate();
        final double TARGET_FPS = refreshRate; // FPS we're aiming for'
        
        System.out.println("[LOG]: Monitor refresh rate is: " + refreshRate + " hz. Targetting.");

        // TODO: Test if `setOpaque(true)` has flickering on other platforms.
        // Confirmed flickering on: Plasma/ Wayland
        // this.setOpaque(false);
        this.setDoubleBuffered(true);
        
        // Last time
        double lastTick = System.currentTimeMillis()/1000.0;
        double deltaTime = 0;

        while (Game.gameRunning) { // Loop while game is running
            deltaTime = Game.now() - lastTick; // Calculate delta

            // Update mouse position
            Point mp = this.getMousePosition();
            if (mp != null)
                Game.mousePos = new Vector2(mp.x, mp.y);

            if (deltaTime >= 1.0/TARGET_FPS) { // If we're ready to render a frame render it
                FPS = 1.0 / deltaTime;

                Game.WIDTH = this.getWidth();
                Game.HEIGHT = this.getHeight();

                Update(deltaTime);
                repaint(); // Tell the panel to call paint
                lastTick = Game.now(); //Update( Update last tick

                // Update keysdown array
                for (int i = 0; i < Game.keysDown.length; i++) {
                    Game.keysDownLastFrame[i] = Game.keysDown[i];
                    Game.keysDown[i] = false;
                }
            } else {
                try {
                    Thread.sleep((long)((1.0/TARGET_FPS - deltaTime)*900.0), 0); // Sleep 1ms if we're doing nothing so we don't bog CPU
                } catch (InterruptedException err) {
                    System.err.println(err);
                }
            }
        }

        System.out.println("[LOG]: Closing window.");

        // Close the window properly
        this.parentJFrame.dispose();
        this.parentJFrame.dispatchEvent(new WindowEvent(this.parentJFrame, WindowEvent.WINDOW_CLOSING)); // Call close event
    }

    // Function to set the games fonts, if we failed to load fall back
    public static void SetFonts(String fontName) {
        if (fontName == null) {
            System.out.println("[WARN]: Using fallback fonts. You may encounter issues..");
            fontName = "Cascadia Code";
        }

        // Set the fonts
        Game.font16 = new Font(fontName, Font.PLAIN, 24);
        Game.font32 = new Font(fontName, Font.PLAIN, 32);
        Game.font64 = new Font(fontName, Font.PLAIN, 64);
    }

    // Function to load our custom fonts from disk and register them
    public static void LoadFontsFromFile() throws IOException, FontFormatException {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        
        // List of all fonts we need
        Font[] PixelifySans = {
            Font.createFont(Font.TRUETYPE_FONT, new File("./res/fonts/PixelifySans-Regular.ttf")),
            Font.createFont(Font.TRUETYPE_FONT, new File("./res/fonts/PixelifySans-Bold.ttf")),
            Font.createFont(Font.TRUETYPE_FONT, new File("./res/fonts/PixelifySans-SemiBold.ttf"))
        };

        // Log and register them
        for (Font f : PixelifySans) {
            System.out.println("[LOG]: Font Loaded \"" + f.getFontName() + "\" from file successfully!");

            ge.registerFont(f);
        }

        // Set them as the base font
        Game.SetFonts(PixelifySans[0].getFontName());
    }

    static boolean IsKeyDown(int keycode) {
        return Game.keysDown[keycode] == true; // Check if it's down in our array'
    }
    static boolean IsKeyPressed(int keycode) {
        return Game.keysDown[keycode] == true && Game.keysDownLastFrame[keycode] == false; // It's just pressed if it wasn't pressed last frame but is now
    }
    static boolean IsKeyReleased(int keycode) {
        return Game.keysDown[keycode] == false && Game.keysDownLastFrame[keycode] == true; // Opposite for this
    }

    static boolean IsMouseDown(int mouseButton) {
        return Game.mouseButtonsDown[mouseButton] == true; // Check if it's down in our array'
    }
    static boolean IsMousePressed(int mouseButton) {
        return Game.mouseButtonsDown[mouseButton] == true && Game.mouseButtonsDownLastFrame[mouseButton] == false; // It's just pressed if it wasn't pressed last frame but is now
    }
    static boolean IsMouseReleased(int mouseButton) {
        return Game.mouseButtonsDown[mouseButton] == false && Game.mouseButtonsDownLastFrame[mouseButton] == true; // Opposite for this
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int keyCode = e.getKeyCode();
        
        // If it's escape close the game.
        if (keyCode == KeyEvent.VK_ESCAPE) {
            gameRunning = false;
        }
        
        if (keyCode > MAX_KEYS) {
            System.err.println("[WARN]: Encountered a key in key down larger than " + MAX_KEYS + ": " + keyCode);
        } else {
            Game.keysDown[keyCode] = true; // Set the keycode in our keying system to true
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int keyCode = e.getKeyCode();
        
        if (keyCode > MAX_KEYS) {
            System.err.println("[WARN]: Encountered a key in key up larger  than " + MAX_KEYS + ": " + keyCode);
        } else {
            System.out.println("Key up!");
            Game.keysDown[keyCode] = false; // But false
        }
    }

    @Override
    public void keyTyped(KeyEvent e) { }
}