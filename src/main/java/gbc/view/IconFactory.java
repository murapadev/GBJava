package gbc.view;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import org.kordamp.ikonli.swing.FontIcon;

final class IconFactory {
    private IconFactory() {
    }

    static Icon open() {
        return icon(MaterialDesignF.FOLDER_OPEN, 16);
    }

    static Icon recent() {
        return icon(MaterialDesignH.HISTORY, 16);
    }

    static Icon save() {
        return icon(MaterialDesignC.CONTENT_SAVE, 16);
    }

    static Icon load() {
        return icon(MaterialDesignF.FOLDER_DOWNLOAD, 16);
    }

    static Icon screenshot() {
        return icon(MaterialDesignC.CAMERA, 16);
    }

    static Icon exit() {
        return icon(MaterialDesignL.LOGOUT, 16);
    }

    static Icon pause() {
        return icon(MaterialDesignP.PAUSE, 16);
    }

    static Icon play() {
        return icon(MaterialDesignP.PLAY, 16);
    }

    static Icon reset() {
        return icon(MaterialDesignR.RESTART, 16);
    }

    static Icon apply() {
        return icon(MaterialDesignC.CHECK, 16);
    }

    static Icon close() {
        return icon(MaterialDesignC.CLOSE, 16);
    }

    static Icon restore() {
        return icon(MaterialDesignR.RESTORE, 16);
    }

    static Icon step() {
        return icon(MaterialDesignS.STEP_FORWARD, 16);
    }

    static Icon target() {
        return icon(MaterialDesignT.TARGET, 16);
    }

    static Icon fullscreen() {
        return icon(MaterialDesignF.FULLSCREEN, 16);
    }

    static Icon settings() {
        return icon(MaterialDesignC.COG, 16);
    }

    static Icon debug() {
        return icon(MaterialDesignB.BUG, 16);
    }

    static Icon help() {
        return icon(MaterialDesignH.HELP_CIRCLE_OUTLINE, 16);
    }

    private static Icon icon(Ikon glyph, int size) {
        FontIcon icon = FontIcon.of(glyph, size);
        return icon;
    }

    static Icon badge(Icon base, Color color) {
        int w = base.getIconWidth();
        int h = base.getIconHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        base.paintIcon(null, g2, 0, 0);
        g2.setColor(color);
        int r = Math.max(3, Math.min(w, h) / 5);
        g2.fillOval(w - r - 1, h - r - 1, r, r);
        g2.dispose();
        return new ImageIcon(img);
    }
}
