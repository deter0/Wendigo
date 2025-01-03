import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
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

class Panel {
    static Color BUTTON_BG = new Color(0x404040);
    static Color BUTTON_HOV_BG = new Color(0x666666);
    static Color BUTTON_DOWN_BG = new Color(0x252525);
    static Color BUTTON_BORDER = new Color(0x202020);
    static Color BUTTON_HILI_BG = new Color(0x3b8c4d);
    static double LINE_HEIGHT = 40;
    static double PADDING = 6.0;
    
    private Vector2 initialPosition = null;
    private Vector2 initialSize = null;

    public Vector2 position = null;
    public Vector2 size = null;

    private Graphics2D g;

    public static String context = null;
    public boolean open = true;

    public boolean nextButtonInactive = false;
    public boolean nextButtonHighlight = false;

    private static HashMap<String, Double> scrolls = new HashMap<>();

    private boolean isResizing = false;
    private Vector2 resizingStartMousePos;
    private Vector2 resizingStartPrevSize;
    private Vector2 resizingStartPrevPos;
    private Vector2 resizingRestriction = new Vector2(1.0);
    private boolean flipResizingAnchor = true;

    private boolean isMoving = false;
    private Vector2 movementPrevPos;
    private Vector2 movementStartMousePos;

    public Panel() {
    }

    public void End() {
        this.position = this.initialPosition.scale(1.0);
        this.size = this.initialSize.scale(1.0);
        g.setClip(0, 0, Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT);
    }

    public void Begin(Graphics2D g, Vector2 position, Vector2 size) {
        this.g = g;

        if (this.size == null) {
            this.size = size;
            this.initialSize = size.scale(1.0);
        }
        if (this.position == null) {
            this.position = position;
            this.initialPosition = position.scale(1.0);
        }

        g.setColor(new Color(0x2e2e2e));
        GG.fillRect(this.position, this.size); // Fill panel background
        g.setColor(new Color(0x777777));
        GG.drawRect(this.position.sub(new Vector2(1, 1)), this.size.add(new Vector2(2, 2))); // Border

        double grabbingRadius = 10.0;


        boolean grabbingTop = Vector2.AABBContainsPoint(initialPosition.add(new Vector2(0, -grabbingRadius)),
                                                         new Vector2(initialSize.x, grabbingRadius),
                                                         Game.mousePos);
        boolean grabbingBottom = Vector2.AABBContainsPoint(initialPosition.add(new Vector2(0, this.initialSize.y)),
                                                            new Vector2(initialSize.x, grabbingRadius),
                                                            Game.mousePos);

        boolean grabbingLeft = Vector2.AABBContainsPoint(initialPosition.add(new Vector2(-grabbingRadius, 0)),
                                                          new Vector2(grabbingRadius, initialSize.y),
                                                          Game.mousePos);
        boolean grabbingRight = Vector2.AABBContainsPoint(initialPosition.add(new Vector2(initialSize.x, 0)),
                                                          new Vector2(grabbingRadius, initialSize.y),
                                                          Game.mousePos);


        Vector2 scalingDifference = new Vector2();

        if (Game.IsMousePressed(MouseEvent.BUTTON1)) {
            if (!this.isResizing && grabbingTop || grabbingBottom || grabbingRight || grabbingLeft) {
                if (this.resizingStartMousePos == null) {
                    this.resizingStartMousePos = Game.mousePos.scale(1.0);
                    this.resizingStartPrevSize = this.initialSize.scale(1.0);
                    this.resizingStartPrevPos = this.initialPosition.scale(1.0);

                    if (grabbingTop || grabbingBottom) {
                        this.resizingRestriction = new Vector2(0.0, 1.0);
                    } else if (grabbingRight || grabbingLeft) {
                        this.resizingRestriction = new Vector2(1.0, 0.0);
                    } else {
                        this.resizingRestriction = new Vector2(1.0);
                    }

                    if (grabbingTop || grabbingLeft) {
                        this.flipResizingAnchor = false;
                    } else {
                        this.flipResizingAnchor = true;
                    }

                    Vector2[] corners = {
                        this.initialPosition,
                        this.initialPosition.add(new Vector2(this.initialSize.x, 0.0)),
                        this.initialPosition.add(new Vector2(0, this.initialSize.y)),
                        this.initialPosition.add(this.initialSize),
                    };
                    for (Vector2 corner : corners) {
                        if (Game.mousePos.distance(corner) < 2.0*grabbingRadius) {
                            this.resizingRestriction = new Vector2(1.0);
                        }
                    }
                }
                this.isResizing = true;
            }
        }
        if (Game.IsMouseReleased(MouseEvent.BUTTON1)) {
            this.resizingStartMousePos = null;
            this.resizingStartPrevSize = null;
            this.isResizing = false;
        }

        if (this.isResizing) {
            scalingDifference = this.resizingStartMousePos.sub(Game.mousePos).mult(this.resizingRestriction);
        }
        
        int cursor = -1;
        if (grabbingTop) {
            cursor = Cursor.N_RESIZE_CURSOR;
        } else if (grabbingRight) {
            cursor = Cursor.E_RESIZE_CURSOR;
        } else if (grabbingLeft) {
            cursor = Cursor.W_RESIZE_CURSOR;
        } else if (grabbingBottom) {
            cursor = Cursor.S_RESIZE_CURSOR;
        }
        if (cursor != -1) {
            Game.currentCursor = Cursor.getPredefinedCursor(cursor);
        }
        
        if (this.isResizing) {
            if (this.flipResizingAnchor) {
                // Right and down
                this.initialSize = this.resizingStartPrevSize.sub(scalingDifference);
            } else {
                // Left and up
                this.initialPosition = this.resizingStartPrevPos.sub(scalingDifference);
                this.initialSize = this.resizingStartPrevSize.sub(this.initialPosition.sub(this.resizingStartPrevPos));
            }
        }

        g.setClip((int)this.initialPosition.x, (int)this.initialPosition.y,
                    (int)this.initialSize.x, (int)this.initialSize.y);
    }

