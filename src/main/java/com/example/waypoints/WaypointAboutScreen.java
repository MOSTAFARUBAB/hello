package com.example.waypoints;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** About page in the shared dark / lime Waypoints workspace. */
public final class WaypointAboutScreen extends Screen {
    private static final int BG = 0xFF0B0C0E;
    private static final int RAIL = 0xFF101113;
    private static final int PANEL = 0xFF131719;
    private static final int EDGE = 0xFF29312B;
    private static final int TEXT = 0xFFF2F5F5;
    private static final int MUTED = 0xFFA3ABB0;
    private static final int ACCENT = 0xFFB7F36B;
    private static final int RAIL_W = 176;

    public WaypointAboutScreen() { super(Component.literal("About Simple Waypoints")); }

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, BG);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawSidebar(g, mx, my);
        int x = RAIL_W + 24;
        WaypointUiText.drawTitle(g, font, "About", x, 28, TEXT);
        WaypointUiText.draw(g, font, "Simple tools for remembering the places that matter.", x, 56, MUTED, false);
        int panelY = 102, panelW = width - x - 28;
        rounded(g, x, panelY, x + panelW, panelY + 210, PANEL, 12);
        border(g, x, panelY, x + panelW, panelY + 210, EDGE);
        rounded(g, x + 22, panelY + 24, x + 70, panelY + 72, ACCENT, 12);
        WaypointUiText.draw(g, font, "◆", x + 35, panelY + 39, 0xFF101410, true);
        WaypointUiText.drawTitle(g, font, "Simple Waypoints Manager", x + 88, panelY + 26, TEXT);
        WaypointUiText.draw(g, font, "Version 1.4.4  ·  Minecraft 26.3", x + 88, panelY + 56, ACCENT, true);
        WaypointUiText.draw(g, font, "Save locations, organize dimensions, and follow your own beacon markers.", x + 22, panelY + 100, TEXT, false);
        WaypointUiText.draw(g, font, "Your waypoint data is stored locally in your Minecraft configuration.", x + 22, panelY + 124, MUTED, false);
        WaypointUiText.draw(g, font, "Includes a custom dashboard, color picker, Chroma mode, map preview and settings.", x + 22, panelY + 148, MUTED, false);
        rounded(g, x, panelY + 230, x + panelW, panelY + 326, PANEL, 10);
        border(g, x, panelY + 230, x + panelW, panelY + 326, EDGE);
        WaypointUiText.draw(g, font, "PROJECT DETAILS", x + 18, panelY + 248, ACCENT, true);
        WaypointUiText.draw(g, font, "Minecraft 26.3", x + 18, panelY + 277, TEXT, false);
        WaypointUiText.draw(g, font, "Fabric Loader 0.19.5", x + 190, panelY + 277, TEXT, false);
        WaypointUiText.draw(g, font, "Java 25", x + 390, panelY + 277, TEXT, false);
        WaypointUiText.draw(g, font, "Loom is configured independently of the Minecraft game version.", x + 18, panelY + 302, MUTED, false);
        super.extractRenderState(g, mx, my, delta);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my) {
        g.fill(0, 0, RAIL_W, height, RAIL);
        g.fill(RAIL_W - 1, 0, RAIL_W, height, EDGE);
        rounded(g, 18, 20, 53, 55, ACCENT, 10);
        WaypointUiText.draw(g, font, "◆", 28, 28, 0xFF101410, true);
        WaypointUiText.draw(g, font, "Simple", 69, 22, TEXT, true);
        WaypointUiText.draw(g, font, "Waypoints", 69, 36, TEXT, true);
        nav(g, mx, my, 112, "Waypoints", false);
        nav(g, mx, my, 157, "Map", false);
        nav(g, mx, my, 202, "Settings", false);
        nav(g, mx, my, 247, "About", true);
        g.fill(14, height - 99, 42, height - 92, 0xFF4F9C35);
        g.fill(14, height - 92, 42, height - 79, 0xFF79502E);
        WaypointUiText.draw(g, font, "v1.4.4", 54, height - 91, ACCENT, true);
    }

    private void nav(GuiGraphicsExtractor g, int mx, int my, int y, String label, boolean active) {
        int x=10,w=156; boolean hover=mx>=x&&mx<x+w&&my>=y&&my<y+36;
        if(active||hover) rounded(g,x,y,x+w,y+36,active?0xFF283B1C:PANEL,7);
        int c=active?ACCENT:MUTED;
        if (label.equals("Waypoints")) drawPinGlyph(g,x+13,y+10,c);
        else if (label.equals("Map")) drawCubeGlyph(g,x+12,y+10,c);
        else if (label.equals("Settings")) drawSlidersGlyph(g,x+11,y+10,c);
        else drawInfoGlyph(g,x+11,y+10,c);
        WaypointUiText.draw(g,font,label,x+38,y+11,active?ACCENT:MUTED,false);
    }
    private void drawPinGlyph(GuiGraphicsExtractor g,int x,int y,int c){g.fill(x+4,y,x+8,y+2,c);g.fill(x+2,y+2,x+10,y+6,c);g.fill(x+3,y+6,x+9,y+9,c);g.fill(x+5,y+9,x+7,y+12,c);g.fill(x+4,y+3,x+8,y+6,RAIL);}
    private void drawCubeGlyph(GuiGraphicsExtractor g,int x,int y,int c){g.fill(x+5,y,x+6,y+1,c);g.fill(x+2,y+2,x+9,y+3,c);g.fill(x,y+4,x+2,y+6,c);g.fill(x+9,y+4,x+11,y+6,c);g.fill(x+2,y+7,x+9,y+8,c);g.fill(x+5,y+3,x+6,y+8,c);}
    private void drawSlidersGlyph(GuiGraphicsExtractor g,int x,int y,int c){g.fill(x,y+2,x+13,y+3,c);g.fill(x,y+7,x+13,y+8,c);g.fill(x,y+12,x+13,y+13,c);g.fill(x+3,y,x+6,y+5,c);g.fill(x+8,y+5,x+11,y+10,c);}
    private void drawInfoGlyph(GuiGraphicsExtractor g,int x,int y,int c){g.fill(x+2,y,x+11,y+1,c);g.fill(x,y+2,x+1,y+11,c);g.fill(x+12,y+2,x+13,y+11,c);g.fill(x+2,y+12,x+11,y+13,c);g.fill(x+6,y+3,x+7,y+4,c);g.fill(x+6,y+6,x+7,y+10,c);}
    private static void rounded(GuiGraphicsExtractor g,int x,int y,int x2,int y2,int color,int r){int w=x2-x,h=y2-y;if(w<=0||h<=0)return;r=Math.min(WaypointsMod.smoothUiEdges?Math.max(r,8):Math.min(r,3),Math.min(w/2,h/2));g.fill(x+r,y,x2-r,y2,color);g.fill(x,y+r,x2,y2-r,color);for(int row=0;row<r;row++){int inset=(int)Math.ceil(r-Math.sqrt(Math.max(0,r*r-Math.pow(r-row-.5,2))));g.fill(x+inset,y+row,x2-inset,y+row+1,color);g.fill(x+inset,y2-row-1,x2-inset,y2-row,color);}}
    private static void border(GuiGraphicsExtractor g,int x,int y,int x2,int y2,int color){g.fill(x,y,x2,y+1,color);g.fill(x,y2-1,x2,y2,color);g.fill(x,y,x+1,y2,color);g.fill(x2-1,y,x2,y2,color);}

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if(event.button()==1&&event.x()<=RAIL_W){
            if(event.y()>=112&&event.y()<148){Minecraft.getInstance().gui.setScreen(new WaypointScreen(0));return true;}
            if(event.y()>=157&&event.y()<193){Minecraft.getInstance().gui.setScreen(new WaypointMapScreen());return true;}
            if(event.y()>=202&&event.y()<238){Minecraft.getInstance().gui.setScreen(new WaypointSettingsScreen());return true;}
        }
        return super.mouseClicked(event,doubleClick);
    }
}
