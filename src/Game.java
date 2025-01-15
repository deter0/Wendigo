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

import javax.imageio.ImageIO;
import javax.swing.*;

import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.awt.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

public class Game extends JPanel implements Runnable, KeyListener {
    private Thread gameThread; // Thread game is ran on
    private JFrame parentJFrame; // Parent frame passed through constructor
    
    private double targetFrameTime;

    /*
        Many different things can block and unblock inputs at once.
        For example if this was a boolean many things could set it to true and false many times a frame.
        For that reason input is free when the size of this array is zero.
    */
    public static ArrayList<String> inputBlockers = new ArrayList<>();
    
    /*  Having keysDownLastFrame and keys down this frame so we can implement Game.isKeyDown
        that can be called at any time, instead of events.
    */
    public static final int MAX_KEYS = 256; // We track most keys 0->256
    public static boolean[] keysDownLastFrame = new boolean[MAX_KEYS]; // Last frame
    public static boolean[] keysDown = new boolean[MAX_KEYS]; // Current frame

    /*
        Text the user has inputted, taking into account the capitals and modifers (!@#$%^&*) and such.
        Will start overwriting after 1024 characters.
    */
    public static String textInputBuffer = "";

    /*
     * Same as above
     */
    public static final int MAX_MOUSE_BUTTONS = 5;
    public static boolean[] mouseButtonsDown = new boolean[MAX_MOUSE_BUTTONS]; // LMB, MMB, RMB, etc.
    public static boolean[] mouseButtonsDownLastFrame = new boolean[MAX_MOUSE_BUTTONS];

    private static double scrollThisFrame;
    private static double scrollLastFrame;
    public static double deltaScroll;

    public static boolean gameRunning = true; // Game running, modifiable
    
    public static int WINDOW_WIDTH = 1600; // Variable Game size
    public static int WINDOW_HEIGHT = 900;

    public static int WIDTH = WINDOW_WIDTH;
    public static int HEIGHT = WINDOW_HEIGHT;

    // This one is in the world (relative to the camera)
    static Vector2 worldMousePos = new Vector2(); // Mouse position so classes can access Game.mousePos
    static Vector2 mousePos = new Vector2();  // Relative to the top left
    
    // Last reported FPS
    public static double FPS;
    public static double deltaTime;

    // All fonts
    public static Font font16;
    public static Font font32;
    public static Font font64;

    // Time stuff
    public static double gameStart = System.currentTimeMillis()/1000.0;
    public static double now() {
        return (double)System.nanoTime() / (double)1e9 - gameStart;
    }

    // Current cursor
    public static Cursor currentCursor = null;

    // Good Graphics
    public static GG gg = new GG();

    public static AffineTransform worldTransform = new AffineTransform();

    // Initialize the player
    public static Player player = new Player(100, 100, 0);
    public Weapon gun = new Weapon(1000, 10, 10, player);

    // Load test map
    TileMap testMap;
    TileMapEditor editor;