    public void Name(String name) {
        if (context != null) {
            name = context + " - " + name;
        }

        Vector2 labelDims = this.CenteredLabel(name, new Vector2(), new Vector2(this.size.x, 4+TileMapEditor.ED_FONT_SIZE));
        double y = this.position.y + 8+labelDims.y;
        
        g.setColor(new Color(0x777777));
        GG.drawLine(this.position.x, y, this.position.x + this.size.x, y);


        boolean mouseInHeader = Vector2.AABBContainsPoint(this.position, labelDims, Game.mousePos);
        if (Game.IsMousePressed(MouseEvent.BUTTON1)) {
            if (!this.isResizing && mouseInHeader) {
                this.isMoving = true;
                this.movementPrevPos = this.initialPosition.scale(1.0);
                this.movementStartMousePos = Game.mousePos.scale(1.0);
            }
        }
        if (this.isResizing || Game.IsMouseReleased(MouseEvent.BUTTON1)) {
            this.isMoving = false;
        }

        if (this.isMoving) {
            Game.currentCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
            this.initialPosition = this.movementPrevPos.add(Game.mousePos.sub(this.movementStartMousePos));
        }

        this.position.y += 8+labelDims.y;
    }

    private Vector2 currentHeaderPos;
    private Vector2 currentHeaderSize;
    private Vector2 currentHeaderPrevPos;
    private Vector2 currentHeaderRCursor;

    public Vector2 HeaderBegin(String headerName) {
        Vector2 position = this.position;
        Vector2 size = new Vector2(this.size.x, LINE_HEIGHT);

        g.setColor(BUTTON_BG);
        GG.drawLine(position.x + PADDING, position.y+size.y,
                    position.x + size.x - 2*PADDING, position.y+size.y);

        this.CenteredYLabel(headerName, new Vector2(PADDING, 0), new Vector2(0, LINE_HEIGHT));

        this.currentHeaderPos = position;
        this.currentHeaderSize = size;
        this.currentHeaderPrevPos = this.position.scale(1.0);
        this.currentHeaderRCursor = new Vector2(this.size.x - PADDING, 0);

        return size;
    }

