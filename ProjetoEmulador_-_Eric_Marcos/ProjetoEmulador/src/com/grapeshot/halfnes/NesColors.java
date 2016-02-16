/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.grapeshot.halfnes;

/**
 *
 * @author Andrew
 */
public class NesColors {

    private final static double att = 0.7;
    public final static int[][] col = GetNESColors();

    private static int[][] GetNESColors() {
        //just or's all the colors with opaque alpha and does the color emphasis calcs
        int[] colorarray = {0x757575, 0x271B8F, 0x0000AB,
            0x47009F, 0x8F0077, 0xAB0013, 0xA70000, 0x7F0B00, 0x432F00,
            0x004700, 0x005100, 0x003F17, 0x1B3F5F, 0x000000, 0x000000,
            0x000000, 0xBCBCBC, 0x0073EF, 0x233BEF, 0x8300F3, 0xBF00BF,
            0xE7005B, 0xDB2B00, 0xCB4F0F, 0x8B7300, 0x009700, 0x00AB00,
            0x00933B, 0x00838B, 0x000000, 0x000000, 0x000000, 0xFFFFFF,
            0x3FBFFF, 0x5F97FF, 0xA78BFD, 0xF77BFF, 0xFF77B7, 0xFF7763,
            0xFF9B3B, 0xF3BF3F, 0x83D313, 0x4FDF4B, 0x58F898, 0x00EBDB,
            0x444444, 0x000000, 0x000000, 0xFFFFFF, 0xABE7FF, 0xC7D7FF,
            0xD7CBFF, 0xFFC7FF, 0xFFC7DB, 0xFFBFB3, 0xFFDBAB, 0xFFE7A3,
            0xE3FFA3, 0xABF3BF, 0xB3FFCF, 0x9FFFF3, 0xaaaaaa, 0x000000,
            0x000000};
        for (int i = 0; i < colorarray.length; ++i) {
            colorarray[i] |= 0xff000000;
        }
        int[][] colors = new int[8][colorarray.length];
        for (int j = 0; j < colorarray.length; ++j) {
            int col = colorarray[j];
            int r = r(col);
            int b = b(col);
            int g = g(col);
            colors[0][j] = col;
            //emphasize red
            colors[1][j] = compose_col(r, g * att, b * att);
            //emphasize green
            colors[2][j] = compose_col(r * att, g, b * att);
            //emphasize yellow
            colors[3][j] = compose_col(r, g, b * att);
            //emphasize blue
            colors[4][j] = compose_col(r * att, g * att, b);
            //emphasize purple
            colors[5][j] = compose_col(r, g * att, b);
            //emphasize cyan?
            colors[6][j] = compose_col(r * att, g, b);
            //de-emph all 3 colors
            colors[7][j] = compose_col(r * att, g * att, b * att);

        }
        return colors;
    }

    private static int r(int col) {
        return (col >> 16) & 0xff;
    }

    private static int g(int col) {
        return (col >> 8) & 0xff;
    }

    private static int b(int col) {
        return col & 0xff;
    }

    private static int compose_col(double r, double g, double b) {
        return (((int) r & 0xff) << 16) + (((int) g & 0xff) << 8) + ((int) b & 0xff) + 0xff000000;
    }
}
