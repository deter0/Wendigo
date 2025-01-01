import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import java.io.*;
import java.awt.image.*;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;

// Class for loading and managing SpriteSheet. See: https://en.wikipedia.org/wiki/Texture_atlas
class SpriteSheet {
    protected int tileSize;
    
    protected int numTilesX;
    protected int numTilesY;

    protected BufferedImage image;

    public SpriteSheet(String filePath, int tileSize) throws IOException {
        int imageWidth, imageHeight;

        this.image = ImageIO.read(new File(filePath));
        
        if (this.image == null) {
            System.err.println("[ERROR]: Error loading texture atlas: " + filePath);
            return;
        }

        this.tileSize = tileSize;
        imageWidth = this.image.getWidth();
        imageHeight = this.image.getHeight();

        this.numTilesX = imageWidth / tileSize;
        this.numTilesY = imageHeight / tileSize;

        if (imageWidth % tileSize != 0 || imageHeight % tileSize != 0) {
            System.err.println("[WARN]: Atlas tile size not equally divisible by it's width or height. Atlas: `" + filePath + "`");
        }

        System.err.println("[LOG]: Loaded sprite sheet: `" + filePath + "`");
    }
}

class Tile {
    public SpriteSheet textureSheet;
    public int textureIndex;

    public Tile(SpriteSheet sheet, int textureIndex) {
        this.textureSheet = sheet;
        this.textureIndex = textureIndex;
    }

    public void Draw(Graphics2D g, int x, int y, int w, int h) {
        int tileSize = this.textureSheet.tileSize;
        int sx = this.textureIndex % this.textureSheet.numTilesX;
        int sy = this.textureIndex / this.textureSheet.numTilesX;

        g.drawImage(this.textureSheet.image,
                    x, y, x+w, y+h,
                    sx*tileSize, sy*tileSize, (sx*tileSize)+tileSize, (sy*tileSize)+tileSize,
                    GG.COLOR_OPAQUE, null);
    }
}

class TileMapLayer {
    public String name = "Layer 1";
    protected TileMap parentMap;
    protected ArrayList<Tile> tiles;

    public TileMapLayer(TileMap map) {
        this.parentMap = map;
        this.tiles = new ArrayList<>();

        for (int i = 0; i < map.width*map.height; i++) {
            this.tiles.add(null); // Add all blank tiles
        }
    }

    public void SetTile(int x, int y, SpriteSheet sheet, int textureIndex) {
        int tileIndex = y * this.parentMap.width + x;

        Tile t = this.tiles.get(tileIndex); // The tile we are modifying;

        if (t == null) {
            t = new Tile(sheet, textureIndex);
            this.tiles.set(tileIndex, t);
        }

        t.textureSheet = sheet; // Modify it
        t.textureIndex = textureIndex;
    }
}

class TileMap {
    public double scale = 40.f;

    protected int width;
    protected int height;

    protected ArrayList<SpriteSheet> ownedSheets = new ArrayList<>();
    protected ArrayList<TileMapLayer> layers = new ArrayList<>();

    public TileMap(int width, int height) {
        this.width = width;
        this.height = height;

        this.layers.add(new TileMapLayer(this));
    }

    public SpriteSheet LoadSpriteSheet(String filePath, int tileSize) {
        SpriteSheet sheet;
        try {
            sheet = new SpriteSheet(filePath, tileSize);
        } catch (IOException e) {
            System.err.println("[ERROR]: Error loading spritesheet from file (" + filePath + ")\n" + e.getLocalizedMessage());
            return null;
        }

        this.ownedSheets.add(sheet);

        return sheet;
    }

    static Color DEBUG_GRID_COLOR = new Color(0x4b0b61);
    public void Draw(Graphics2D g) {
        int drewCount = 0;
        for (int y = 0; y < this.height; y++) {
            double realY = y * scale;
            if (realY > Game.HEIGHT) break; // Off bounds vertically stop rendering
            if ((realY+scale) < 0) continue; // Not in bounds yet, continue till we are

            for (int x = 0; x < this.width; x++) {
                int index = y * this.width + x;
                double realX = x * scale;

                if (realX > Game.WIDTH) break; // Out of bounds horizontally stop rendering
                if ((realX+scale) < 0) continue; // Not in bounds yet, continue until

                drewCount++;
                g.setColor(DEBUG_GRID_COLOR);
                GG.drawRect(x * scale, y * scale, scale, scale);

                for (int i = 0; i < this.layers.size(); i++) {
                    TileMapLayer layer = this.layers.get(i);
                    Tile t = layer.tiles.get(index);
                    if (t != null) {
                        t.Draw(g, (int)realX, (int)realY, (int)scale, (int)scale);
                    }
                }
            }
        }

        // System.out.println("Drew " + drewCount);
    }
}