    public boolean HeaderButton(String text) {
        double buttonHeight = TileMapEditor.ED_FONT_SIZE+PADDING;

        boolean state = this.ButtonFromTopRight(text,
                                                this.currentHeaderRCursor.add(new Vector2(0, LINE_HEIGHT/2.0 - buttonHeight/2.0)),
                                                new Vector2(0, buttonHeight));

        this.currentHeaderRCursor.x -= this.lastButtonSize.x+PADDING;

        return state;
    }

    public void HeaderEnd() {
        this.position = this.currentHeaderPrevPos.add(new Vector2(0.0, this.currentHeaderSize.y + PADDING));
    }

    public Vector2 CenteredLabel(String text, Vector2 position, Vector2 size) {
        position = position.add(this.position);
        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        int textWidth = m.stringWidth(text);

        g.setColor(Color.WHITE);
        g.drawString(text, (int)(position.x + size.x / 2 - textWidth / 2),
                     (int)(position.y + size.y/2.0 + m.getHeight()/2.0));
        
        return size;
    }

    public void LayoutVertBAdded(double spacing) {
        Vector2 buttonSize = this.lastButtonSize;
        this.position.y += buttonSize.y+spacing;
    }

    private Vector2 currentListPrevPosition;
    private Vector2 currentListPrevSize;
    private Vector2 currentListTopLeft;
    private String currentListRandomName;
    private Shape currentListPrevClip;
    private AffineTransform currentListPrevTransform;

    public Vector2 ListBegin(String name, Vector2 size) {
        this.currentListRandomName = name;
        this.currentListPrevPosition = this.position.scale(1.0); 
        this.currentListPrevSize = this.size.scale(1.0);
        this.currentListPrevClip = g.getClip();

        Vector2 position = this.position.add(new Vector2(PADDING, 0));
        this.currentListTopLeft = position.scale(1.0);

        if (size.x == 0) {
            size.x = this.size.x - 2*PADDING; // Expand to full width
        }
        if (size.y <= 0) {
            size.y = this.size.y-(this.position.y - this.initialPosition.y) - PADDING + size.y; // Remaning space
        }

        g.setColor(BUTTON_BORDER);
        GG.drawRect(position, size);

        g.setClip((int)position.x, (int)position.y, (int)size.x, (int)size.y);

        Double scrollPixels = Panel.scrolls.get(this.currentListRandomName);
        if (scrollPixels == null) {
            scrollPixels = 0.0;
        }
        this.currentListPrevTransform = g.getTransform();
        // g.translate(0, -scrollPixels);
        this.position = position;
        this.position.y -= scrollPixels;

        this.size = size;
        
        return size;
    }
    
    public void ListEnd() {
        g.setTransform(this.currentListPrevTransform);

        this.currentListPrevPosition.y += this.size.y + PADDING;
        
        Double scrollPixels = Panel.scrolls.get(this.currentListRandomName);
        if (scrollPixels == null) {
            scrollPixels = 0.0;
        }

        double contentBottomCoord = (this.position.y-this.currentListTopLeft.y);

        if (contentBottomCoord != 0 && contentBottomCoord > this.size.y) {
            double scrollBarWidth = 10.0;
            Vector2 scrollBarPos = this.currentListTopLeft.add(new Vector2(this.size.x - scrollBarWidth, 0));
            Vector2 scrollBarSize = new Vector2(scrollBarWidth, this.size.y);

            double percentContentVisible = this.size.y / contentBottomCoord;
            double scrollBarButtonSizeY = this.size.y * percentContentVisible;

            if (Vector2.AABBContainsPoint(this.currentListTopLeft, this.size, Game.mousePos)) {
                scrollPixels += Game.deltaScroll;
            }
            double minScroll = 0.0, maxScroll = contentBottomCoord - this.size.y;
            if (scrollPixels < minScroll) {
                scrollPixels = minScroll;
            } else if (scrollPixels > maxScroll) {
                scrollPixels = maxScroll;
            }

            double percentScroll = scrollPixels/maxScroll;

            double scrollBarButtonOffset = percentScroll * (this.size.y-scrollBarButtonSizeY);
            Vector2 scrollBarButtonPosition = scrollBarPos.add(new Vector2(0, scrollBarButtonOffset));
            Vector2 scrollBarButtonSize = new Vector2(scrollBarWidth, scrollBarButtonSizeY);

            g.setColor(BUTTON_DOWN_BG);
            GG.fillRect(scrollBarPos.sub(new Vector2(1, 1)), scrollBarSize.add(new Vector2(2, 2)));
            g.setColor(BUTTON_BG);
            GG.drawRect(scrollBarPos, scrollBarSize);

            g.setColor(BUTTON_BG);
            GG.fillRect(scrollBarButtonPosition, scrollBarButtonSize);
        } else {
            scrollPixels = 0.0;
        }
        Panel.scrolls.put(this.currentListRandomName, scrollPixels);

        this.position = this.currentListPrevPosition;
        this.size = this.currentListPrevSize;

        g.setClip(this.currentListPrevClip);
    }