    public boolean editorEnabled = false;

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
        addMouseWheelListener(new MouseWheelListener() {
           @Override
           public void mouseWheelMoved(MouseWheelEvent e) {
               Game.scrollThisFrame += e.getPreciseWheelRotation() * e.getScrollAmount();
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


        this.testMap = new TileMap(100, 100);
        SpriteSheet def = this.testMap.LoadSpriteSheet("res/Tile_set.png", 16);

        this.editor = new TileMapEditor(testMap);
        
        this.testMap.LoadFromFile("./res/tempMapFile.wmap");
        // Maximize window
        this.parentJFrame.setExtendedState( this.parentJFrame.getExtendedState()|JFrame.MAXIMIZED_BOTH );
    }

    public void Update(double deltaTime) {
        worldTransform = new AffineTransform();

        worldTransform.translate(Game.WINDOW_WIDTH/2.0 - player.frameWidth / 2.0,
                                Game.WINDOW_HEIGHT/2.0 - player.frameHeight / 2.0);

        worldTransform.translate(-player.x, -player.y);

        Point mousePoint = new Point((int)mousePos.x, (int)mousePos.y);
        Point worldMousePoint = new Point();
        try {
            worldTransform.inverseTransform(mousePoint, worldMousePoint);
        } catch (NoninvertibleTransformException e) {

        }
        Game.worldMousePos = new Vector2(worldMousePoint.x, worldMousePoint.y);

        player.Update(deltaTime);
        gun.Update(deltaTime);

        if (this.editorEnabled) {
            editor.Update(deltaTime);
        }

        if (Game.IsKeyPressed(KeyEvent.VK_E)) {
            this.editorEnabled = !this.editorEnabled;
            if (this.editorEnabled && Game.IsKeyDown(KeyEvent.VK_SHIFT)) {
                this.editor = new TileMapEditor(this.testMap);
            }
        }
        // System.out.println("Game tick! At " + 1.0/deltaTime + "TPS");
    }

    private void DrawFPS(Graphics2D g) {
        if (TileMapEditor.ED_FONT != null) {
            g.setFont(TileMapEditor.ED_FONT);
        } else {
            g.setFont(Game.font16);
        }
        
        String text = Long.toString(Math.round(Game.FPS)) + " FPS";
        FontMetrics m = g.getFontMetrics();
        int textWidth = m.stringWidth(text);

        g.setColor(Color.GREEN);
        g.drawString(text, Game.WIDTH - textWidth - 10, 32 + 10);
    }

    BufferedImage la = null;
    VolatileImage va = null;

    public void Draw(Graphics2D g) {
        AffineTransform defaultTransform = g.getTransform();

        g.setTransform(Game.worldTransform);
        testMap.Draw(g);

        //draw player obj
        player.Draw(g);
        //draw the weapon projectiles
        gun.Draw(g);

        if (this.editorEnabled) {
            try {
                // Workaround: Temporarily set mouse to world mouse then revert for editor
                Vector2 origPos = Game.mousePos.scale(1);
                Game.mousePos = Game.worldMousePos; 
                this.editor.Draw(g);
                Game.mousePos = origPos;
            } catch (NoninvertibleTransformException e) {
                // Unreachable, to my knowledge.
            }
        }
        
        Panel.InputField("Enter Name");

        g.setTransform(defaultTransform);
        this.DrawFPS(g);
        
        Panel.Draw(g);
    }

    public static void ResetMouse() {
        Game.deltaScroll = 0;
        for (int i = 0; i < Game.mouseButtonsDown.length; i++) {
            Game.mouseButtonsDown[i] = false;
            Game.mouseButtonsDownLastFrame[i] = false;
        }
    }

    private double lastDraw = Game.now();
    private double nextDrawDeadline = -Double.MAX_VALUE;
    @Override
    public void paint(Graphics gAbs) { // Override JPanel paint method
        if (Game.gameRunning == false) this.Close();

        double now = Game.now();
        double deltaTime = now - this.lastDraw;
        
        if (now < nextDrawDeadline) {
            try {
                Thread.sleep(0, 100);
            } catch (IllegalArgumentException | InterruptedException e) {
                e.printStackTrace();
            }

            repaint();
            return;
        }
        
        nextDrawDeadline = now + this.targetFrameTime - this.targetFrameTime/16.0;

        Point mp = this.getMousePosition();

        if (mp != null)
            Game.mousePos = new Vector2(mp.x, mp.y);

        Game.WINDOW_WIDTH = this.getWidth();
        Game.WINDOW_HEIGHT = this.getHeight();
        
        Game.WIDTH = Game.WINDOW_WIDTH;
        Game.HEIGHT = Game.WINDOW_HEIGHT;    

        Game.FPS = 1.0 / deltaTime;
        lastDraw = Game.now();

        Game.deltaTime = deltaTime;

        /* Required Update logic */
        Game.deltaScroll = Game.scrollThisFrame - Game.scrollLastFrame;
        Game.scrollLastFrame = Game.scrollThisFrame;
        
        // Update the game
        Update(deltaTime);
        
        // super.paint(gAbs);
        Graphics2D g = (Graphics2D)gAbs;
        
        // GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
        //                                                 .getDefaultScreenDevice()
        //                                                 .getDefaultConfiguration();

        // System.out.println(gc.getImageCapabilities().isAccelerated());

        // VolatileImage backBuffer = gc.createCompatibleVolatileImage(WINDOW_WIDTH, WINDOW_HEIGHT, Transparency.OPAQUE);
        // backBuffer.setAccelerationPriority(1.f);

        // System.out.println("Backbuffer accelerated: " + backBuffer.getCapabilities(gc).isAccelerated());

        // Graphics2D bg = backBuffer.createGraphics();


        // Set good graphic's graphic context to the new graphic's context
        GG.g = g;

        
        // Set anti-aliasing
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        
        // g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        //     RenderingHints.VALUE_ANTIALIAS_ON); 
        
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
        
        Game.currentCursor = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
        this.Draw(g);
        
        // Update mouse button down arrays
        for (int i = 0; i < Game.mouseButtonsDown.length; i++) {
            Game.mouseButtonsDownLastFrame[i] = Game.mouseButtonsDown[i];
        }
        
        // Update keysdown array
        for (int i = 0; i < Game.keysDown.length; i++) {
            Game.keysDownLastFrame[i] = Game.keysDown[i];
        }

        g.dispose();

        // g.drawImage(backBuffer, null, null);

        repaint((int)(this.targetFrameTime*900));
    }

    public void Close() {
        System.out.println("[LOG]: Closing window.");

        // Close the window properly
        this.parentJFrame.dispose();
        this.parentJFrame.dispatchEvent(new WindowEvent(this.parentJFrame, WindowEvent.WINDOW_CLOSING)); // Call close event
    }

    @Override
    public void run() {
        final double refreshRate = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getRefreshRate();
        final double TARGET_FPS = refreshRate; // FPS we're aiming for'
        
        this.targetFrameTime = 1.0 / TARGET_FPS;

        System.out.println("[LOG]: Monitor refresh rate is: " + refreshRate + " hz. Targetting.");

        // TODO: Test if `setOpaque(true)` has flickering on other platforms.
        // Confirmed flickering on: Plasma/ Wayland
        // this.setOpaque(false);
        // this.setDoubleBuffered(true);
        
        // Last time
        // double lastTick = System.currentTimeMillis()/1000.0;
        double deltaTime = 0;

        repaint();

        // while (Game.gameRunning) { // Loop while game is running
        //     deltaTime = Game.now() - lastTick; // Calculate delta

        //     // Update mouse position
        //     Point mp = this.getMousePosition();
        //     if (mp != null)
        //         Game.mousePos = new Vector2(mp.x, mp.y);
                
        //     if (deltaTime >= 1.0/TARGET_FPS/2.0) { // If we're ready to render a frame render it
        //         // System.out.println(1.0/deltaTime);

        //         Game.WINDOW_WIDTH = this.getWidth();
        //         Game.WINDOW_HEIGHT = this.getHeight();
                
        //         Game.WIDTH = Game.WINDOW_WIDTH;
        //         Game.HEIGHT = Game.WINDOW_HEIGHT;        

        //         // repaint(); // Tell the panel to call paint
                
        //         lastTick = Game.now(); //Update( Update last tick)
        //     } else {
        //         try {
        //             //Thread.sleep((long)((1.0/TARGET_FPS - deltaTime)*600.0), 0); // Sleep 1ms if we're doing nothing so we don't bog CPU
        //             Thread.sleep(1);
        //         } catch (InterruptedException err) {
        //             System.err.println(err);
        //         }
        //     }
        // }
    }


    // @Override
    // public void run() {
    //     final double refreshRate = GraphicsEnvironment.getLocalGraphicsEnvironment()
    //             .getDefaultScreenDevice().getDisplayMode().getRefreshRate();
    //     final double TARGET_FPS = (refreshRate > 0) ? refreshRate : 60.0;
    //     final double frameDuration = 1.0 / TARGET_FPS;

    //     System.out.println("[LOG]: Monitor refresh rate is: " + refreshRate + " hz. Targeting " + TARGET_FPS + " FPS.");
    //     System.out.println("[LOG] Optimized drawing enabled: " + isOptimizedDrawingEnabled());

    //     this.setDoubleBuffered(true);

    //     double lastTick = System.nanoTime() / 1e9;
    //     double nextFrameTime = lastTick + frameDuration;

    //     while (Game.gameRunning) {
    //         double now = System.nanoTime() / 1e9;
    //         if (now >= nextFrameTime) {
    //             // System.out.println("haha");
    //             Game.WINDOW_WIDTH = this.getWidth();
    //             Game.WINDOW_HEIGHT = this.getHeight();

    //             paintImmediately(new Rectangle(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT));

    //             lastTick = now;
    //             nextFrameTime = now+frameDuration;
    //         } else {
    //             try {
    //                 Thread.sleep((long) ((nextFrameTime - now) * 1000));
    //             } catch (InterruptedException err) {
    //                 System.err.println(err);
    //             }
    //         }
    //     }

    //     System.out.println("[LOG]: Closing window.");
    //     this.parentJFrame.dispose();
    //     this.parentJFrame.dispatchEvent(new WindowEvent(this.parentJFrame, WindowEvent.WINDOW_CLOSING));
    // }

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
        if (Game.inputBlockers.size() > 0) return false;
        
        return Game.keysDown[keycode] == true; // Check if it's down in our array'
    }
    static boolean IsKeyPressed(int keycode) {
        if (Game.inputBlockers.size() > 0) return false;
        
        return Game.keysDown[keycode] == true && Game.keysDownLastFrame[keycode] == false; // It's just pressed if it wasn't pressed last frame but is now
    }
    static boolean IsKeyReleased(int keycode) {
        if (Game.inputBlockers.size() > 0) return false;
        
        return Game.keysDown[keycode] == false && Game.keysDownLastFrame[keycode] == true; // Opposite for this
    }

    static boolean IsMouseDown(int mouseButton) {
        if (Game.inputBlockers.size() > 0) return false;
        
        return Game.mouseButtonsDown[mouseButton] == true; // Check if it's down in our array'
    }
    static boolean IsMousePressed(int mouseButton) {
        if (Game.inputBlockers.size() > 0) return false;
        
        return Game.mouseButtonsDown[mouseButton] == true && Game.mouseButtonsDownLastFrame[mouseButton] == false; // It's just pressed if it wasn't pressed last frame but is now
    }
    static boolean IsMouseReleased(int mouseButton) {
        if (Game.inputBlockers.size() > 0) return false;
        
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
            Game.keysDown[keyCode] = false; // But false
        }
    }
    
    @Override
    public void keyTyped(KeyEvent e) {
        int keyCode = e.getKeyCode();
        char c = e.getKeyChar();
        
        if (!Character.isISOControl(c)) {
            Game.textInputBuffer = Game.textInputBuffer.concat(Character.toString(c));
        }
        if (c == 8 && Game.textInputBuffer.length() > 0) {
            Game.textInputBuffer = Game.textInputBuffer.substring(0, Game.textInputBuffer.length() - 1);
        }
        
        if (Game.textInputBuffer.length() >= 1024) {
            Game.textInputBuffer = Game.textInputBuffer.substring(1, Game.textInputBuffer.length());
        }
    }
}
