import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Arrays;

import javax.imageio.ImageIO;
import java.io.*;
import java.awt.image.*;

// Class for loading and managing SpriteSheet. See: https://en.wikipedia.org/wiki/Texture_atlas
class SpriteSheet {
    public String name = "null";
    protected int tileSize;
    
    protected int numTilesX;
    protected int numTilesY;

    protected boolean tilesPurged = false;

    protected BufferedImage image;
    protected VolatileImage GPUImage;

    protected boolean hasAlpha = false;

    protected String imagePath;

    ArrayList<Tile> tiles;

    public BufferedImage GetCPUImage() {
        return this.image;
    }

    public Image GetImage(Graphics2D g) {
        if (this.GPUImage != null && this.VolatileImageNeedsCreation(g)) {
            System.out.println("[LOG]: Recreating volatile image.");
            this.RenderGPUImage();
        }
        
        if (this.GPUImage != null) {
            return this.GPUImage;
        }

        return (Image)this.image;
    }

    public void RenderGPUImage() {
        // Obtain a Graphics2D context for the VolatileImage
        Graphics2D vg = (Graphics2D) this.GPUImage.createGraphics();
    
        vg.setComposite(AlphaComposite.Src);
        // Use SrcOver composite to blend the images properly
        // vg.clearRect(0, 0, this.GPUImage.getWidth(), this.GPUImage.getHeight());

        // Draw the original image onto the VolatileImage
        vg.drawImage(this.image, 0, 0, null);
    
        vg.dispose();
    }    

    public boolean VolatileImageNeedsCreation(Graphics2D g) {
        if (this.GPUImage.contentsLost()) {
            return true;
        }

        return false;
    }

    public void SetHasAlpha(boolean hasAlpha) {
        this.hasAlpha = hasAlpha;
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                      .getDefaultScreenDevice()
                                                      .getDefaultConfiguration();
        this.GPUImage = gc.createCompatibleVolatileImage(this.image.getWidth(), this.image.getHeight(), hasAlpha ? Transparency.TRANSLUCENT : Transparency.OPAQUE);
        this.RenderGPUImage();
    }

    private void Init(BufferedImage image, String imagePath, int tileSize) {
        this.tileSize = tileSize;
        this.image = image;
        this.imagePath = imagePath;

        int imageWidth = this.image.getWidth();
        int imageHeight = this.image.getHeight();

        this.numTilesX = imageWidth / tileSize;
        this.numTilesY = imageHeight / tileSize;

        this.UpdateTilesSize();
        this.SetHasAlpha(false);

        if (this.GPUImage == null) {
            System.err.println("[ERROR]: Error creating volatile image (GPU Image) for sprite sheet: `" + this.name + "`. Expect performance degradations.");
        } else {
            this.RenderGPUImage();
            this.GPUImage.setAccelerationPriority(1.0f);
        }
    
        if (imageWidth % tileSize != 0 || imageHeight % tileSize != 0) {
            System.err.println("[WARN]: Atlas tile size not equally divisible by it's width or height.");
        }
    }
    
