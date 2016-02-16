/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.grapeshot.halfnes;

import java.awt.image.*;
import java.util.HashMap;

/**
 *
 * @author Andrew
 */
public class NTSCRenderer extends Renderer {

    private int offset = 0;
    private int scanline = 0;
    private static final boolean VHS = false;
    //hm, if I downsampled these perfectly to 4Fsc i could get rid of matrix decode
    //altogether...
    private final static byte[][] colorphases = {
        {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},//0x00
        {0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1},//0x01
        {0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0},//0x02
        {0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0},//0x03
        {0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0},//0x04
        {0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0},//0x05
        {0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0},//0x06
        {1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0},//0x07
        {1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1},//0x08
        {1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1},//0x09
        {1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1},//0x0A
        {1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1},//0x0B
        {1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1},//0x0C
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},//0x0D
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},//0x0E
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}};//0x0F
    private final static double[][][] lumas = genlumas();
    private final static int[][] coloremph = {
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        {1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1},//X
        {0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0},//Y
        {1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1},//XY
        {1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1},//Z
        {1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 1},//XZ
        {1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1},//YZ
        {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}};//XYZ
    //private final static double sync = -0.359f;
    private int frames = 0;
    private final double[] i_filter = new double[12], q_filter = new double[12];
    final double[] sample = new double[2728];
    private final static int[] colortbl = genColorCorrectTbl();
