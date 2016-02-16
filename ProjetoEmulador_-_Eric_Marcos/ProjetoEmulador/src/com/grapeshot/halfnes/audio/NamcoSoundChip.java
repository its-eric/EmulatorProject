/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.grapeshot.halfnes.audio;

import com.grapeshot.halfnes.utils;

/**
 *
 * @author Andrew
 */
public class NamcoSoundChip implements ExpansionSoundChip {

    private int[] registers = new int[128], out = new int[8];
    private int numch, cycpos = 0, curch = 0;

    @Override
    public void clock(int cycles) {
        numch = 1 + ((registers[127] >> 4) & 7);
        for (int i = 0; i < cycles; ++i) {
            cycpos = ++cycpos % 15;
            if (cycpos == 0) {
                curch = ++curch % numch;
                clock_channel(curch);
            }
        }
    }

    private void clock_channel(final int ch) {
        //get channel register start position
        final int off = 0x80 - (8 * (ch + 1));
        //get phase/freq value
        int phase = (registers[off + 5] << 16) + (registers[off + 3] << 8) + registers[off + 1];
        final int f = ((registers[off + 4] & 3) << 16) + (registers[off + 2] << 8) + registers[off];
        //get waveform length
        final int len = (64 - (registers[off + 4] >> 2)) * 4;
        //get waveform start position
        final int wavestart = registers[off + 6];
        //get volume
        phase = (phase + f) % (len << 16);
        final int volume = registers[off + 7] & 0xf;
        final int output = (getWavefromRAM(((phase >> 16) + wavestart) & 0xff) - 8) * volume;
        //store phase back
        registers[off + 5] = (phase >> 16) & 0xff;
        registers[off + 3] = (phase >> 8) & 0xff;
        registers[off + 1] = phase & 0xff;
        out[ch] = output * 16;
        output(output * 128);
    }

    private int getWavefromRAM(final int addr) {
        final int b = registers[(addr) >> 1];
        int r = utils.getbit(addr, 0) ? b >> 4 : b & 0xf;
        return r;
    }

    @Override
    public void write(int register, int data) {
        //System.err.println(numch);
        registers[register] = data;
    }

    public int read(int register) {
        return registers[register];
    }

    @Override
    public int getval() {
        return hpaccum << 2;
    }
    int hpaccum = 0;

    private void output(int sample) {
        //this low pass filter is here to reduce noise in games using 8 channels
        //while still letting me output 1 after the other like the real chip does
                sample += hpaccum;
        hpaccum -= sample * (1/16.);
    }
}