class TileMapEditor {
    public int height = 340;
    public TileMap map;

    static Font ED_FONT;
    static int ED_FONT_SIZE = 14;

    private TileMapLayer currentLayer;
    private JFileChooser fileChooser;

    private int layerCount = 1;

    public TileMapEditor(TileMap m) {
        this.map = m;

        this.currentLayer = m.layers.get(0);

        LoadInterfaceFont();
    }

    private static void LoadInterfaceFont() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            Font f = Font.createFont(Font.TRUETYPE_FONT, new File("res/fonts/RobotoMono-Regular.ttf"));
            ge.registerFont(f);
            TileMapEditor.ED_FONT =  new Font(f.getFontName(), Font.PLAIN, ED_FONT_SIZE);
        } catch (FontFormatException | IOException e) {
            System.err.println("[ERROR]: Error loading editor font from file! Your editor may be borked.");
            TileMapEditor.ED_FONT = new Font("Courier New", Font.PLAIN, ED_FONT_SIZE);
        }
    }

    public void Update(double dt) {
        if (Game.IsKeyPressed(KeyEvent.VK_UP)) {
            ED_FONT_SIZE += 2;
            LoadInterfaceFont();
        } else if (Game.IsKeyPressed(KeyEvent.VK_DOWN)) {
            ED_FONT_SIZE -= 2;
            LoadInterfaceFont();
        }
    }

    static Color BUTTON_HOVERING_COLOR = new Color(0xc4c4c4);
    private int scrollLeftPanel = 0;

    public void Draw(Graphics2D g) throws NoninvertibleTransformException {
        AffineTransform previous =  g.getTransform();

        g.translate(0, Game.WINDOW_HEIGHT - this.height);

        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, Game.WIDTH, this.height);

        int spacing = 6;
        int layersPanelWidth = (int)(Game.WIDTH * 0.1 + 100.0);

        int containerWidth = layersPanelWidth;
        int containerHeight = this.height - 2*spacing;
        Rectangle container = new Rectangle(0, 0, containerWidth, containerHeight);
        
        g.translate(spacing, spacing);
        AffineTransform prevTrans = g.getTransform();
        
        g.setColor(Color.GRAY);
        g.drawRect(container.x, container.y, container.width, container.height); // Left Panel
        container.height -= spacing; // Introduce padding
        g.setClip(container.x, container.y, container.width, container.height); // Clip to left panel

        scrollLeftPanel += Game.deltaScroll;
        if (scrollLeftPanel < 0) {
            scrollLeftPanel = 0;
        }

        double lpScroll = -scrollLeftPanel*ED_FONT_SIZE*0.3;
        g.translate(0, lpScroll);
        g.translate(spacing/2, spacing/2); // Padding

        // Update mouse
        double[] transformedMouse = new double[2];
        g.getTransform().inverseTransform(new double[]{Game.mousePos.x, Game.mousePos.y}, 0, transformedMouse, 0, 1);
       
        containerWidth -= spacing; // Padding

        g.setFont(ED_FONT);
        g.setColor(Color.GRAY);
        g.drawLine(0, ED_FONT_SIZE, containerWidth, ED_FONT_SIZE);
        g.setColor(Color.WHITE);
        g.drawString("Layers", 0, ED_FONT_SIZE);

        Rectangle newLayerButton = new Rectangle(containerWidth - (ED_FONT_SIZE + 5), 0, ED_FONT_SIZE, ED_FONT_SIZE);
        boolean hoveringNLB = newLayerButton.contains(transformedMouse[0], transformedMouse[1]);

        g.setColor(hoveringNLB ? BUTTON_HOVERING_COLOR : Color.GRAY);
        g.fillRect(newLayerButton.x, newLayerButton.y, newLayerButton.width, newLayerButton.height);
        g.setColor(Color.WHITE);
        g.drawString("+", newLayerButton.x + ED_FONT_SIZE/4, newLayerButton.y + ED_FONT_SIZE - ED_FONT_SIZE/5);

        if (hoveringNLB && Game.IsMousePressed(MouseEvent.BUTTON1)) {
            TileMapLayer newLayer = new TileMapLayer(map);
            newLayer.name = "Layer " + (++this.layerCount);
            this.currentLayer = newLayer;
            this.map.layers.add(newLayer);
        }

        // Delete Button
        newLayerButton.x -= 5 + ED_FONT_SIZE;
        hoveringNLB = newLayerButton.contains(transformedMouse[0], transformedMouse[1]);

        g.setColor(hoveringNLB ? BUTTON_HOVERING_COLOR : Color.GRAY);
        g.fillRect(newLayerButton.x, newLayerButton.y, newLayerButton.width, newLayerButton.height);
        g.setColor(Color.WHITE);
        g.drawString("x", newLayerButton.x + ED_FONT_SIZE/4, newLayerButton.y + ED_FONT_SIZE - ED_FONT_SIZE/5);
        if (hoveringNLB && Game.IsMousePressed(MouseEvent.BUTTON1)) {
            this.map.layers.remove(this.currentLayer);
            this.currentLayer = this.map.layers.get(this.map.layers.size()-1);
        }

        for (int i = 0; i < map.layers.size(); i++) {
            TileMapLayer l = map.layers.get(i);

            Rectangle layerNameButton = new Rectangle(0, 4 + ED_FONT_SIZE + i*ED_FONT_SIZE, containerWidth, ED_FONT_SIZE);
            boolean hoveringLNB = layerNameButton.contains(transformedMouse[0], transformedMouse[1]);

            if (l == this.currentLayer) {
                g.setColor(Color.GRAY);
                g.fillRect(layerNameButton.x, layerNameButton.y, layerNameButton.width, layerNameButton.height);
            }

            if (hoveringLNB) {
                g.setColor(BUTTON_HOVERING_COLOR);
                g.fillRect(layerNameButton.x, layerNameButton.y, layerNameButton.width, layerNameButton.height);
            }

            if (hoveringLNB && Game.IsMousePressed(MouseEvent.BUTTON1)) {
                currentLayer = l;
            }
            if (hoveringLNB && Game.IsKeyDown(KeyEvent.VK_DELETE)) {
                map.layers.remove(l);
            }

            g.setColor(Color.WHITE);
            g.drawString(l.name, layerNameButton.x, layerNameButton.y+ED_FONT_SIZE);
        }

        g.setTransform(prevTrans);
        g.translate(container.width + spacing, 0);
        container.height += spacing;

        g.setColor(Color.GRAY);
        g.setClip(0, 0, Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT);
        g.drawRect(container.x, container.y, container.width, container.height); // Left Panel
        container.height -= spacing; // Introduce padding
        g.setClip(container.x, container.y, container.width, container.height); // Clip to left panel

        g.translate(spacing, spacing);

        // Update mouse
        g.getTransform().inverseTransform(new double[]{Game.mousePos.x, Game.mousePos.y}, 0, transformedMouse, 0, 1);

        g.setColor(Color.WHITE);
        g.drawString("Sprites", 0, ED_FONT_SIZE);

        Rectangle importSSB = new Rectangle(container.width - ED_FONT_SIZE - 15, 0, ED_FONT_SIZE, ED_FONT_SIZE);
        boolean hoveringSSB = importSSB.contains(transformedMouse[0], transformedMouse[1]);

        g.setColor(hoveringSSB ? BUTTON_HOVERING_COLOR : Color.GRAY);
        g.fillRect(importSSB.x, importSSB.y, importSSB.width, importSSB.height + 4);
        g.setColor(Color.WHITE);
        g.drawString("+", importSSB.x + ED_FONT_SIZE/4, importSSB.y + ED_FONT_SIZE);

        if (hoveringSSB && Game.IsMousePressed(MouseEvent.BUTTON1)) {
            // Open the save dialog
            if (fileChooser == null) {
                fileChooser = new JFileChooser("./res");
                fileChooser.setVisible(true);
                int r = fileChooser.showSaveDialog(null);

                if (r == JFileChooser.APPROVE_OPTION) {
                    File f = fileChooser.getSelectedFile();
                    System.out.println(f.getPath());
                }

                fileChooser = null;
            }
        }

        g.setTransform(previous);
    }
}