    public void LoadFromFile(BufferedReader br, TileMap map) throws IOException {
        String name = TileMap.readString(br, "name");
        String imagePath = TileMap.readString(br, "image_path");
        int tileSize = TileMap.readInt(br, "tile_size");

        String deletedIndicies = TileMap.readString(br, "delted_tiles_indicies");
        int numModifiedTiles = TileMap.readInt(br, "num_modified_tiles");
        
        File f = new File(imagePath);
        BufferedImage loadedImage = ImageIO.read(f);

        System.out.println("Sprite Sheet Name: " + name);
        System.out.println("\tImage Path: " + imagePath);
        System.out.println("\tTile Size: " + tileSize);
        
        this.Init(loadedImage, imagePath, tileSize);
        this.name = name;

        if (deletedIndicies != null) {
            String[] stringIndiciesRaw = deletedIndicies.split(",");
            String[] stringIndicies = Arrays.copyOfRange(stringIndiciesRaw, 0, stringIndiciesRaw.length - 1); // Last one is empty

            for (String indexStr : stringIndicies) {
                int index = -1;
                try {
                    index = Integer.parseInt(indexStr);
                } catch (NumberFormatException e) {
                    System.err.println("[ERROR]: Error in loading tiles, expected index got: " + indexStr);
                    continue;
                } 

                this.ClearTileAtIndex(index);
            }
        }

        for (int i = 0; i < numModifiedTiles; i++) {
            Tile t = new Tile(0, 0, null, -1);
            t.LoadFromFile(br, map);
            t.textureSheet = this;
            this.tiles.set(t.y * this.numTilesX + t.x, t);
        }

        String hasAlpha = TileMap.readString(br, "has_alpha");
        System.out.println("has alpha =" + hasAlpha);
        if (hasAlpha.equals("true")) {
            this.SetHasAlpha(true);
        } else {
            this.SetHasAlpha(false);
        }


        TileMap.GoToEnd(br);
    }
    public void SaveToFile(FileWriter fw, TileMap map) throws IOException {
        fw.write("__SPRITE SHEET__\n");
        fw.write("name=" + this.name + "\n");
        fw.write("image_path=" + this.imagePath + "\n");
        fw.write("tile_size="+this.tileSize+"\n");

        fw.write("delted_tiles_indicies=");

        int modifiedTilesCount = 0;

        for (Tile t : this.tiles) {
            if (!t.IsNull()) {
                if (t.w != 1 || t.h != 1) {
                    modifiedTilesCount ++;
                }
            } else {
                int index = t.y * this.numTilesX + t.x;
                fw.write(index + ",");
            }
        }
        fw.write("\n");

        fw.write("num_modified_tiles=" + modifiedTilesCount + "\n");
        for (int y = 0; y < this.numTilesY; y++) {
            for (int x = 0; x < this.numTilesX; x++) {
                Tile t = this.tiles.get(y * this.numTilesX + x);
                if (!t.IsNull() && (t.w != 1 || t.h != 1)) {
                    t.SaveToFile(fw, map);
                }
            }
        }

        fw.write("has_alpha="+this.hasAlpha+"\n");

        fw.write("END\n");
    }

    public SpriteSheet(BufferedReader br, TileMap map) throws IOException {
        this.LoadFromFile(br, map);
    }

    public SpriteSheet(BufferedImage image, String filePath, int tileSize) {
        Init(image, filePath, tileSize);   
    }

    public SpriteSheet(String filePath, int tileSize) throws IOException {
        File f = new File(filePath);
        BufferedImage loadedImage = ImageIO.read(f);
        this.imagePath = filePath;
        this.name = f.getName();
        Init(loadedImage, filePath, tileSize);
    }

    public void ClearTileAtIndex(int index) {
        if (index < 0) return;
        if (index > this.tiles.size()) return;

        Tile t = this.tiles.get(index);

        t.w = 1;
        t.h = 1;
        t.textureIndex = -1;
        t.textureSheet = null;
    }

