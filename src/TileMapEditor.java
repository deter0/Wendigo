import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.*;
import java.awt.image.*;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;

// Class for loading and managing SpriteSheet. See: https://en.wikipedia.org/wiki/Texture_atlas
class SpriteSheet {
    public String name = "null";
    protected int tileSize;
    
    protected int numTilesX;
    protected int numTilesY;

    protected boolean tilesPurged = false;

    protected BufferedImage image;
    protected VolatileImage GPUImage;

    ArrayList<Tile> tiles;

    public BufferedImage GetCPUImage() {
        return this.image;
    }

    public Image GetImage(Graphics2D g) {
        if (this.VolatileImageNeedsCreation(g)) {
            // System.out.println("[LOG]: Recreating volatile image.");
            // this.CreateVolatileImage();
        }
        
        if (this.GPUImage != null) {
            return this.GPUImage;
        }

        return (Image)this.image;
    }

    public void CreateVolatileImage() {
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                    .getDefaultScreenDevice()
                                                    .getDefaultConfiguration();
    
        // Create a VolatileImage with the same dimensions as the source image
        VolatileImage vImage = gc.createCompatibleVolatileImage(this.image.getWidth(), this.image.getHeight(), Transparency.TRANSLUCENT);
    
        if (vImage == null) {
            System.err.println("[ERROR]: Error creating volatile image (GPU Image) for sprite sheet: `" + this.name + "`. Expect performance degradations.");
            return;
        }
    
        System.out.println("[LOG]: Created volatile image (GPU Image) for sprite sheet: `" + this.name + "` " + this.image.getWidth() + "x" + this.image.getHeight());
    
        // Obtain a Graphics2D context for the VolatileImage
        Graphics2D vg = (Graphics2D) vImage.createGraphics();
    
        // Use SrcOver composite to blend the images properly
        vg.setComposite(AlphaComposite.SrcOver); 
        
        // Optional: You can clear the image with a transparent color (or black if you prefer)
        vg.setColor(Color.black); // If you want a black background, use this line.
        vg.fillRect(0, 0, vImage.getWidth(), vImage.getHeight());
    
        // Draw the original image onto the VolatileImage
        vg.drawImage(this.image, 0, 0, null);
    
        vg.dispose();
    
        // Store the resulting VolatileImage

        // this.GPUImage = vImage;
        // this.image = vImage.getSnapshot();
    }    

    public boolean VolatileImageNeedsCreation(Graphics2D g) {
        if (this.GPUImage == null || this.GPUImage.contentsLost() || this.GPUImage.validate(g.getDeviceConfiguration()) == VolatileImage.IMAGE_INCOMPATIBLE) {
            return true;
        }

        return false;
    }

    private void Init(BufferedImage image, int tileSize) {
        this.tileSize = tileSize;
        this.image = image;

        int imageWidth = this.image.getWidth();
        int imageHeight = this.image.getHeight();

        this.numTilesX = imageWidth / tileSize;
        this.numTilesY = imageHeight / tileSize;

        this.UpdateTilesSize();
        // this.CreateVolatileImage();
    
        if (imageWidth % tileSize != 0 || imageHeight % tileSize != 0) {
            System.err.println("[WARN]: Atlas tile size not equally divisible by it's width or height.");
        }
    }

    public SpriteSheet(BufferedImage image, int tileSize) {
        Init(image, tileSize);   
    }

    public SpriteSheet(String filePath, int tileSize) throws IOException {
        File f = new File(filePath);
        BufferedImage loadedImage = ImageIO.read(f);
        this.name = f.getName();
        Init(loadedImage, tileSize);
    }

    public void PurgeBlankTiles() {
        int removalCount = 0;
        for (Tile t : this.tiles) {
            if (t != null && t.IsBlank()) {
                removalCount ++;
                this.tiles.set(this.tiles.indexOf(t), null);
            }
        }

        System.out.println("[LOG]: Purged blank " + removalCount + " tiles in `" + this.name + "` sprite sheet.");
        this.tilesPurged = true;
    }

    public void UpdateTilesSize() {
        int imageWidth = this.image.getWidth();
        int imageHeight = this.image.getHeight();

        this.numTilesX = imageWidth / tileSize;
        this.numTilesY = imageHeight / tileSize;

        System.err.println("[WARN]: Reset blueprint tiles.");
        this.tiles = new ArrayList<>();
        for (int y = 0; y < this.numTilesY; y++) {
            for (int x = 0; x < this.numTilesX; x++) {
                this.tiles.add(new Tile(x, y, this, y * this.numTilesX + x));
            }
        }
        this.tilesPurged = false;
    }
}

class Tile {
    public SpriteSheet textureSheet;
    public int textureIndex;

    protected int x;
    protected int y;
    public int w = 1;
    public int h = 1;

    public Tile(int x, int y, SpriteSheet sheet, int textureIndex) {
        this.x = x;
        this.y = y;
        this.textureSheet = sheet;
        this.textureIndex = textureIndex;
    }

    public boolean IsCompoundTile() {
        return this.w > 1 || this.h > 1;
    }

    public int GetSheetIndex() {
        return this.y * this.textureSheet.numTilesX + this.x;
    }

    public boolean IsBlank() {
        int tileSize = this.textureSheet.tileSize;
        int sx = this.textureIndex % this.textureSheet.numTilesX;
        int sy = this.textureIndex / this.textureSheet.numTilesX;
        BufferedImage image = this.textureSheet.GetCPUImage();
    
        if (image == null)
            return true;

        // Calculate the top-left corner of the tile in the texture sheet
        int startX = sx * tileSize;
        int startY = sy * tileSize;
    
        // Get the alpha raster of the image
        WritableRaster alphaRaster = image.getAlphaRaster();
        if (alphaRaster == null)
            return false;
    
        // Loop through each pixel in the tile
        for (int y = 0; y < tileSize; y++) {
            for (int x = 0; x < tileSize; x++) {
                // Get the alpha value of the pixel (0 = fully transparent, 255 = fully opaque)
                int alpha = alphaRaster.getSample(startX + x, startY + y, 0);
    
                // If any pixel is not fully transparent, the tile is not blank
                if (alpha > 0) {
                    return false;
                }
            }
        }
    
        // All pixels are fully transparent; the tile is blank
        return true;
    }