    private double remainingVerticalSpace() {
        return this.size.y - (this.position.y-this.initialPosition.y);
    }

    public Vector2 CenteredYLabel(String text, Vector2 position, Vector2 size) {
        position = position.add(this.position);
        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        g.setColor(Color.WHITE);

        int baselineY = (int) (position.y + (size.y - (m.getAscent() + m.getDescent())) / 2 + m.getAscent());

        g.drawString(text, (int)(position.x), baselineY);
        
        return size;
    }

    private Vector2 lastButtonSize;
    public boolean Button(String text, Vector2 position, Vector2 size) {
        if (position.x <= 1.0) {
            position.x = position.x * this.size.x;
        }
        if (position.y <= 1.0) {
            position.y = position.y * this.remainingVerticalSpace();
        }

        position = position.add(this.position);

        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        int textWidth = m.stringWidth(text);
        
        double compSizeX = textWidth + 1.5*PADDING, compSizeY = m.getHeight() + PADDING;
        if (size == null)
            size = new Vector2(compSizeX, compSizeY);

        if (size.x == 0.0)
            size.x = compSizeX;
        if (size.y == 0.0)
            size.y = compSizeY;
            
        if (size.x <= 1.0)
            size.x = this.size.x * size.x;
        if (size.y <= 1.0) {
            size.y = size.y * this.remainingVerticalSpace();
        }

        boolean inactive = this.nextButtonInactive;
        boolean highlight = this.nextButtonHighlight;
        boolean hovering = !inactive && Vector2.AABBContainsPoint(position, size, Game.mousePos);
        boolean clicked = Game.IsMousePressed(MouseEvent.BUTTON1);

        if (inactive) {
            g.setColor(BUTTON_BORDER);
        } else if (highlight) {
            g.setColor(BUTTON_HILI_BG);
        } else {
            g.setColor(hovering ? (clicked ? BUTTON_DOWN_BG : BUTTON_HOV_BG) : BUTTON_BG);
        }
        GG.fillRect(position, size);
        g.setColor(inactive ? BUTTON_DOWN_BG : BUTTON_BORDER);
        GG.drawRect(position, size);

        // Calculate the baseline Y-coordinate for vertically centered text
        int baselineY = (int) (position.y + (size.y - (m.getAscent() + m.getDescent())) / 2 + m.getAscent());

        g.setColor(inactive ? Color.GRAY : Color.WHITE);
        g.drawString(text, (int)(position.x + size.x / 2 - textWidth / 2), baselineY);
        
        this.lastButtonSize = size;
        this.nextButtonInactive = false;
        this.nextButtonHighlight = false;

        return hovering && clicked;
    }
    public boolean ButtonFromTopRight(String text, Vector2 position, Vector2 size) {
        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        
        int textWidth = m.stringWidth(text);
        if (size == null)
            size = new Vector2(textWidth + 1.5*PADDING, m.getHeight() + PADDING);
        if (size.x == 0.0)
            size.x = textWidth + 1.5*PADDING;

        return this.Button(text, position.sub(new Vector2(size.x, 0)), size);
    }
}