    public void PurgeBlankTiles() {
        int removalCount = 0;
        for (Tile t : this.tiles) {
            if (t != null && t.IsBlank()) {
                removalCount ++;
                this.tiles.set(this.tiles.indexOf(t), new Tile(t.x, t.y, this, -1));
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

    public void LoadFromFile(BufferedReader br, TileMap map) throws IOException {
        int x = TileMap.readInt(br, "x");
        int y = TileMap.readInt(br, "y");
        int w = TileMap.readInt(br, "w");
        int h = TileMap.readInt(br, "h");
        int textureIndex = TileMap.readInt(br, "texture_index");
        int spriteSheetIndex = TileMap.readInt(br, "sprite_sheet_index");

        TileMap.GoToEnd(br);
        
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.textureIndex = textureIndex;

        if (spriteSheetIndex >= 0 && spriteSheetIndex < map.ownedSheets.size()) {
            this.textureSheet = map.ownedSheets.get(spriteSheetIndex);
        }
    }

    public void SaveToFile(FileWriter fw, TileMap map) throws IOException {
        fw.write("__TILE__\n");
        fw.write("x=" + this.x + "\n");
        fw.write("y=" + this.y + "\n");
        fw.write("w=" + this.w + "\n");
        fw.write("h=" + this.h + "\n");
        fw.write("texture_index=" + this.textureIndex + "\n");
        if (this.textureSheet != null) {
            int index = map.ownedSheets.indexOf(this.textureSheet);
            if (index == -1) {
                System.err.println("[ERRORRRRRRRR]: " + this);
                // throw new Exception("LA");
            }
            fw.write("sprite_sheet_index=" + index + "\n");    
        } else {
            fw.write("sprite_sheet_index=-1\n");
        }
        fw.write("END\n");
    }

    public Tile Clone() {
        Tile t = new Tile(this.x, this.y, this.textureSheet, this.textureIndex);
        t.w = this.w;
        t.h = this.h;

        return t;
    }

    public Tile(int x, int y, SpriteSheet sheet, int textureIndex) {
        this.x = x;
        this.y = y;
        this.textureSheet = sheet;
        this.textureIndex = textureIndex;
    }

    public void Clear() {
        this.textureSheet = null;
        this.textureIndex = -1;
        this.w = 1;
        this.h = 1;
    }

    public boolean IsNull() {
        return this.textureIndex == -1;
    }

    public boolean IsCompoundTile() {
        return this.w > 1 || this.h > 1;
    }

    public int GetSheetIndex() {
        return this.y * this.textureSheet.numTilesX + this.x;
    }

    public boolean IsBlank() {
        if (this.textureIndex == -1) return false;

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
        if (this.textureIndex == -1 || this.textureSheet == null) return;

        int tileSize = this.textureSheet.tileSize;
        int sx = this.textureIndex % this.textureSheet.numTilesX;
        int sy = this.textureIndex / this.textureSheet.numTilesX;

        g.drawImage(this.textureSheet.GetImage(g),
                    (int)x, (int)y, (int)(x+w), (int)(y+h),
                    sx*tileSize, sy*tileSize, (sx*tileSize)+(tileSize*this.w), (sy*tileSize)+(tileSize*this.h),
                    GG.COLOR_OPAQUE, null);
    }

    public String toString() {
        String sheetName = this.textureSheet != null ? this.textureSheet.name : "NULL"; 
        return "Tile @(" + this.x + ", " + this.y + ") Sheet : { Name: `" + sheetName + "`, Texture Index: " + this.textureIndex + " }";
    }
}

class TileMapLayer {
    public String name = "Layer 1";
    protected TileMap parentMap;
    protected int width, height;
    protected ArrayList<Tile> tiles;

    public void LoadFromFile(BufferedReader br, TileMap map) throws IOException {
        String name = TileMap.readString(br, "name");
        int width = TileMap.readInt(br, "width");
        int height = TileMap.readInt(br, "height");

        int num_tiles = TileMap.readInt(br, "num_tiles");

        this.width = width;
        this.height = height;
        this.name = name;

        this.tiles.clear();
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < height; x++) {
                this.tiles.add(new Tile(x, y, null, -1)); // Add all blank tiles
            }
        }

        for (int i = 0; i < num_tiles; i++) {
            Tile t = new Tile(0, 0, null, -1);
            t.LoadFromFile(br, map);
            this.SetTile(t.x, t.y, t);
        }

        TileMap.GoToEnd(br);
    }

    public void SaveToFile(FileWriter fw, TileMap map) throws IOException {
        fw.write("__LAYER__\n");
        fw.write("name=" + this.name + "\n");
        fw.write("width=" + this.width + "\n");
        fw.write("height=" + this.height + "\n");

        int numNonNullTiles = 0;

        for (Tile t : this.tiles) {
            if (!t.IsNull()) {
                numNonNullTiles++;
            }
        }

        fw.write("num_tiles=" + numNonNullTiles + "\n");
        
        for (Tile t : this.tiles) {
            if (!t.IsNull()) {
                t.SaveToFile(fw, map);
            }
        }

        fw.write("END\n");
    }

    public TileMapLayer(TileMap map, int width, int height) {
        this.parentMap = map;
        this.tiles = new ArrayList<>();

        this.width = width;
        this.height = height;

        for (int y = 0; y < width; y++) {
            for (int x = 0; x < height; x++) {
                this.tiles.add(new Tile(x, y, null, -1)); // Add all blank tiles
            }
        }
    }

    public void SetTile(int x, int y, Tile t) {
        int tileIndex = y * this.parentMap.width + x;

        if (tileIndex < 0) return;
        if (tileIndex > this.tiles.size()-1) return;

        try {
            Tile current = this.tiles.get(tileIndex);
    
            current.w = t.w;
            current.h = t.h;
            current.textureSheet = t.textureSheet;
            current.textureIndex = t.textureIndex;
        } catch (IndexOutOfBoundsException e) {
            System.err.println("[ERROR]: Attempt to set a tile out of bounds. @(" + x + ", " + y +")");
        }

        // this.tiles.set(tileIndex, t);
    }

    public void SetTile(int x, int y, SpriteSheet sheet, int textureIndex) {
        int tileIndex = y * this.parentMap.width + x;

        if (tileIndex < 0) return;
        if (tileIndex > this.tiles.size()) return;

        try {
            Tile current = this.tiles.get(tileIndex);
            current.w = 1;
            current.h = 1;
            current.textureSheet = sheet;
            current.textureIndex = textureIndex;
        } catch (IndexOutOfBoundsException e) {
            System.err.println("[ERROR]: Attempt to set a tile out of bounds. @(" + x + ", " + y +")");
        }
    }

    public Tile GetTile(int x, int y) {
        int tileIndex = y * this.parentMap.width + x;

        if (tileIndex < 0) return null;
        if (tileIndex > this.tiles.size()) return null;
        
        try {
            return this.tiles.get(tileIndex);
        } catch (IndexOutOfBoundsException e) {
            System.err.println("[ERROR]: Attempt to get a tile out of bounds. @(" + x + ", " + y +")");
            return null;
        }
    }
}

class TileMap {
    static double RENDERSCALE = 50.0;

