import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.*;
import java.awt.image.*;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;


class TileMapEditor {
    public TileMap map;

    static Font ED_FONT;
    static int ED_FONT_SIZE = 14;

    private TileMapLayer currentLayer;
    private SpriteSheet currentSelectedSheet;
    private JFileChooser fileChooser;

    private ArrayList<Tile> mapSelection = new ArrayList<>();

    private SpriteSheet sslSheet = null;
    private String sslError = null;
    private boolean sslVisible = false;
    private BufferedImage sslImage = null;
    private boolean sslIsNew = true;
    private String sslImgPath = null;
    private boolean sslLeftPanelOpen = true;
    
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
        this.sslLeftPanelOpen = true;
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
                    this.sslSheet.tiles.set(y * this.sslSheet.numTilesX + x, new Tile(x, y, this.sslSheet, -1));
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
            this.sslSelectedKeyTile = topLeftTile;
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
    
    Panel ssEditor = new Panel();

    private void SpriteSheetLoader(Graphics2D g) {
        if (this.sslLeftPanelOpen) {
            this.layers.disabled = true;
            this.sheetsPanel.disabled = true;
        } else {
            this.layers.disabled = false;
            this.sheetsPanel.disabled = false;    
        }

        if (this.sslSheet != null) {
            this.sslImage = this.sslSheet.GetCPUImage();
            this.sslImgPath = null;
        }

        if (Game.IsKeyPressed(KeyEvent.VK_H)) {
            this.sslLeftPanelOpen = !this.sslLeftPanelOpen;
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
            this.sslSheet = new SpriteSheet((BufferedImage)this.sslImage, this.sslImgPath, 16);
            this.sslSheet.name = new File(this.sslImgPath).getName();
        }
        
        final double ssEditorPadding = 40.;
        ssEditor.Begin(g, new Vector2(ssEditorPadding), new Vector2(Game.WINDOW_WIDTH-2*ssEditorPadding, Game.WINDOW_HEIGHT-2*ssEditorPadding));

        ssEditor.Name("Sprite Sheet Editor \"" + this.sslSheet!=null ? this.sslSheet.name : "None" + "\"");
        if (ssEditor.CloseButton()) {
            this.sslReset();
            return;
        }

        ssEditor.size.y -= Panel.PADDING;

        Vector2 prevPosition = ssEditor.position.scale(1.0);
        Vector2 prevSize = ssEditor.size.scale(1.0);

        if (this.sslError == null && this.sslImage != null && this.sslSheet != null) {
            Vector2 leftCSize = new Vector2();
            if (this.sslLeftPanelOpen) {
                leftCSize = ssEditor.ListBegin("SSEdLeftC", new Vector2(), new Vector2(0.33, -40));
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
            }

            prevPosition.x += leftCSize.x + Panel.PADDING;
            prevSize.x -= leftCSize.x + 2*Panel.PADDING;

            ssEditor.position = prevPosition;
            ssEditor.size = prevSize;

            ssEditor.ListBegin("spriteSheetEdSheet", new Vector2(0, 0), new Vector2(1.0, 1.0));

            g.setColor(Color.BLACK);
            GG.fillRect(0, 0,
                        Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT);

            AffineTransform prevTrans = g.getTransform();
            Vector2 imagePos = new Vector2(-this.sslImage.getWidth(null) / 2.0, -this.sslImage.getHeight(null) / 2.0);
            boolean hovering = Vector2.AABBContainsPoint(ssEditor.position, ssEditor.size, Game.worldMousePos);

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
                prevTrans.transform(
                    new Point((int) Game.worldMousePos.x, (int) Game.worldMousePos.y),
                    relativeMousePos
                );
                currentTransform.inverseTransform(
                    relativeMousePos,
                    relativeMousePos
                );
                // relativeMousePos = new Point((int) Game.worldMousePos.x, (int) Game.worldMousePos.y);
            } catch (Exception e) {
                e.printStackTrace(); // Handle any exceptions from inverse transform
            }
            if (hovering) {
                // Adjust zoom based on scroll input
                this.sslZoomTarget -= Game.deltaScroll * 0.06;

                // Handle panning (mouse drag with middle button)
                if (Game.IsMousePressed(MouseEvent.BUTTON3) && this.sslMovMouseStart == null) {
                    this.sslMovMouseStart = Game.worldMousePos.scale(1.0);
                    this.sslMovInitialScroll = this.sslScroll.scale(1.0);
                }

                if (this.sslMovMouseStart != null) {
                    Vector2 delta = this.sslMovMouseStart.sub(Game.worldMousePos);
                    this.sslScroll = this.sslMovInitialScroll.sub(delta);
                    Game.currentCursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
                }

                if (Game.IsMouseReleased(MouseEvent.BUTTON3)) {
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
                    if (t != null && !t.IsNull()) {
                        Rectangle tileRect = new Rectangle((int)(imagePos.x + x*this.sslSheet.tileSize),
                                                           (int)(imagePos.y + y*this.sslSheet.tileSize), 
                                                           this.sslSheet.tileSize*t.w, this.sslSheet.tileSize*t.h);

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
                    if (t == null || t.IsNull())
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
        if (this.map != null) {
            if (Game.IsKeyPressed(KeyEvent.VK_S) && Game.IsKeyDown(KeyEvent.VK_CONTROL)) {
                this.map.Save("./res/tempMapFile.wmap");
            }
        }
    }
    
    Panel sheetsPanel = new Panel();
    Panel layers = new Panel();
    Panel tools = new Panel();

    
    String[] toolButtons = {"Paint", "Erase", "Random Selection"};
    String currentTool = toolButtons[0];

    public void Draw(Graphics2D g) throws NoninvertibleTransformException {
        Panel.context = "Editor";

        if (this.map != null) {
            for (TileMapLayer l : this.map.layers) {
                for (Tile t : l.tiles) {
                    if (t.IsNull()) continue;

                    Color tileOutlineColor = new Color(175, 50, 200, 100);
                    Rectangle tileRectangle = new Rectangle();

                    boolean drawOutline = this.sslGridVisible;
                    boolean hoveringTile = false;
                    boolean selected = (this.sslSelection.indexOf(t) != -1);
                    boolean lastSelected = this.sslSelectedKeyTile == t;

                    Vector2 position = this.map.LocalToWorldVectorScalar(new Vector2(t.x, t.y));
                    Vector2 tileSize = this.map.LocalToWorldVectorScalar(new Vector2(t.w, t.h));

                    tileRectangle.x = (int)(position.x);
                    tileRectangle.y = (int)(position.y);
                    tileRectangle.width = (int)(tileSize.x);
                    tileRectangle.height = (int)(tileSize.y);

                    if (tileRectangle.contains(new Point((int)Game.worldMousePos.x, (int)Game.worldMousePos.y))) {
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

                    
                    if (drawOutline) {
                        g.setColor(tileOutlineColor);
                        g.setStroke(new BasicStroke((hoveringTile || lastSelected) ? 0.6f : 0.25f));
                        GG.drawRect(tileRectangle.x, tileRectangle.y, tileRectangle.width, tileRectangle.height);
                    }
                }
            }
        }

        tools.Begin(g, new Vector2(800, 100), new Vector2(400, 100));
        tools.Name("Tools");

        if (this.currentLayer != null && Game.IsMouseDown(MouseEvent.BUTTON1)) {
            if (this.currentTool == "Paint") {
                if (this.sslSelectedKeyTile != null) {
                    this.map.SetTileAtWorldPosition(Game.worldMousePos, this.sslSelectedKeyTile.Clone(), this.currentLayer);
                }
            } else if (this.currentTool == "Random Selection") {
                if (this.sslSelection.size() > 1) {
                    int randomIndex = (int)(Math.random() * this.sslSelection.size());
                    Tile randomTile = this.sslSelection.get(randomIndex);
                    this.map.SetTileAtWorldPosition(Game.worldMousePos, randomTile, this.currentLayer);
                }
            } else if (this.currentTool == "Erase") {
                this.map.ClearTileAtWorldPosition(Game.worldMousePos, this.currentLayer);
            }
        }

        tools.ListBegin("Tools", new Vector2(), new Vector2(1.0, 1.0));
            tools.FlowLayoutBegin();

            for (String tool : toolButtons) {
                String text = tool;

                Rectangle buttonDims = tools.CalculateButtonDims(text, new Vector2(), new Vector2());
                Vector2 buttonPosition = tools.FlowLayoutAdd(new Vector2(buttonDims.width, buttonDims.height));
                
                tools.nextButtonHighlight = tool == this.currentTool;
                tools.nextButtonAbsPos = true;
                if (tools.Button(text, buttonPosition, new Vector2(buttonDims.width, buttonDims.height))) {
                    // Its been clicked
                    this.currentTool = tool;
                }
            }

            tools.FlowLayoutEnd();
        tools.ListEnd();

        tools.End();

        layers.Begin(g, new Vector2(800, 250), new Vector2(300, 300));
        
        layers.Name("Layers");
        
        layers.EntryBegin("Layers");
        if (layers.EntryButton("New")) {
            TileMapLayer newLayer = new TileMapLayer(map, map.width, map.height);
            
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
            
            
        sheetsPanel.Begin(g, new Vector2(1100, 500), new Vector2(300, 300));
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

            if (Vector2.AABBContainsPoint(sheetsPanel.currentListTopLeft, listSize, Game.worldMousePos)) {
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

                boolean hovering = Vector2.AABBContainsPoint(pos, boxSize, Game.worldMousePos);
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
            sheetsPanel.nextButtonDisabled = (this.currentSelectedSheet == null || this.sslVisible == true);
            if (sheetsPanel.Button("Open", position, buttonSize)) {
                this.sslReset();
                this.sslIsNew = false;
                this.sslSheet = this.currentSelectedSheet;
                this.sslLeftPanelOpen = false;
                this.sslVisible = true;
            }

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