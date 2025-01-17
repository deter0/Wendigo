import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

class GFXManager {
    protected HashMap<String, Tile> loadedGFXS = null;
    private ArrayList<GFX> activeGFXS = new ArrayList<>();

    public GFXManager() {
        String[] essentialGfxsNames = {"smoke_cloud", "gfx_eye_of_rah", "real_rah", "gfx_star_spin"}; 
        if (this.loadedGFXS == null) {
            this.loadedGFXS = new HashMap<>();

            for (String gfxName : essentialGfxsNames) {
                Tile gfx = Game.currentMap.GetSheetTileByTag(gfxName);

                if (gfx == null) {
                    new Message("Couldn't load GFX: " + gfxName);
                } else {
                    System.out.println("[LOG]: Loaded gfx: " + gfxName);
                    this.loadedGFXS.put(gfxName, gfx);
                }
            }
        }
    }

    public void Draw(Graphics2D g) {
        Iterator<GFX> iterator = this.activeGFXS.iterator();
        while (iterator.hasNext()) {
            
            GFX gfx = iterator.next();
            if (gfx.tile.animPlayedCount > 1) {
                System.out.println("Removed");
                iterator.remove();
                continue;
            }
            
            if (gfx.tile.tags.contains("real_rah")) {
                System.out.println("real rah!");
                gfx.Draw(g);
            } else {
                Game.currentMap.RenderResponsibly(gfx);
            }

            // gfx.tile.Draw(g, gfx.position.x, gfx.position.y, gfxSize.x, gfxSize.y);   
        }
    }

    private Tile GetGFX(String gfx) {
        if (this.loadedGFXS == null) {
            return null;
        }

        return this.loadedGFXS.get(gfx);
    }

    private Vector2 GetGFXSize(Tile gfxTile) {
        if (gfxTile == null || Game.currentMap == null) return new Vector2();

        return Game.currentMap.LocalToWorldVectorScalar(new Vector2(gfxTile.w, gfxTile.h));
    }

    private Vector2 GetGFXSize(String gfxName) {
        Tile gfx = GetGFX(gfxName);
        return GetGFXSize(gfx);
    }

    public void PlayGFXOnce(String gfxName, Vector2 position, double speed) {
        Tile gfx = GetGFX(gfxName);
        Vector2 gfxSize = GetGFXSize(gfxName);

        if (gfx == null || gfxSize == null) {
            return;
        }

        gfx = gfx.Clone();

        gfx.animFPS *= speed;

        position = position.sub(gfxSize.scale(0.5));
        gfx.ResetAnimation();

        activeGFXS.add(new GFX(gfx, position));
    }
}

public class GFX extends GameObject {
    Tile tile;

    public GFX(Tile gfxTile, Vector2 position) {
        super();
        this.tile = gfxTile;
        this.position = position;
    }

    public void Draw(Graphics2D g) {
        if (Game.currentMap == null) return;

        Vector2 gfxSize = Game.currentMap.LocalToWorldVectorScalar(new Vector2(tile.w, tile.h));
        tile.Draw(g, position.x, position.y, gfxSize.x, gfxSize.y);
    }
}