    public Vector2 renderOffset = new Vector2();

    public AffineTransform transform;

    protected int width;
    protected int height;

    protected ArrayList<SpriteSheet> ownedSheets = new ArrayList<>();
    protected ArrayList<TileMapLayer> layers = new ArrayList<>();

    public TileMap(int width, int height) {
        this.width = width;
        this.height = height;

        this.transform = new AffineTransform();
        this.transform.scale(30.0, 30.0);

        this.layers.add(new TileMapLayer(this, width, height));
    }

    public void DeleteSheet(SpriteSheet sheet) {
        for (TileMapLayer layer : this.layers) {
            for (Tile t : layer.tiles) {
                if (t.textureSheet == sheet) {
                    t.Clear();
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

    public Vector2 LocalToWorldVectorScalar(Vector2 worldVector) {
        Vector2 n = new Vector2();
        
        n.x = worldVector.x * TileMap.RENDERSCALE;
        n.y = worldVector.y * TileMap.RENDERSCALE;

        return n;
    }

    public Vector2 LocalToWorldVectorPositional(Vector2 worldVector) {
        Vector2 n = new Vector2();
        
        n.x = worldVector.x * TileMap.RENDERSCALE + this.renderOffset.x;
        n.y = worldVector.y * TileMap.RENDERSCALE + this.renderOffset.y;

        return n;
    }

    public Vector2 WorldToLocalVector(Vector2 localVector) {
        Vector2 n = new Vector2();
        
        n.x = localVector.x / TileMap.RENDERSCALE;
        n.y = localVector.y / TileMap.RENDERSCALE;
        // n = n.ApplyTransformation(this.transform);

        return n;
    }

    public Tile GetTileAtWorldPosition(Vector2 position, TileMapLayer layerMask) {
        position = WorldToLocalVector(position);

        int x = (int)Math.floor(position.x);
        int y = (int)Math.floor(position.y);

        System.out.println(x + " , " + y);

        if (x < 0) return null;
        if (x > this.width) return null;
        if (y < 0) return null;
        if (y > this.height) return null;
        
        if (layerMask != null) {
            return layerMask.GetTile(x, y);
        }

        for (TileMapLayer layer : this.layers) {
            Tile t = layer.GetTile(x, y);
            if (!t.IsNull()) {
                return t;
            }
        }

        return null;
    }

    public void ClearTileAtWorldPosition(Vector2 position, TileMapLayer layerMask) {
        position = WorldToLocalVector(position);

        int x = (int)Math.floor(position.x);
        int y = (int)Math.floor(position.y);

        
        if (x < 0) return;
        if (x > this.width) return;
        if (y < 0) return;
        if (y > this.height) return;

        if (layerMask != null) {
            layerMask.SetTile(x, y, null, -1);
            return;
        }
    }

    public void SetTileAtWorldPosition(Vector2 position, Tile t, TileMapLayer layerMask) {
        position = WorldToLocalVector(position);

        int x = (int)Math.floor(position.x);
        int y = (int)Math.floor(position.y);

        if (x < 0) return;
        if (x >= this.width) return;
        if (y < 0) return;
        if (y > this.height) return;

        if (layerMask != null) {
            layerMask.SetTile(x, y, t);
            return;
        }

        TileMapLayer topMost = this.layers.get(0);
        if (topMost != null) {
            topMost.SetTile(x, y, t);
        }
    } 

    static Color DEBUG_GRID_COLOR = new Color(0x4b0b61);
    public void Draw(Graphics2D g) {
        int drewCount = 0;
        
        // for (int y = 0; y < this.height; y++) {
        //     for (int x = 0; x < this.width; x++) {
        //         Vector2 tilePosition = LocalToWorldVector(new Vector2(x, y));
        //         Vector2 tileSize = LocalToWorldVector(new Vector2(1, 1));
                
        //         g.setColor(DEBUG_GRID_COLOR);
        //         GG.drawRect(tilePosition, tileSize);
        //     }
        // }
        
        for (int i = 0; i < this.layers.size(); i++) {
            TileMapLayer layer = this.layers.get(i);
            for (Tile t : layer.tiles) {
                if (t == null) continue;
                
                drewCount++;

                Vector2 tilePosition = LocalToWorldVectorPositional(new Vector2(t.x, t.y));
                Vector2 tileSize = LocalToWorldVectorScalar(new Vector2(t.w, t.h));
                
                if (!t.IsNull()) {                    
                    // g.setColor(Panel.BUTTON_HILI_BG);                    
                    // g.setStroke(new BasicStroke(2.0f));
                    // GG.drawRect(tilePosition, tileSize);
                    // g.setStroke(new BasicStroke());

                    t.Draw(g, tilePosition.x, tilePosition.y, tileSize.x, tileSize.y);
                }
            }
        }

        // System.out.println("Drew " + drewCount);
    }

    public void Save(String filePath) {
        try {
            File mapF = new File(filePath);
            if (mapF.createNewFile()) {
                System.out.println("[LOG]: Map file created: " + mapF.getAbsolutePath());
            } else {
                System.out.println("[LOG]: Map file already exists: `" + mapF.getAbsolutePath() + "`. Overwriting.");
            }

            FileWriter fw = new FileWriter(mapF);

            fw.write("__MAP__\n");
            fw.write("width=" + this.width + "\n");
            fw.write("height=" + this.height + "\n");

            fw.write("num_owned_sheets=" + this.ownedSheets.size() + "\n");
            for (SpriteSheet sheet : this.ownedSheets) {
                sheet.SaveToFile(fw, this);
            }

            fw.write("num_layers=" + this.layers.size() + "\n");
            for (TileMapLayer l : this.layers) {
                l.SaveToFile(fw, this);
            }

            fw.write("END\n");

            fw.flush();
            fw.close();
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    private static String loaderError = null;

    private static String getNextLine(BufferedReader br)  throws IOException {
        String line = br.readLine();

        if (line == null)  return null;

        while (line.strip().equals("") || line.startsWith("__")) {
            line = br.readLine();
        }

        return line;
    } 

    public static void GoToEnd(BufferedReader br) throws IOException {
        String line = null;
        do {
            String newLine = br.readLine();
            if (newLine == null) {
                loaderError = "Unexpected EOF, Expecting END. `" + line + "`";
                return;
            }
            line = newLine;
        } while (line == null || !line.equals("END"));
    }

    public static int readInt(BufferedReader br, String expected_header) throws IOException {
        String line = getNextLine(br);

        if (line == null) {
            loaderError = "Unexpected EOF.\n";
            return 0;
        }

        String components[] = line.split("=");
        if (components.length < 2) {
            loaderError = "Incomplete statement. Line: `" + line + "`";
            return 0;
        }
        
        String header = components[0];
        if (!header.equals(expected_header)) {
            loaderError = "Expected `" + expected_header + "` got: `" + header + "`";
            return 0;
        }

        String valueStr = components[1];
        for (int i = 2; i < components.length; i++) {
            valueStr += components[i];
        }

        int value = 0;
        try {
            value = Integer.parseInt(valueStr);
        } catch (NumberFormatException e) {
            loaderError = "Expected integer got: " + valueStr + ".\n";
            return 0;
        }

        return value;
    }

    
    public static String readString(BufferedReader br, String expected_header) throws IOException {
        String line = getNextLine(br);

        if (line == null) {
            loaderError = "Unexpected EOF.\n";
            return null;
        }

        String components[] = line.split("=");
        if (components.length < 2) {
            return "";
        }
        
        String header = components[0];
        if (!header.equals(expected_header)) {
            loaderError = "Expected `" + expected_header + "` got: `" + header + "`";
            return null;
        }

        String valueStr = components[1];
        for (int i = 2; i < components.length; i++) {
            valueStr += components[i];
        }

        return valueStr;
    }

    public void LoadFromFile(String filePath) {
        try {
            File mapF = new File(filePath);
            if (!mapF.exists()) {
                System.err.println("[ERROR]: Attempted to load map file that doesn't exist: `" + filePath + "`");
            }

            BufferedReader br = new BufferedReader(new FileReader(mapF));

            int width = readInt(br, "width");
            int height = readInt(br, "height");

            System.out.println("Width:" + width);
            System.out.println("Height:" + height);

            int numOwnedSheets = readInt(br, "num_owned_sheets");

            ArrayList<SpriteSheet> sheets = new ArrayList<>();

            for (int i = 0; i < numOwnedSheets; i++) {
                SpriteSheet s = new SpriteSheet(br, this);
                sheets.add(s);
            }

            
            if (loaderError != null) {
                System.err.println("[ERROR] Map loader error in loading sheets: " + loaderError);
                return;
            } else {
                this.ownedSheets = sheets;
            }

            int numLayers = readInt(br, "num_layers");
            ArrayList<TileMapLayer> layers = new ArrayList<>();
            for (int i = 0; i < numLayers; i++) {
                TileMapLayer tl = new TileMapLayer(this, width, height);
                tl.LoadFromFile(br, this);
                layers.add(tl);
            }
            
            if (loaderError != null) {
                System.err.println("[ERROR] Map loader error in loading layers: " + loaderError);
                return;
            } else {
                this.layers = layers;
            }

            GoToEnd(br);

            br.close();
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }
}