//    Oscilloscope o = new Oscilloscope();

    public NTSCRenderer() {
        int hue = -80;
        double col_adjust = 1.2 / .707;
        for (int j = 0; j < 12; ++j) {
            double angle = Math.PI * ((hue + (j << 8)) / (12 * 128.0) - 33.0 / 180);
            i_filter[j] = -col_adjust * Math.cos(angle);
            q_filter[j] = col_adjust * Math.sin(angle);
        }

    }

    public static int[] genColorCorrectTbl() {
        int[] corr = new int[256];
        //double gamma = 1.2;
        double brightness = 20;
        double contrast = 1;
        for (int i = 0; i < 256; ++i) {
            double br = (i * contrast - (128 * contrast) + 128 + brightness) / 255.;
            corr[i] = clamp((int) (255 * Math.pow(br, 1.3)));
            //convert tv gamma image (~2.2-2.5) to computer gamma (~1.8)
        }
        return corr;
    }

    public static double[][][] genlumas() {
        double[][] lumas = {
            {-0.117f, 0.000f, 0.308f, 0.715f},
            //0x00    0x10    0x20    0x30
            {0.397f, 0.681f, 1.0f, 1.0f}
        };
        double[][][] premultlumas = new double[lumas.length][lumas[0].length][2];
        for (int i = 0; i < lumas.length; ++i) {
            for (int j = 0; j < lumas[i].length; ++j) {
                premultlumas[i][j][0] = lumas[i][j];
                premultlumas[i][j][1] = lumas[i][j] * 0.735;
            }
        }
        return premultlumas;
    }

    public final double[] ntsc_encode(int[] nescolors, int pxloffset, int bgcolor, boolean dotcrawl) {
        //part one of the process. creates a 2728 pxl array of doubles representing
        //ntsc version of scanline passed to it. Meant to be called 240x a frame

        //todo:
        //-make this encode an entire frame at a time
        //-reduce # of array lookups (precalc. what is necessary)

        //first of all, increment scanline numbers and get the offset for this line.
        ++scanline;
        if (scanline > 239) {
            scanline = 0;
            ++frames;
            offset = ((frames & 1) == 0 && dotcrawl) ? 6 : 0;
        }
        offset = (offset + 4) % 12; //3 line dot crawl
        //offset = (offset + 6) % 12; //2 line dot crawl it couldve had
        int i, col, level, emphasis;
        //luminance portion of nes color is bits 4-6, chrominance part is bits 1-3
        //they are both used as the index into various tables
        //the chroma generator chops between 2 different voltages from luma table 
        //at a constant rate but shifted phase.

        //sync and front porch are not actually used by decoder so not implemented here
        //dot 0-200:sync
        //dot 200-232:black
        //dot 232-352:colorburst
        //dot 352-400:black       
        //dot 400-520 and 2568-2656: background color
        col = bgcolor & 0xf;
        level = (bgcolor >> 4) & 3;
        emphasis = (bgcolor >> 6);
        for (i = 400; i < 520; ++i) {
            final int phase = (i + offset) % 12;
            sample[i] = lumas[colorphases[col][phase]][level][coloremph[emphasis][phase]];
        }
        for (i = 2568; i < 2656; ++i) {
            final int phase = (i + offset) % 12;
            sample[i] = lumas[colorphases[col][phase]][level][coloremph[emphasis][phase]];
        }
        //dot 520-2568:picture
        for (i = 520; i < 2568; ++i) {
            if ((i & 7) == 0) {
                col = nescolors[(((i - 520) >> 3)) + pxloffset];
                if ((col & 0xf) > 0xd) {
                    col = 0x0f;
                }
                level = (col >> 4) & 3;
                emphasis = (col >> 6);
                col &= 0xf;
            }
            final int phase = (i + offset) % 12;
            sample[i] = lumas[colorphases[col][phase]][level][coloremph[emphasis][phase]];
        }
        //dot 2656-2720:black
        return sample;
    }
    public final static double chroma_filterfreq = 3579000., pixel_rate = 42950000.;
    private final static int coldelay = 12;

    public final void ntsc_decode(final double[] ntsc, final int[] frame, int frameoff) {


        double[] chroma;
        final double[] luma = new double[2728];
        final double[] eye = new double[2728];
        final double[] queue = new double[2728];
        //decodes one scan line of ntsc video and outputs as rgb packed in int
        //uses the cheap TV method, which is filtering the chroma from the luma w/o
        //combing or buffering previous lines

        box_filter(ntsc, luma, 12);
        highpass_filter(ntsc);
        chroma = ntsc;
        int cbst;
        //find color burst
        switch (offset) {
            case 10:
                cbst = 242;
                break;
            case 2:
                cbst = 250;
                break;
            case 6:
                cbst = 246;
                break;
            case 4:
                cbst = 248;
                break;
            case 8:
                cbst = 244;
                break;
            case 0:
            default:
                cbst = 240;
                break;
        }
//        for (cbst = 240; cbst < 260; ++cbst) {
//            if (chroma[cbst] >= 0.4) {
//                break;
//            }
//        }
        int x = 492;
        int j = 0;
        for (int i = (cbst - coldelay); i < 2620; ++i, ++j, ++cbst, j %= 12) {
            //matrix decode the color diff signals;
            eye[i] = i_filter[j] * chroma[cbst];
            queue[i] = q_filter[j] * chroma[cbst];
        }

        lowpass_filter(eye, 0.04);
        lowpass_filter(queue, 0.03);
        //random picture jitter of 1 subpixel. helps surprisingly much with
        //color banding in dark blue (which itself happens because of the
        //chroma filters)
        //x += (int) (Math.random() * 4 - 2);
        for (int i = 0; i < frame_w; ++i, ++x) {
            frame[i + frameoff] = compose_col(
                    ((luma[x] <= 0) ? 0 : colortbl[clamp((int) (iqm[0][0] * luma[x] + iqm[0][1] * eye[x] + iqm[0][2] * queue[x]))]),
                    ((luma[x] <= 0) ? 0 : colortbl[clamp((int) (iqm[1][0] * luma[x] + iqm[1][1] * eye[x] + iqm[1][2] * queue[x]))]),
                    ((luma[x] <= 0) ? 0 : colortbl[clamp((int) (iqm[2][0] * luma[x] + iqm[2][1] * eye[x] + iqm[2][2] * queue[x]))]));
        }
    }

    private static int compose_col(int r, int g, int b) {
        return (r << 16) | (g << 8) | (b) | 0xff000000;
    }
    //private final static int[][] iqm = {{255, -249, 159}, {255, 70, -166}, {255, 283, 436}};
    private final static int[][] iqm = {{255, -244, 158}, {255, 69, -165}, {255, 282, 434}};

    public static int clamp(final int a) {
        return (a != (a & 0xff)) ? ((a < 0) ? 0 : 255) : a;
    }
    public final static int frame_w = 704 * 3;
    int[] out = new int[frame_w];
    int[] frame = new int[frame_w * 240];
    Kernel kernel = new Kernel(5, 1,
            new float[]{-.2f, -.3f, 2f, -.3f, -.2f});
    BufferedImageOp op = new ConvolveOp(kernel);

    @Override
    public BufferedImage render(int[] nespixels, int[] bgcolors, boolean dotcrawl) {
        for (int line = 0; line < 240; ++line) {
            ntsc_decode(ntsc_encode(nespixels, line * 256, bgcolors[line], dotcrawl), frame, line * frame_w);
        }
        BufferedImage i = getImageFromArray(frame, frame_w * 8, frame_w, 224);
        // = op.filter(i, null); //sharpen
        //o.flushFrame(false);
        return i;
    }
    double[] xv = new double[5];
    double[] yv = new double[5];
    private final static double[] a = {2.143505E-4, 8.566037E-4,
        1.284906E-4, 8.566037E-4, 9.726342E-4},
            b = {3.425455, -4.479272, 2.643718, -5.933269E-1};
    double hold = 0;

    public final void ch_filter(final double[] filter_in, final double[] filter_out) {
        //does a 4 pole chebychev filter with r = 0.05
        //that's a 2.14 mhz lowpass. this may be a little TOO much blur.
        //try boosting later but for now this gets rid of almost all the chroma.
        for (int i = 358; i < 2656; ++i) {
            filter_out[i] = a[0] * filter_in[i]
                    + a[1] * filter_in[i - 1]
                    + a[2] * filter_in[i - 2]
                    + a[3] * filter_in[i - 3]
                    + a[4] * filter_in[i - 4]
                    + b[0] * filter_out[i - 1]
                    + b[1] * filter_out[i - 2]
                    + b[2] * filter_out[i - 3]
                    + b[3] * filter_out[i - 4];
        }
    }

    public final void box_filter(final double[] in, final double[] out, final int order) {
        double accum = 0;
        for (int i = 358; i < 2656; ++i) {
            accum += in[i] - in[i - order];
            out[i] = accum / order;
        }
    }

    public final void lowpass_filter(final double[] arr, final double order) {
        double b = 0;
        for (int i = 358; i < 2656; ++i) {
            arr[i] -= b;
            b += arr[i] * order;
            arr[i] = b;
        }
//        if (scanline == 160) {
//            for (int i = 600; i < 2568; ++i) {
//                o.outputSample((int) (arr[i] * 16384));
//            }
//        }
    }

    public final void highpass_filter(final double[] arr) {
        double b = 0;
        for (int i = 358; i < 2656; ++i) {
            arr[i] += b;
            b -= arr[i] * .5;
        }

    }
}
