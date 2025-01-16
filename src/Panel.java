
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.HashMap;

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

    private static String inputContext = null;
    public static int inputMaxSize = 128;
    private static boolean inputOpen = false;
    private static boolean inputJustClosed = false;
    protected static String inputInput = null;
    
    public static void Draw(Graphics2D g) {
        if (Panel.inputOpen) {
            if (!Game.inputBlockers.contains("PanelInputField"))
                Game.inputBlockers.add("PanelInputField");
            
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRect(0, 0, Game.WINDOW_WIDTH, Game.WINDOW_HEIGHT);
            
            Rectangle inputRectangle = new Rectangle();
            inputRectangle.width = Game.WINDOW_WIDTH/4 + 200;
            inputRectangle.height = 80;
            
            inputRectangle.x = (int)(Game.WINDOW_WIDTH/2.0 - inputRectangle.width/2.0);
            inputRectangle.y = (int)(Game.WINDOW_HEIGHT/2.0 - inputRectangle.height/2.0);
            
            g.setColor(new Color(24, 24, 24));
            GG.fillRect(inputRectangle);
            
            g.setColor(Color.WHITE);

            g.setFont(TileMapEditor.ED_FONT);
            FontMetrics fm = g.getFontMetrics();

            int y = (int)(inputRectangle.y + Panel.PADDING + fm.getHeight());
            int textWidth = fm.stringWidth(Game.textInputBuffer);

            if (Game.textInputBuffer.length() >= inputMaxSize) {
                Game.textInputBuffer = Game.textInputBuffer.substring(0, inputMaxSize);
            }

            g.drawString(inputContext, (int)(inputRectangle.x + Panel.PADDING), y);
            y += 2*Panel.PADDING;
            
            g.setColor(new Color(48, 48, 48));
            g.fillRect(inputRectangle.x, y, inputRectangle.width, fm.getHeight()*2);

            g.setColor(new Color(200, 200, 200));
            g.drawString(Game.textInputBuffer, (int)(inputRectangle.x + Panel.PADDING), (int)(y + fm.getHeight()*1.25));

            y += fm.getHeight()*1.25;

            int value = (int)((Math.sin(10*Game.now())+1)*0.5*255);
            int x = (int)(inputRectangle.x + PADDING + textWidth);

            g.setColor(new Color(255, 255, 255, value));
            g.drawLine(x, y+4, x, y - fm.getHeight()+4);

            if (Game.keysDown[KeyEvent.VK_ENTER] == true || Game.keysDown[KeyEvent.VK_ESCAPE] == true) {
                System.out.println("Closing!");
                Panel.inputContext = null;
                inputInput = Game.textInputBuffer + ""; // Make a duplicate?  ¯\_(ツ)_/¯
                Panel.inputOpen = false;
                Panel.inputJustClosed = true;
            }
        } else {
            Game.inputBlockers.remove("PanelInputField");
        }
    }

    public static boolean InputField(String context, String initialText) {
        if (Panel.inputJustClosed) {
            Panel.inputJustClosed = false;
            return true;
        }

        if (Panel.inputContext == null) {
            Panel.inputContext = context;
            Panel.inputJustClosed = false;
            Panel.inputInput = null;
            Panel.inputOpen = true;
            Game.textInputBuffer = initialText != null ? initialText : "";
        }
        
        return false;
    }

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
            g.setClip(null);
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

        Vector2 dotPosition = new Vector2(sliderPos.x + sliderSize.x*currentPercent - dotSize/2.0, sliderPos.y + sliderThickness/2.0 - dotSize/2.0);

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
            size.x = (this.size.x - 2*PADDING) * size.x;
        }
        if (size.y >= -1.0 && size.y <= 1.0) {
            size.y = (this.remainingVerticalSpace() - PADDING) * size.y;
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
            g.setClip(null);
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

    public Rectangle CalculateButtonDims(String text, Vector2 position, Vector2 size) {
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

        return new Rectangle((int)position.x, (int)position.y, (int)size.x, (int)size.y);
    }

    protected Vector2 lastButtonSize;
    public boolean Button(String text, Vector2 position, Vector2 size) {
        if (text == null) text = "N/A";

        Rectangle dims = CalculateButtonDims(text, position, size);

        position.x = dims.x;
        position.y = dims.y;
        size.x = dims.width;
        size.y = dims.height;

        g.setFont(TileMapEditor.ED_FONT);

        FontMetrics m = g.getFontMetrics();
        int textWidth = m.stringWidth(text);
    
        boolean inactive = this.nextButtonDisabled;
        boolean highlight = this.nextButtonHighlight;
        boolean hovering = !this.disabled && (!inactive && Vector2.AABBContainsPoint(position, size, Game.mousePos));
        boolean clicked = Game.IsMouseReleased(MouseEvent.BUTTON1);

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