    public void Draw(Graphics2D g, double x, double y, double w, double h) {
        int tileSize = this.textureSheet.tileSize;
        int sx = this.textureIndex % this.textureSheet.numTilesX;
        int sy = this.textureIndex / this.textureSheet.numTilesX;

        g.drawImage(this.textureSheet.GetImage(g),
                    (int)x, (int)y, (int)(x+(w * this.w)), (int)(y+(h * this.h)),
                    sx*tileSize, sy*tileSize, (sx*tileSize)+(tileSize*this.w), (sy*tileSize)+(tileSize*this.h),
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
            t = new Tile(x, y, sheet, textureIndex);
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

    public void DeleteSheet(SpriteSheet sheet) {
        for (TileMapLayer layer : this.layers) {
            for (Tile t : layer.tiles) {
                if (t != null && t.textureSheet == sheet) {
                    layer.tiles.set(layer.tiles.indexOf(t), null);
                }
            }
        }
        this.ownedSheets.remove(sheet);
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
    static Color PANEL_BG = new Color(0x2e2e2e);
    static Color BUTTON_BG = new Color(0x404040);
    static Color BUTTON_HOV_BG = new Color(0x666666);
    static Color BUTTON_DOWN_BG = new Color(0x252525);
    static Color BUTTON_BORDER = new Color(0x202020);
    static Color BUTTON_HILI_BG = new Color(0x3b8c4d);
    static double PADDING = 6.0;
    static double LINE_HEIGHT = TileMapEditor.ED_FONT_SIZE + 2*PADDING;
    
    public Vector2 windowPosition = null;
    public Vector2 windowSize = null;

    public Vector2 position = null;
    public Vector2 size = null;

    private Graphics2D g;

    public static String context = null;
    public boolean open = true;

    public boolean nextButtonDisabled = false;
    public boolean nextButtonHighlight = false;
    public boolean nextButtonAbsPos = false;


    private static HashMap<String, Double> scrolls = new HashMap<>();
    private static HashMap<String, Double> scrollsTarget = new HashMap<>();

    private ArrayList<Shape> clipsStack = new ArrayList<>();

    private boolean isResizing = false;
    private Vector2 resizingStartMousePos;
    private Vector2 resizingStartPrevSize;
    private Vector2 resizingStartPrevPos;
    private Vector2 resizingRestriction = new Vector2(1.0);
    private boolean flipResizingAnchor = true;
    public boolean disabled = false;

    private boolean isMoving = false;
    private Vector2 movementPrevPos;
    private Vector2 movementStartMousePos;

    public Panel() {
    }

    public void End() {
        this.position = this.windowPosition.scale(1.0);
        this.size = this.windowSize.scale(1.0);

        if (this.disabled) {
            g.setColor(new Color(0, 0, 0, 200));
            GG.fillRect(this.windowPosition, this.windowSize);
        }
        
        Shape firstClip = this.clipsStack.size() > 0 ? this.clipsStack.get(0) : null;
        if (firstClip != null) {
            g.setClip(firstClip);
        } else {
            g.setClip(0, 0, Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT);
        }
        this.clipsStack.clear();
    }

    public void Begin(Graphics2D g, Vector2 position, Vector2 size) {
        this.g = g;

        if (this.size == null) {
            this.size = size;
            this.windowSize = size.scale(1.0);
        }
        if (this.position == null) {
            this.position = position;
            this.windowPosition = position.scale(1.0);
        }

        for(HashMap.Entry<String, Double> entry : Panel.scrollsTarget.entrySet()) {
            String key = entry.getKey();
            Double targetValue = entry.getValue();
            Double currentValue = Panel.scrolls.get(key);

            if (targetValue == null) continue;
            if (currentValue == null) currentValue = 0.0;

            Panel.scrolls.put(key, Vector2.lerpFRI(currentValue, targetValue, 0.995, Game.deltaTime));
        }

        g.setColor(PANEL_BG);
        GG.fillRect(this.position, this.size); // Fill panel background
        g.setColor(this.disabled ? BUTTON_DOWN_BG : new Color(0x777777));
        GG.drawRect(this.position.sub(new Vector2(1, 1)), this.size.add(new Vector2(2, 2))); // Border

        double grabbingRadius = 10.0;

        boolean grabbingTop = Vector2.AABBContainsPoint(windowPosition.add(new Vector2(0, -grabbingRadius)),
                                                         new Vector2(windowSize.x, grabbingRadius),
                                                         Game.mousePos);
        boolean grabbingBottom = Vector2.AABBContainsPoint(windowPosition.add(new Vector2(0, this.windowSize.y)),
                                                            new Vector2(windowSize.x, grabbingRadius),
                                                            Game.mousePos);

        boolean grabbingLeft = Vector2.AABBContainsPoint(windowPosition.add(new Vector2(-grabbingRadius, 0)),
                                                          new Vector2(grabbingRadius, windowSize.y),
                                                          Game.mousePos);
        boolean grabbingRight = Vector2.AABBContainsPoint(windowPosition.add(new Vector2(windowSize.x, 0)),
                                                          new Vector2(grabbingRadius, windowSize.y),
                                                          Game.mousePos);

        Vector2 scalingDifference = new Vector2();

        if (!this.disabled && Game.IsMousePressed(MouseEvent.BUTTON1)) {
            if (!this.isResizing && grabbingTop || grabbingBottom || grabbingRight || grabbingLeft) {
                if (this.resizingStartMousePos == null) {
                    this.resizingStartMousePos = Game.mousePos.scale(1.0);
                    this.resizingStartPrevSize = this.windowSize.scale(1.0);
                    this.resizingStartPrevPos = this.windowPosition.scale(1.0);

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
                        this.windowPosition,
                        this.windowPosition.add(new Vector2(this.windowSize.x, 0.0)),
                        this.windowPosition.add(new Vector2(0, this.windowSize.y)),
                        this.windowPosition.add(this.windowSize),
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
        if (!this.disabled && Game.IsMouseReleased(MouseEvent.BUTTON1)) {
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
        if (!this.disabled && cursor != -1) {
            Game.currentCursor = Cursor.getPredefinedCursor(cursor);
        }
        
        if (this.isResizing) {
            if (this.flipResizingAnchor) {
                // Right and down
                this.windowSize = this.resizingStartPrevSize.sub(scalingDifference);
            } else {
                // Left and up
                this.windowPosition = this.resizingStartPrevPos.sub(scalingDifference);
                this.windowSize = this.resizingStartPrevSize.sub(this.windowPosition.sub(this.resizingStartPrevPos));
            }
        }

        this.clipsStack.add(g.getClip());
        g.setClip((int)this.windowPosition.x, (int)this.windowPosition.y,
                    (int)this.windowSize.x, (int)this.windowSize.y);
    }

    public void Name(String name) {
        if (context != null) {
            name = context + " - " + name;
        }

        Vector2 labelDims = this.CenteredLabel(name, new Vector2(0, PADDING), new Vector2(this.size.x, 4+TileMapEditor.ED_FONT_SIZE));
        double y = 2*PADDING + this.position.y + PADDING+labelDims.y;
        
        g.setColor(new Color(0x777777));
        GG.drawLine(this.position.x, y, this.position.x + this.size.x, y);

        boolean mouseInHeader = Vector2.AABBContainsPoint(this.position, labelDims, Game.mousePos);
        if (Game.IsMousePressed(MouseEvent.BUTTON1)) {
            if (!this.isResizing && mouseInHeader) {
                this.isMoving = true;
                this.movementPrevPos = this.windowPosition.scale(1.0);
                this.movementStartMousePos = Game.mousePos.scale(1.0);
            }
        }
        if (this.isResizing || Game.IsMouseReleased(MouseEvent.BUTTON1)) {
            this.isMoving = false;
        }

        if (!this.disabled && this.isMoving) {
            Game.currentCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
            this.windowPosition = this.movementPrevPos.add(Game.mousePos.sub(this.movementStartMousePos));
        }

        this.position.y = y+PADDING;
    }

    public boolean CloseButton() {
        final double buttonSize = 20.f;
        double x = (this.windowPosition.x + this.windowSize.x) - buttonSize - PADDING, y = this.windowPosition.y + PADDING;
        
        this.nextButtonAbsPos = true;
        return this.Button("x", new Vector2(x, y), new Vector2(buttonSize));
    }

    private Vector2 currentEntryPos;
    private Vector2 currentEntrySize;
    private Vector2 currentEntryPrevPos;
    private Vector2 currentEntryRCursor;
    private Vector2 currentEntryLabelDims;

    public Vector2 EntryBegin(String headerName) {
        Vector2 position = this.position;
        Vector2 size = new Vector2(this.size.x, LINE_HEIGHT);

        g.setColor(BUTTON_BG);
        GG.drawLine(position.x + PADDING, position.y+size.y,
                    position.x + size.x - 2*PADDING, position.y+size.y);

        Vector2 labelDims = this.CenteredYLabel(headerName, new Vector2(PADDING, 0), new Vector2(0, LINE_HEIGHT));

        this.currentEntryLabelDims = labelDims;
        this.currentEntryPos = position;
        this.currentEntrySize = size;
        this.currentEntryPrevPos = this.position.scale(1.0);
        this.currentEntryRCursor = new Vector2(this.size.x - PADDING, 0);

        return size;
    }

    public boolean EntryButton(String text) {
        double buttonHeight = TileMapEditor.ED_FONT_SIZE+PADDING;

        boolean state = this.ButtonFromTopRight(text,
                                                this.currentEntryRCursor.add(new Vector2(0, LINE_HEIGHT/2.0 - buttonHeight/2.0)),
                                                new Vector2(0, buttonHeight));

        this.currentEntryRCursor.x -= this.lastButtonSize.x+PADDING;

        return state;
    }

    public double nextSliderWidth = 0;
    public double EntrySlider(double value, double min, double max) {
        double sliderThickness = 6.0;
        
        double rightX = this.currentEntryRCursor.x + this.position.x - PADDING;
        double leftX = this.currentEntryPos.x + this.currentEntryLabelDims.x + 4*PADDING;
        Vector2 sliderSize = new Vector2(rightX - leftX,
                                         sliderThickness);

        if (this.nextSliderWidth != 0) {
            if (this.nextSliderWidth <= 1.0) {
                sliderSize.x = sliderSize.x * this.nextSliderWidth;
            } else {
                sliderSize.x = this.nextSliderWidth;
            }
        }
        this.nextSliderWidth = 0;

        Vector2 sliderPos = this.position.add(this.currentEntryRCursor)
                                         .add(new Vector2(-(sliderSize.x + PADDING), LINE_HEIGHT/2.0 - sliderThickness/2.0));

        Rectangle sliderHitBox = new Rectangle((int)sliderPos.x, (int)(this.currentEntryRCursor.y+this.position.y), (int)sliderSize.x, (int)LINE_HEIGHT);
        boolean hovering = sliderHitBox.contains(Game.mousePos.x, Game.mousePos.y);
                                 
        if (hovering) {
            if (Game.IsKeyPressed(KeyEvent.VK_LEFT)) {
                value -= 1.0;
            } else if (Game.IsKeyPressed(KeyEvent.VK_RIGHT)) {
                value += 1.0;
            }
        }

        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        
        String maxText = Integer.toString((int)max), minText = Integer.toString((int)min);
        double maxTextWidth = m.stringWidth(maxText), minTextWidth = m.stringWidth(minText);

        double dotSize = 12.0;
        double currentPercent = (value - min)/(max - min);

        maxTextWidth += PADDING/2.0;
        minTextWidth += PADDING/2.0;

        sliderSize.x -= (maxTextWidth + minTextWidth);
        sliderPos.x += minTextWidth;

        Vector2 dotPosition = new Vector2(sliderPos.x + sliderSize.x*currentPercent, sliderPos.y + sliderThickness/2.0 - dotSize/2.0);

        g.setColor(BUTTON_BG);
        GG.fillRoundRect(sliderPos.x, sliderPos.y, sliderSize.x, sliderSize.y, sliderThickness, sliderThickness);

        double textY = -sliderThickness/2.0 + sliderPos.y + m.getHeight() - m.getHeight()/2.0;

        g.setColor(Color.WHITE);
        GG.drawString(minText, sliderPos.x - minTextWidth - PADDING/2.0, textY);
        GG.drawString(maxText, sliderPos.x + sliderSize.x + PADDING/2.0, textY);

        g.setColor(BUTTON_HILI_BG);
        GG.fillOval(dotPosition, new Vector2(dotSize));

        if (!hovering) {
            Color overlay = new Color(46, 46, 46, 100);
            g.setColor(overlay);
            g.fillRect(sliderHitBox.x, sliderHitBox.y, sliderHitBox.width, sliderHitBox.height);
        } else if (!this.disabled) {
            String currentValueText = Integer.toString((int)Vector2.lerp(min, max, currentPercent));
            int currentValueWidth = m.stringWidth(currentValueText);

            g.setColor(BUTTON_BORDER);
            GG.fillRect(dotPosition.x+dotSize/2.0 - currentValueWidth/2.0, dotPosition.y+dotSize/2.0 - m.getAscent()/2.0, currentValueWidth, m.getAscent());
            g.setColor(Color.WHITE);
            GG.drawString(currentValueText, dotPosition.x+dotSize/2.0 - currentValueWidth/2.0, m.getAscent() + dotPosition.y+dotSize/2.0 - m.getAscent()/2.0);

            if (Game.IsMouseDown(MouseEvent.BUTTON1)) {
                double newPercentage = (Game.mousePos.x - sliderPos.x)/sliderSize.x;
                if (newPercentage > 1.0) newPercentage = 1.0;
                if (newPercentage < 0.0) newPercentage = 0.0;

                return min + (max - min) * newPercentage;
            }
        }

        return value;
    }

    public void EntryEnd() {
        this.position = this.currentEntryPrevPos.add(new Vector2(0.0, this.currentEntrySize.y + PADDING));
    }

    public boolean nextLabelAbsPosition = false;
    public Vector2 CenteredLabel(String text, Vector2 position, Vector2 size) {
        if (!this.nextLabelAbsPosition)
            position = position.add(this.position);
        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        int textWidth = m.stringWidth(text);

        g.setColor(Color.WHITE);
        g.drawString(text, (int)(position.x + size.x / 2 - textWidth / 2),
                     (int)(position.y + size.y/2.0 + m.getHeight()/2.0));
        
        this.nextLabelAbsPosition = false;

        return size;
    }

    public void LayoutVertBAdded(double spacing) {
        Vector2 buttonSize = this.lastButtonSize;
        this.position.y += buttonSize.y+spacing;
    }

    private double flowLayoutCurrentHeight = 0;
    private Vector2 flowLayoutCursor = new Vector2();

    public void FlowLayoutBegin() {
        this.flowLayoutCursor = new Vector2(PADDING);
        this.flowLayoutCurrentHeight = 0;
    }

    public void FlowLayoutEnd() {
        this.position = new Vector2(this.position.x, this.position.y + this.flowLayoutCursor.y + this.flowLayoutCurrentHeight + 2*PADDING);
    }

    public Vector2 FlowLayoutAdd(Vector2 size) {
        if (this.flowLayoutCursor.x + size.x > this.size.x) {
            this.flowLayoutCursor.y += this.flowLayoutCurrentHeight + PADDING;
            this.flowLayoutCursor.x = PADDING;
            this.flowLayoutCurrentHeight = 0;
        }
        
        if (size.y > this.flowLayoutCurrentHeight) {
            this.flowLayoutCurrentHeight = size.y;
        }

        Vector2 elementPos = flowLayoutCursor.scale(1.0);
        flowLayoutCursor.x += size.x + PADDING;
        
        return elementPos.add(this.position);
    }

    private Vector2 currentListPrevPosition;
    private Vector2 currentListPrevSize;
    protected Vector2 currentListTopLeft;
    private String currentListRandomName;

    public double GetListScroll() {
        Double value = Panel.scrolls.get(this.currentListRandomName);
        if (value != null) {
            return value.doubleValue();
        }
        return 0;   
    }

    public Vector2 ListBegin(String uniqueName, Vector2 offset, Vector2 size) {
        this.currentListRandomName = uniqueName;
        this.currentListPrevPosition = this.position.scale(1.0); 
        this.currentListPrevSize = this.size.scale(1.0);

        if (offset.x <= 1.0) {
            offset.x = this.size.x * offset.x;
        }
        if (offset.y <= 1.0) {
            offset.y = this.remainingVerticalSpace() * offset.y;
        }

        Vector2 position = this.position.add(new Vector2(PADDING, 0)).add(offset);
        this.currentListTopLeft = position.scale(1.0);

        if (size.x >= -1.0 && size.x <= 1.0) {
            size.x = this.size.x * size.x;
        }
        if (size.y >= -1.0 && size.y <= 1.0) {
            size.y = this.remainingVerticalSpace() * size.y;
        }

        if (size.x == 0) {
            size.x = this.size.x - 2*PADDING; // Expand to full width
        }
        if (size.y <= 0) {
            size.y = this.size.y - (this.position.y - this.windowPosition.y) - PADDING + size.y; // Remaning space
        }

        g.setColor(BUTTON_BORDER);
        GG.drawRect(position, size);

        this.clipsStack.add(g.getClip());
        g.setClip((int)position.x, (int)position.y, (int)size.x, (int)size.y);

        Double scrollPixels = Panel.scrolls.get(this.currentListRandomName);
        if (scrollPixels == null) {
            scrollPixels = 0.0;
        }

        // g.translate(0, -scrollPixels);
        this.position.x = position.x;
        this.position.y = position.y;
        this.position.y -= scrollPixels;

        this.size = size;
        
        return size;
    }
    
    public void ListEnd() {
        this.currentListPrevPosition.y += this.size.y + PADDING;
        
        Double scrollPixels = Panel.scrollsTarget.get(this.currentListRandomName);
        Double scrollPixelsNow = Panel.scrolls.get(this.currentListRandomName);

        if (scrollPixels == null || scrollPixelsNow == null) {
            scrollPixels = 0.0;
            scrollPixelsNow = 0.0;
        }

        double contentBottomCoord = ((this.position.y+scrollPixels) -this.currentListTopLeft.y);

        if ((contentBottomCoord != 0 && contentBottomCoord > this.size.y)) {
            double scrollBarWidth = 10.0;
            Vector2 scrollBarPos = this.currentListTopLeft.add(new Vector2(this.size.x - scrollBarWidth, 0));
            Vector2 scrollBarSize = new Vector2(scrollBarWidth, this.size.y);

            double percentContentVisible = this.size.y / contentBottomCoord;
            double scrollBarButtonSizeY = this.size.y * percentContentVisible;

            double minScroll = 0.0, maxScroll = contentBottomCoord - this.size.y + 2*PADDING;
            double deltaScroll = Game.deltaScroll;

            if (scrollPixels < minScroll) {
                scrollPixels = minScroll;
                deltaScroll = 0;
            } else if (scrollPixels > maxScroll) {
                scrollPixels = maxScroll;
                deltaScroll = 0;
            }
            if (!this.disabled && Vector2.AABBContainsPoint(this.currentListTopLeft, this.size, Game.mousePos)) {
                scrollPixels += deltaScroll * 4.0;
            }

            // TODO: Fix weird effect where scroll bar changes size
            double percentScroll = scrollPixelsNow/maxScroll;

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
        Panel.scrollsTarget.put(this.currentListRandomName, scrollPixels);

        this.position = this.currentListPrevPosition;
        this.size = this.currentListPrevSize;

        if (this.clipsStack.size() > 0) {
            g.setClip(this.clipsStack.remove(this.clipsStack.size()-1));
        } else {
            g.setClip(0, 0, Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT);
        }
    }

    private double remainingVerticalSpace() {
        return this.size.y - (this.position.y-this.windowPosition.y);
    }

    public Vector2 CenteredYLabel(String text, Vector2 position, Vector2 size) {
        position = position.add(this.position);
        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        g.setColor(Color.WHITE);

        int baselineY = (int) (position.y + (size.y - (m.getAscent() + m.getDescent())) / 2 + m.getAscent());

        size.x = m.stringWidth(text);
        g.drawString(text, (int)(position.x), baselineY);
        
        return size;
    }

    protected Vector2 lastButtonSize;
    public boolean Button(String text, Vector2 position, Vector2 size) {
        if (!this.nextButtonAbsPos) {
            if (position.x <= 1.0) {
                position.x = position.x * this.size.x;
            }
            if (position.y <= 1.0) {
                position.y = position.y * this.remainingVerticalSpace();
            }
    
            position = position.add(this.position);
        }

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

        boolean inactive = this.nextButtonDisabled;
        boolean highlight = this.nextButtonHighlight;
        boolean hovering = !this.disabled && (!inactive && Vector2.AABBContainsPoint(position, size, Game.mousePos));
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
        this.nextButtonDisabled = false;
        this.nextButtonHighlight = false;
        this.nextButtonAbsPos = false;

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
    public TileMap map;

    static Font ED_FONT;
    static int ED_FONT_SIZE = 14;

    private TileMapLayer currentLayer;
    private SpriteSheet currentSelectedSheet;
    private JFileChooser fileChooser;

    private SpriteSheet sslSheet = null;
    private String sslError = null;
    private boolean sslVisible = false;
    private BufferedImage sslImage = null;
    private boolean sslIsNew = true;
    private String sslImgPath = null;
    
    private double sslZoomTarget = 3.0;
    private double sslZoom = 3.0;
    private Vector2 sslScroll = new Vector2();

    private ArrayList<Tile> sslSelection = new ArrayList<>();
    private Tile sslSelectedKeyTile;
    private Vector2 sslSelectionMouseStart = null;
    
    private void sslReset() {
        this.sslVisible = false;
        this.sslImgPath = null;
        this.sslImage = null;
        this.sslError = null;
        this.sslSelection = new ArrayList<>();
        this.sslSheet = null;
        this.sslSelectionMouseStart = null;
        this.sslIsNew = true;
        this.sslScroll = new Vector2();
        this.sslZoomTarget = 3.0;
        this.sslZoom = 3.0;
    }

    private void SSLGroupSelection() {
        if (this.sslSelection.size() < 2) return;

        int topLeftX = Integer.MAX_VALUE, topLeftY = Integer.MAX_VALUE;
        int bottomRightX = Integer.MIN_VALUE, bottomRightY = Integer.MIN_VALUE;
        
        Tile topLeftTile = null;
        Tile bottomRightTile = null;

        for (Tile t : this.sslSelection) {
            if (t.x < topLeftX) {
                topLeftTile = t;
                topLeftX = t.x;
            }
            if (t.y < topLeftY) {
                topLeftY = t.y;
            }
            if (t.x > bottomRightX) {
                bottomRightTile = t;
                bottomRightX = t.x;
            }
            if (t.y > bottomRightY) {
                bottomRightY = t.y;
            }
        }

        if (topLeftTile != null && bottomRightTile != null) {
            // Clear tiles within the rectangle defined by topLeft and bottomRight
            for (int x = topLeftX; x <= bottomRightX; x++) {
                for (int y = topLeftY; y <= bottomRightY; y++) {
                    this.sslSheet.tiles.set(y * this.sslSheet.numTilesX + x, null);
                }
            }

            topLeftTile.x = topLeftX;
            topLeftTile.y = topLeftY;

            int tlIndex = topLeftTile.y * this.sslSheet.numTilesX + topLeftTile.x;
            topLeftTile.w = bottomRightX - topLeftX + 1;
            topLeftTile.h = bottomRightY - topLeftY + 1;
            topLeftTile.textureIndex = tlIndex;

            System.out.println("[LOG]: Created group tile of size " + topLeftTile.w + "x" + topLeftTile.h + ".");

            this.sslSheet.tiles.set(tlIndex, topLeftTile);

            this.sslSelection.clear();
            this.sslSelection.add(topLeftTile);
        }
    }

    private void SSLUnGroupSelection() {
        if (this.sslSelection.size() != 1) return;
        
        Tile selection = this.sslSelection.get(0);
        this.sslSheet.tiles.set(selection.GetSheetIndex(), null);

        this.sslSelection.clear();

        for (int x = selection.x; x < selection.x + selection.w; x++) {
            for (int y = selection.y; y < selection.y + selection.h; y++) {
                int index = y * this.sslSheet.numTilesX + x;
                Tile newTile = new Tile(x, y, this.sslSheet, index);
                if (this.sslSheet.tilesPurged && newTile.IsBlank()) {
                    continue;
                }

                this.sslSheet.tiles.set(index, newTile);
                this.sslSelection.add(newTile);
            }
        }
    }
    
    private void SpriteSheetLoader(Graphics2D g) {
        this.layers.disabled = true;
        this.sheetsPanel.disabled = true;

        if (this.sslSheet != null) {
            this.sslImage = this.sslSheet.GetCPUImage();
            this.sslImgPath = null;
        }

        if (this.sslImgPath != null) {
            try {
                BufferedImage loadedImage = ImageIO.read(new File(this.sslImgPath));
                if (loadedImage != null) {
                    // BufferedImage compatibleImage = GraphicsEnvironment
                    //         .getLocalGraphicsEnvironment()
                    //         .getDefaultScreenDevice()
                    //         .getDefaultConfiguration()
                    //         .createCompatibleImage(loadedImage.getWidth(), loadedImage.getHeight(), Transparency.BITMASK);

                    // Graphics2D cig = compatibleImage.createGraphics();
                    // cig.drawImage(loadedImage, 0, 0, null);
                    // cig.dispose();

                    this.sslImage = loadedImage;//loadedImage;
                    this.sslError = null;
                } else {
                    throw new Exception("Tried to load image but got NULL.");
                }
            } catch (Exception e) {
                this.sslError = e.getLocalizedMessage();
            }
        }

        if (this.sslSheet == null && this.sslImage != null) {
            this.sslSheet = new SpriteSheet((BufferedImage)this.sslImage, 16);
            this.sslSheet.name = new File(this.sslImgPath).getName();
        }
        
        final double ssEditorPadding = 40.;
        Panel ssEditor = new Panel();
        ssEditor.Begin(g, new Vector2(ssEditorPadding), new Vector2(Game.WINDOW_WIDTH-2*ssEditorPadding, Game.WINDOW_HEIGHT-2*ssEditorPadding));

        ssEditor.Name("Sprite Sheet Editor \"" + this.sslImgPath + "\"");
        if (ssEditor.CloseButton()) {
            this.sslReset();
            return;
        }

        ssEditor.size.y -= Panel.PADDING;

        Vector2 prevPosition = ssEditor.position.scale(1.0);
        Vector2 prevSize = ssEditor.size.scale(1.0);

        if (this.sslError == null && this.sslImage != null && this.sslSheet != null) {
            Vector2 leftCSize = ssEditor.ListBegin("SSEdLeftC", new Vector2(), new Vector2(0.33, -40));
                ssEditor.size.x -= 2*Panel.PADDING;
                ssEditor.size.y -= Panel.PADDING;

                ssEditor.EntryBegin("Sprite Sheet Properties");
                ssEditor.EntryEnd();

                ssEditor.ListBegin("SSEditorProperties", new Vector2(), new Vector2(1.0, 0.5));
                    ssEditor.EntryBegin("Tile Size: ");
                    
                    if (ssEditor.EntryButton("Update & Reset")) {
                        this.sslSheet.UpdateTilesSize();
                    }
                    this.sslSheet.tileSize = (int)ssEditor.EntrySlider(this.sslSheet.tileSize, 2, 128);

                    ssEditor.EntryEnd();

                    ssEditor.EntryBegin("Grid Visibiltiy: ");
                    if (ssEditor.EntryButton("Toggle")) {
                        this.sslGridVisible = !this.sslGridVisible;
                    }
                    ssEditor.EntryEnd();

                    ssEditor.EntryBegin("Auto Purge: ");
                    ssEditor.nextButtonDisabled = this.sslSheet.tilesPurged;
                    if (ssEditor.EntryButton("Purge Blank Tiles")) {
                        this.sslSheet.PurgeBlankTiles();
                    }
                    ssEditor.EntryEnd();

                    ssEditor.EntryBegin("Group Selection: ");
                    ssEditor.nextButtonDisabled = this.sslSelection.size() < 2;
                    if (ssEditor.EntryButton("Group")) {
                        this.SSLGroupSelection();
                    }

                    ssEditor.nextButtonDisabled = this.sslSelection.size() == 1 && !this.sslSelection.get(0).IsCompoundTile();
                    if (ssEditor.EntryButton("Ungroup")) {
                        this.SSLUnGroupSelection();
                    }

                    ssEditor.EntryEnd();
                ssEditor.ListEnd();

                ssEditor.EntryBegin("Tile Properties");
                ssEditor.EntryEnd();

                ssEditor.ListBegin("SSTileProperties", new Vector2(), new Vector2(1.0, 1.0));
                ssEditor.ListEnd();

                if (ssEditor.Button("Cancel", new Vector2(Panel.PADDING, 0), new Vector2(0, 40))) {
                    this.sslReset();
                    return;
                }

                if (ssEditor.Button(this.sslIsNew ? "Create Sheet" : "Save Sheet", new Vector2(ssEditor.lastButtonSize.x + 4*Panel.PADDING, 0), new Vector2(0, 40))) {
                    if (this.sslIsNew) {
                        this.map.ownedSheets.add(this.sslSheet);
                    }

                    System.out.println("[LOG]: Added sprite sheet: `" + this.sslSheet.name + "` to map.");
                    this.sslReset();

                    return;
                }
            ssEditor.ListEnd();


            prevPosition.x += leftCSize.x + Panel.PADDING;
            prevSize.x -= leftCSize.x + 2*Panel.PADDING;

            ssEditor.position = prevPosition;
            ssEditor.size = prevSize;

            ssEditor.ListBegin("spriteSheetEdSheet", new Vector2(0, 0), new Vector2(1.0, 1.0));

            g.setColor(Color.BLACK);
            g.fillRect(0, 0, Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT);

            AffineTransform prevTrans = g.getTransform();
            Vector2 imagePos = new Vector2(-this.sslImage.getWidth(null) / 2.0, -this.sslImage.getHeight(null) / 2.0);
            boolean hovering = Vector2.AABBContainsPoint(ssEditor.position, ssEditor.size, Game.mousePos);

            // Update smooth zoom
            this.sslZoom = Vector2.lerpFRI(this.sslZoom, this.sslZoomTarget, 0.99, Game.deltaTime);

            // Apply transformations
            g.translate(this.sslScroll.x, this.sslScroll.y);
            g.translate(ssEditor.position.x + ssEditor.size.x / 2.0, ssEditor.position.y + ssEditor.size.y / 2.0);
            g.scale(this.sslZoom, this.sslZoom);

            AffineTransform currentTransform = g.getTransform();

            // Transform the mouse position relative to the current view
            Point relativeMousePos = new Point();
            try {
                currentTransform.inverseTransform(
                    new Point((int) Game.mousePos.x, (int) Game.mousePos.y),
                    relativeMousePos
                );
            } catch (Exception e) {
                e.printStackTrace(); // Handle any exceptions from inverse transform
            }

            if (hovering) {
                // Adjust zoom based on scroll input
                this.sslZoomTarget -= Game.deltaScroll * 0.06;

                // Handle panning (mouse drag with middle button)
                if (Game.IsMousePressed(MouseEvent.BUTTON2) && this.sslMovMouseStart == null) {
                    this.sslMovMouseStart = Game.mousePos.scale(1.0);
                    this.sslMovInitialScroll = this.sslScroll.scale(1.0);
                }

                if (this.sslMovMouseStart != null) {
                    Vector2 delta = this.sslMovMouseStart.sub(Game.mousePos);
                    this.sslScroll = this.sslMovInitialScroll.sub(delta);
                    Game.currentCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
                }

                if (Game.IsMouseReleased(MouseEvent.BUTTON2)) {
                    this.sslMovMouseStart = null;
                }

                if (Game.IsMousePressed(MouseEvent.BUTTON1)) {
                    if (!Game.IsKeyDown(KeyEvent.VK_CONTROL)) {
                        this.sslSelection.clear();
                    }
                    this.sslSelectionMouseStart = new Vector2(relativeMousePos.x, relativeMousePos.y);
                }
                if (Game.IsMouseReleased(MouseEvent.BUTTON1)) {
                    this.sslSelectionMouseStart = null;
                }
            }

            Rectangle selectionRectangle = new Rectangle();
            if (this.sslSelectionMouseStart != null) {
                // Calculate the initial rectangle with potentially negative width/height
                int startX = (int) this.sslSelectionMouseStart.x;
                int startY = (int) this.sslSelectionMouseStart.y;
                int endX = (int) relativeMousePos.x;
                int endY = (int) relativeMousePos.y;

                // Normalize the rectangle to ensure positive width and height
                int normalizedX = Math.min(startX, endX);
                int normalizedY = Math.min(startY, endY);
                int normalizedWidth = Math.abs(endX - startX) + 1;
                int normalizedHeight = Math.abs(endY - startY) + 1; // Add one to always select

                // Set the normalized rectangle
                selectionRectangle.setBounds(normalizedX, normalizedY, normalizedWidth, normalizedHeight);

                this.sslSelection.clear();
            }

            for (int y = 0; y < this.sslSheet.numTilesY; y++) {
                for (int x = 0; x < this.sslSheet.numTilesX; x++) {
                    Tile t = this.sslSheet.tiles.get(y * this.sslSheet.numTilesX + x);
                    if (t != null) {
                        Rectangle tileRect = new Rectangle((int)(imagePos.x + x*this.sslSheet.tileSize),
                                                           (int)(imagePos.y + y*this.sslSheet.tileSize), 
                                                           this.sslSheet.tileSize, this.sslSheet.tileSize);

                        if (selectionRectangle.intersects(tileRect) || tileRect.intersects(selectionRectangle)) {
                            this.sslSelection.add(t);

                            if (tileRect.contains(relativeMousePos)) {
                                this.sslSelectedKeyTile = t;
                            }
                        }

                        t.Draw(g, tileRect.x, tileRect.y, tileRect.width, tileRect.height);
                    }
                }
            }

            for (int y = 0; y < this.sslSheet.numTilesY; y++) {
                for (int x = 0; x < this.sslSheet.numTilesX; x++) {
                    Tile t = this.sslSheet.tiles.get(y * this.sslSheet.numTilesX + x);
                    if (t == null)
                        continue;

                    Color tileOutlineColor = new Color(175, 50, 200, 100);
                    Rectangle tileRectangle = new Rectangle();

                    boolean drawOutline = this.sslGridVisible;
                    boolean hoveringTile = false;
                    boolean selected = (this.sslSelection.indexOf(t) != -1);
                    boolean lastSelected = this.sslSelectedKeyTile == t;

                    tileRectangle.x = (int)(imagePos.x + x * this.sslSheet.tileSize);
                    tileRectangle.y = (int)(imagePos.y + y * this.sslSheet.tileSize);
                    tileRectangle.width = this.sslSheet.tileSize * t.w;
                    tileRectangle.height = this.sslSheet.tileSize * t.h;

                    if (tileRectangle.contains(relativeMousePos)) {
                        tileOutlineColor = new Color(175, 50, 200);
                        hoveringTile = true;
                        drawOutline = true;
                    }

                    if (selected) {
                        tileOutlineColor = Panel.BUTTON_HILI_BG;
                        drawOutline = true;

                        if (lastSelected) {
                            tileOutlineColor = new Color(91, 207, 117);
                        }
                    }

                    g.setColor(tileOutlineColor);

                    if (drawOutline) {
                        g.setStroke(new BasicStroke((hoveringTile || lastSelected) ? 0.6f : 0.25f));

                        GG.drawRect(tileRectangle.x, tileRectangle.y, tileRectangle.width, tileRectangle.height);
                    }
                }
            }

            g.setColor(new Color(175, 50, 200, 100));
            g.fillRect(selectionRectangle.x, selectionRectangle.y, selectionRectangle.width, selectionRectangle.height);

            g.setTransform(prevTrans);

            ssEditor.ListEnd();
        } else {
            ssEditor.EntryBegin("Error: " + this.sslError);
            ssEditor.EntryEnd();
        }

        ssEditor.End();
    }

    private double ssDisplayZoom = 17.0;
    private boolean sslGridVisible = true;
    private Vector2 sslMovMouseStart = null;
    private Vector2 sslMovInitialScroll = null;


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
    }
    
    Panel sheetsPanel = new Panel();
    Panel layers = new Panel();

    public void Draw(Graphics2D g) throws NoninvertibleTransformException {
        Panel.context = "Editor";

        layers.Begin(g, new Vector2(400, 40), new Vector2(300, 300));
        
        layers.Name("Layers");
        
        layers.EntryBegin("Layers");
        if (layers.EntryButton("New")) {
            TileMapLayer newLayer = new TileMapLayer(map);
            
            newLayer.name = "Layer " + (++this.layerCount);
            this.currentLayer = newLayer;

            this.map.layers.add(newLayer);
        }
        
        layers.nextButtonDisabled = (currentLayer == null);
        if (layers.EntryButton("Delete")) {
            this.map.layers.remove(this.currentLayer);
            if (this.map.layers.size() == 0) {
                this.currentLayer = null;
            } else {
                this.currentLayer = this.map.layers.get(this.map.layers.size()-1);
            }
        }
        layers.nextButtonDisabled = (currentLayer == null);
        if (layers.EntryButton("Rename")) {
            // TODO: Rename layers
        }
        layers.EntryEnd();
        
        layers.ListBegin("Layers", new Vector2(), new Vector2(0, -40.0));   
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
        
        layers.nextButtonDisabled = (currentIndex == this.map.layers.size()-1);
        if (layers.Button("Down", new Vector2(0.0, 0), new Vector2(0.5, 1.0))) {
            this.map.layers.remove(this.currentLayer);
            map.layers.add(currentIndex + 1, this.currentLayer);
        }
        
        layers.nextButtonDisabled = (currentIndex == 0);
        if (layers.Button("Up", new Vector2(0.5, 0), new Vector2(0.5, 1.0))) {
            this.map.layers.remove(this.currentLayer);
            map.layers.add(currentIndex - 1, this.currentLayer);
        }
        layers.End();
            
            
        sheetsPanel.Begin(g, new Vector2(100, 40), new Vector2(300, 300));
        sheetsPanel.Name("Sprite Sheets");
            
        sheetsPanel.EntryBegin("Sheets");
            if (sheetsPanel.EntryButton("Add New")) {
                if (this.fileChooser == null) {
                    this.sheetsPanel.disabled = true;

                    Game.ResetMouse();

                    this.fileChooser = new JFileChooser("./res");
                    this.fileChooser.setVisible(true);
                    
                    int r = this.fileChooser.showSaveDialog(null);
                    if (r == JFileChooser.APPROVE_OPTION) {
                        File f = this.fileChooser.getSelectedFile();
                        
                        this.sslReset();

                        this.sslImgPath = f.getPath();
                        this.sslVisible = true;
                        this.sslIsNew = true;
                    }
                    
                    Game.ResetMouse(); // Reseting mouse after file picker is neccesarry as it skips frames

                    this.fileChooser = null;
                }
            }
            
            sheetsPanel.nextSliderWidth = 100;
            this.ssDisplayZoom = sheetsPanel.EntrySlider(this.ssDisplayZoom, 0, 100);
        sheetsPanel.EntryEnd();

        Vector2 listSize = sheetsPanel.ListBegin("SpriteSheetList", new Vector2(), new Vector2(0, -(Panel.LINE_HEIGHT + 3*Panel.PADDING)));
            final double minDisplayWidth = 50, maxDisplayWidth = 250;
            Vector2 boxSize = new Vector2(Vector2.lerp(minDisplayWidth, maxDisplayWidth, this.ssDisplayZoom/100.0),
                                          Vector2.lerp(minDisplayWidth/0.75, maxDisplayWidth/0.75, this.ssDisplayZoom/100.0));

            if (Vector2.AABBContainsPoint(sheetsPanel.currentListTopLeft, listSize, Game.mousePos)) {
                if (!sheetsPanel.disabled && Game.IsMousePressed(MouseEvent.BUTTON1)) {
                    this.currentSelectedSheet = null;
                }
            }

            sheetsPanel.FlowLayoutBegin();
            
            for (SpriteSheet sheet : this.map.ownedSheets) {
                Rectangle imageArea = new Rectangle();
                Vector2 pos = sheetsPanel.FlowLayoutAdd(boxSize);
                double textAreaHeight = Panel.LINE_HEIGHT+Panel.PADDING;
                
                Shape prevClip = g.getClip();

                boolean hovering = Vector2.AABBContainsPoint(pos, boxSize, Game.mousePos);
                boolean selected = (this.currentSelectedSheet == sheet);

                if (!sheetsPanel.disabled && hovering) {
                    if (Game.IsMousePressed(MouseEvent.BUTTON1)) {
                        this.currentSelectedSheet = sheet;
                    }
                }

                g.setColor(selected ? Panel.BUTTON_HILI_BG : (hovering ? Panel.BUTTON_HOV_BG : Panel.BUTTON_BG));
                GG.fillRect(pos, boxSize);
    
                g.setColor(Panel.BUTTON_BORDER);
                GG.drawRect(pos, boxSize.sub(new Vector2(1)));
                
                imageArea.x = (int)(pos.x + Panel.PADDING);
                imageArea.y = (int)(pos.y + Panel.PADDING);
                
                imageArea.width = (int)(boxSize.x - 2*Panel.PADDING);
                imageArea.height = (int)(boxSize.y - textAreaHeight);
                
                g.setColor(Color.BLACK);
                GG.fillRect(imageArea.x, imageArea.y, imageArea.width, imageArea.height);

                g.clipRect((int)pos.x, (int)pos.y, (int)boxSize.x, (int)boxSize.y);

                // Calculate the aspect ratio of the image and the image area
                double imageAspect = (double) sheet.image.getWidth() / sheet.image.getHeight();
                double areaAspect = (double) imageArea.width / imageArea.height;

                // Determine the dimensions of the drawn image while maintaining the aspect ratio
                int drawWidth, drawHeight;
                if (imageAspect > areaAspect) {
                    // Image is wider than the area; scale based on width
                    drawWidth = imageArea.width;
                    drawHeight = (int) (drawWidth / imageAspect);
                } else {
                    // Image is taller than the area; scale based on height
                    drawHeight = imageArea.height;
                    drawWidth = (int) (drawHeight * imageAspect);
                }

                // Center the image within the imageArea
                int drawX = imageArea.x + (imageArea.width - drawWidth) / 2;
                int drawY = imageArea.y + (imageArea.height - drawHeight) / 2;

                // Draw the image
                g.drawImage(
                    sheet.image,
                    drawX, drawY, drawX + drawWidth, drawY + drawHeight, // Destination rectangle
                    0, 0, sheet.GetImage(g).getWidth(null), sheet.GetImage(g).getHeight(null),       // Source rectangle
                    null                                                // Image observer
                );

                sheetsPanel.nextLabelAbsPosition = true;
                sheetsPanel.CenteredLabel(sheet.name, pos.add(new Vector2(0, imageArea.height)), new Vector2(boxSize.x, textAreaHeight));

                g.setClip(prevClip);
            }

            sheetsPanel.FlowLayoutEnd();
        sheetsPanel.ListEnd();

        listSize = sheetsPanel.ListBegin("SpriteSheetListActions", new Vector2(), new Vector2(0.0, 0.0));
            sheetsPanel.FlowLayoutBegin();
            
            Vector2 buttonSize = new Vector2(sheetsPanel.size.x / 3.0 - 1.5*Panel.PADDING, listSize.y - 2*Panel.PADDING);
            Vector2 position = sheetsPanel.FlowLayoutAdd(buttonSize);
            
            sheetsPanel.nextButtonAbsPos = true;
            sheetsPanel.nextButtonDisabled = (this.currentSelectedSheet == null);
            if (sheetsPanel.Button("Delete", position, buttonSize)) {
                if (this.currentSelectedSheet != null) {
                    this.map.DeleteSheet(this.currentSelectedSheet);
                    this.currentSelectedSheet = null;
                }
            }

            position = sheetsPanel.FlowLayoutAdd(buttonSize);
            sheetsPanel.nextButtonAbsPos = true;
            sheetsPanel.nextButtonDisabled = (this.currentSelectedSheet == null);
            if (sheetsPanel.Button("Edit", position, buttonSize)) {
                this.sslReset();
                this.sslIsNew = false;
                this.sslSheet = this.currentSelectedSheet;
                this.sslVisible = true;
            }

            position = sheetsPanel.FlowLayoutAdd(buttonSize);
            sheetsPanel.nextButtonAbsPos = true;
            sheetsPanel.nextButtonDisabled = (this.currentSelectedSheet == null);
            sheetsPanel.Button("Open", position, buttonSize);

            sheetsPanel.FlowLayoutEnd();
        sheetsPanel.ListEnd();

        sheetsPanel.End();
        
        if (sslVisible) {
            SpriteSheetLoader(g);
        } else {
            this.layers.disabled = false;
            this.sheetsPanel.disabled = false;
        }
    }
}