class TileMapEditor {
    public int height = 340;
    public TileMap map;

    static Font ED_FONT;
    static int ED_FONT_SIZE = 14;

    private TileMapLayer currentLayer;
    private JFileChooser fileChooser;

    private String spriteSheetToLoad = null;
    private BufferedImage spriteSheetToLoadImage = null;
    private String spriteSheetToLoadError = null;
    private Vector2 spriteSheetLoaderScroll = new Vector2();
    private double spriteSheetLoaderZoom = 3.f;
    private Vector2 spriteSheetLoaderMovementStart = null;
    private Vector2 spriteSheetLoaderInitialScroll = null;
    private int spriteSheetLoaderTileSize = 16;

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

        if (Game.IsKeyPressed(KeyEvent.VK_LEFT)) {
            Panel.LINE_HEIGHT -= 2;
        }
        if (Game.IsKeyPressed(KeyEvent.VK_RIGHT)) {
            Panel.LINE_HEIGHT += 2;
        }
    }
    
    static Color BUTTON_HOVERING_COLOR = new Color(0xc4c4c4);
    private int scrollLeftPanel = 0;
    
    Panel sheetsPanel = new Panel();
    Panel layers = new Panel();
    public void Draw(Graphics2D g) throws NoninvertibleTransformException {
        Panel.context = "Editor";

        layers.Begin(g, new Vector2(400, 40), new Vector2(300, 300));
        
        layers.Name("Layers");
        
        layers.HeaderBegin("Layers");
        if (layers.HeaderButton("New")) {
            TileMapLayer newLayer = new TileMapLayer(map);
            
            newLayer.name = "Layer " + (++this.layerCount);
            this.currentLayer = newLayer;

            this.map.layers.add(newLayer);
        }
        
        layers.nextButtonInactive = (currentLayer == null);
        if (layers.HeaderButton("Delete")) {
            this.map.layers.remove(this.currentLayer);
            if (this.map.layers.size() == 0) {
                this.currentLayer = null;
            } else {
                this.currentLayer = this.map.layers.get(this.map.layers.size()-1);
            }
        }
        layers.nextButtonInactive = (currentLayer == null);
        if (layers.HeaderButton("Rename")) {
            // TODO: Rename layers
        }
        layers.HeaderEnd();
        
        layers.ListBegin("Layers", new Vector2(0, -40.0));   
            for (TileMapLayer layer : this.map.layers) {
                if (layer == currentLayer) {
                    layers.nextButtonHighlight = true;
                }
                if (layers.Button(layer.name, new Vector2(), new Vector2(1.0, 0.0))) {
                    currentLayer = layer;
                }
                layers.LayoutVertBAdded(0.0);
            }
        layers.ListEnd();

        int currentIndex = this.map.layers.indexOf(this.currentLayer);
        
        layers.nextButtonInactive = (currentIndex == this.map.layers.size()-1);
        if (layers.Button("Down", new Vector2(0.0, 0), new Vector2(0.5, 1.0))) {
            this.map.layers.remove(this.currentLayer);
            map.layers.add(currentIndex + 1, this.currentLayer);
        }
        
        layers.nextButtonInactive = (currentIndex == 0);
        if (layers.Button("Up", new Vector2(0.5, 0), new Vector2(0.5, 1.0))) {
            this.map.layers.remove(this.currentLayer);
            map.layers.add(currentIndex - 1, this.currentLayer);
        }
        layers.End();
            
            
        sheetsPanel.Begin(g, new Vector2(100, 40), new Vector2(300, 300));
        sheetsPanel.Name("Sprite Sheets");
            
        sheetsPanel.HeaderBegin("Sheets");
            if (sheetsPanel.HeaderButton("Add New")) {
                // Button was pressed
            }
        sheetsPanel.HeaderEnd();

        sheetsPanel.End();

        if (true) {
            return;
        }

        AffineTransform previous =  g.getTransform();
        if (spriteSheetToLoad != null) {
            g.setColor(new Color(0, 0, 0, 155));
            g.fillRect(0, 0, Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT);

            g.translate(80, 80);
            Rectangle container = new Rectangle(0, 0, Game.WINDOW_WIDTH-2*80, Game.WINDOW_HEIGHT-2*80);
            g.setColor(Color.DARK_GRAY);
            g.fillRect(container.x, container.y, container.width, container.height);
            
            g.setColor(Color.WHITE);
            g.setFont(ED_FONT);
            g.drawString("Spritesheet: `" + this.spriteSheetToLoad + "`", 0, ED_FONT_SIZE);
            g.translate(0, ED_FONT_SIZE);

            if (spriteSheetToLoadError != null) {
                g.setColor(Color.RED);
                g.drawString("! " + spriteSheetToLoadError, 0, ED_FONT_SIZE);
                g.setTransform(previous);
                return;
            }
            if (spriteSheetToLoadImage == null) {
                try {
                    this.spriteSheetToLoadImage = ImageIO.read(new File(this.spriteSheetToLoad));
                } catch (IOException e) {
                    this.spriteSheetToLoadError = "Error loading spritesheet! IOException: " + e.getLocalizedMessage();
                }
                if (this.spriteSheetToLoadImage == null) {
                    this.spriteSheetToLoadError = "Error loading spritesheet!";
                }
            } else {
                container.height -= ED_FONT_SIZE;

                g.setClip(0, 0, container.width, container.height);

                g.translate(container.width/2.0, container.height/2.0);
                g.translate(this.spriteSheetLoaderScroll.x, this.spriteSheetLoaderScroll.y);
                g.scale(this.spriteSheetLoaderZoom, this.spriteSheetLoaderZoom);

                double[] transformedMouse = new double[2];
                g.getTransform().inverseTransform(new double[]{Game.mousePos.x, Game.mousePos.y}, 0, transformedMouse, 0, 1);
                
                this.spriteSheetLoaderZoom -= Game.deltaScroll * 0.1;
                if (Game.IsMouseDown(MouseEvent.BUTTON2)) {
                    if (this.spriteSheetLoaderMovementStart == null) {
                        this.spriteSheetLoaderMovementStart = Game.mousePos.scale(1.0);
                        this.spriteSheetLoaderInitialScroll = this.spriteSheetLoaderScroll.scale(1.0);
                    }
                    Vector2 delta = this.spriteSheetLoaderMovementStart.sub(Game.mousePos);
                    this.spriteSheetLoaderScroll = this.spriteSheetLoaderInitialScroll.sub(delta);
                } else {
                    this.spriteSheetLoaderMovementStart = null;
                    this.spriteSheetLoaderInitialScroll = null;
                }

                int ix = -this.spriteSheetToLoadImage.getWidth()/2, iy = -this.spriteSheetToLoadImage.getHeight()/2;
                g.drawImage(this.spriteSheetToLoadImage, ix, iy, null);

                int tileSize = this.spriteSheetLoaderTileSize;
                int numTilesY = this.spriteSheetToLoadImage.getHeight()/tileSize;
                int numTilesX = this.spriteSheetToLoadImage.getWidth()/tileSize;
                for (int y = 0; y < numTilesY; y++) {
                    for (int x = 0; x < numTilesX; x++) {
                        g.setColor(new Color(255, 0, 0, 10));
                        Rectangle rect = new Rectangle(ix+tileSize*x, iy+tileSize*y, tileSize, tileSize);
                        if (rect.contains(transformedMouse[0], transformedMouse[1])) {
                            g.setColor(new Color(0, 255, 0, 100));
                        }
                        g.drawRect(rect.x, rect.y, rect.width, rect.height);
                    }
                }
            }

            g.setTransform(previous);
        } else {
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
                        this.spriteSheetToLoad = f.getPath();
                    }
    
                    fileChooser = null;
                }
            }
    
            g.setTransform(previous);
        }
    }
}