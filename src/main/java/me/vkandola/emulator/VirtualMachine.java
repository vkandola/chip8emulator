package me.vkandola.emulator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * The encapsulation of the Chip8 machine state (registers, memory, flags, etc), and program execution.
 */
public class VirtualMachine {
    // Memory layout
    private static final int MEMORY_SIZE = 0x1000; // 4096/4K bytes
    // Memory region used to store fonts, 
    private static final int MEMORY_PROGRAM_START = 0x200; // 512 Bytes
    // Rest of memory is working memory of ROM and the ROM itself.
    private byte[] MEMORY = new byte[MEMORY_SIZE];

    // State of the 16 possible key strokes
    private static final int NUM_KEYS = 16;
    private boolean KEY[] = new boolean[NUM_KEYS];

    // Timers for sound and delay
    private byte soundTimer;
    private byte delayTimer;

    // Screen dimensions in local pixels
    public static final int SCREEN_WIDTH = 64;
    public static final int SCREEN_HEIGHT = 32;
    // Monochrome frame buffer for the screen
    private boolean SCREEN_BUFFER[] = new boolean[SCREEN_WIDTH * SCREEN_HEIGHT];

    // Registers from V0 to VF, 8-bit data registers, VF serves as flag register for some instructions
    private static final int NUMBER_REGISTERS = 16;
    private static final int FLAG_REGISTER = 0xF;
    private byte V[] = new byte[NUMBER_REGISTERS];
    // Single 16-bit address register, for memory I/O
    private short I;
    // Single 16-bit program counter, for fetching instructions
    private short PC = MEMORY_PROGRAM_START; // Starts executing first instruction of rom
    // Single 16-bit opcode, set each fetch cycle
    private short OPCODE;

    // Stack and stack pointer
    private static final int STACK_SIZE = 16;
    private short STACK[] = new short[STACK_SIZE];
    private short SP; // Pointer into the stack

    // Random generator
    private static final int RANDOM_SEED = 7;
    private Random random = new Random(RANDOM_SEED);

    // Set to true if the graphics buffer changed.
    private boolean drawPending;

    // Cycle count
    private int cycleCount = 0;

    private Runner runner;

    // Fontset for, taken from http://devernay.free.fr/hacks/chip8/C8TECH10.HTM#font
    private static final int FONT_HEIGHT = 5;
    private short FONT[] = { // Short instead of byte b/c Java uses signed bytes.
            0xF0, 0x90, 0x90, 0x90, 0xF0, // 0
            0x20, 0x60, 0x20, 0x20, 0x70, // 1
            0xF0, 0x10, 0xF0, 0x80, 0xF0, // 2
            0xF0, 0x10, 0xF0, 0x10, 0xF0, // 3
            0x90, 0x90, 0xF0, 0x10, 0x10, // 4
            0xF0, 0x80, 0xF0, 0x10, 0xF0, // 5
            0xF0, 0x80, 0xF0, 0x90, 0xF0, // 6
            0xF0, 0x10, 0x20, 0x40, 0x40, // 7
            0xF0, 0x90, 0xF0, 0x90, 0xF0, // 8
            0xF0, 0x90, 0xF0, 0x10, 0xF0, // 9
            0xF0, 0x90, 0xF0, 0x90, 0x90, // A
            0xE0, 0x90, 0xE0, 0x90, 0xE0, // B
            0xF0, 0x80, 0x80, 0x80, 0xF0, // C
            0xE0, 0x90, 0x90, 0x90, 0xE0, // D
            0xF0, 0x80, 0xF0, 0x80, 0xF0, // E
            0xF0, 0x80, 0xF0, 0x80, 0x80  // F
    };
    private static final int FONT_SIZE = 80; // 16 fonts with 5 rows per font.

    public VirtualMachine() {
        loadFont();
    }

    private void loadFont() {
        for (int i = 0; i < FONT_SIZE; i++) {
            MEMORY[i] = (byte) FONT[i];
        }
    }

    /**
     * Loads the chip 8 ROM into the interpreter's memory
     *
     * @param filepath The file system path of the ROM to load
     * @throws IOException Thrown if failed to find the ROM
     */
    public void loadROM(String filepath) throws IOException {
        Path path = Paths.get(filepath);
        byte[] romBytes = Files.readAllBytes(path);
        if ((MEMORY_SIZE - MEMORY_PROGRAM_START) < romBytes.length) {
            System.out.printf("ROM is too big to fit into memory, %s=%d\n", path.toString(), romBytes.length);
        } else {
            System.arraycopy(romBytes, 0, MEMORY, MEMORY_PROGRAM_START, romBytes.length);
            System.out.printf("ROM copied successfully, %s=%d\n", path.toString(), romBytes.length);
        }

    }

    private void clearScreen() {
        for (int i = 0; i < SCREEN_HEIGHT; i++) {
            for (int j = 0; j < SCREEN_WIDTH; j++) {
                SCREEN_BUFFER[i * SCREEN_HEIGHT + j] = false;
            }
        }
        drawPending = true;
    }

    private byte draw(int X, int Y, int N) {
        boolean clearedPixel = false;
        for (int i = 0; i < N; i++) {
            byte data = MEMORY[I + i];
            for (int j = 0; j < 8; j++) {
                int pixelX = X + j;
                int pixelY = Y + i;
                if (pixelX >= SCREEN_WIDTH || pixelX < 0) {
                    pixelX = pixelX % SCREEN_WIDTH;
                }
                if (pixelY >= SCREEN_HEIGHT || pixelY < 0) {
                    pixelY = pixelY % SCREEN_HEIGHT;
                }
                boolean oldvalue = SCREEN_BUFFER[pixelX + SCREEN_WIDTH * pixelY];
                boolean drawValue = ((data & (0x80 >> j)) >> (7 - j)) == 1;
                boolean newValue = oldvalue ^ drawValue;
                if (oldvalue & !newValue) {
                    clearedPixel = true;
                }
                SCREEN_BUFFER[pixelX + SCREEN_WIDTH * pixelY] = newValue;
            }
            // TODO When drawing things that overflow the screen edges, they should wrap around!

        }

        drawPending = true;
        return (byte) ((clearedPixel) ? 0x1 : 0x0);
    }

    public void cycle() {
        // Fetch
        OPCODE = 0x0;
        OPCODE |= MEMORY[PC];
        OPCODE <<= 8;
        OPCODE |= MEMORY[PC + 1] & 0xFF;
        short NEXT_PC = (short) (PC + 2);
        //System.out.printf("[Debug] OPCODE: %04x\t PC: %x \n", OPCODE, PC);


        // Decode and Execute
        switch (OPCODE & 0xF000) {
            case (0x0000): {
                switch (OPCODE & 0x0FFF) {
                    case (0x00E0): {
                        clearScreen();
                    }
                    break;
                    case (0x00EE): {
                        NEXT_PC = STACK[SP--];
                    }
                    break;
                    default:
                        System.out.printf("[Decode] Unimplemented opcode=%04x\n", OPCODE);
                }
            }
            break;
            case (0x1000):
                NEXT_PC = (short) (OPCODE & 0x0FFF);
                break;
            case (0x2000):
                STACK[++SP] = NEXT_PC;
                NEXT_PC = (short) (OPCODE & 0x0FFF);
                break;
            case (0x3000):
                if (V[(OPCODE & 0x0F00) >> 8] == (OPCODE & 0x00FF)) {
                    NEXT_PC += 2;
                }
                break;
            case (0x4000):
                if (V[(OPCODE & 0x0F00) >> 8] != (OPCODE & 0x00FF)) {
                    NEXT_PC += 2;
                }
                break;
            case (0x5000):
                if (V[(OPCODE & 0x0F00) >> 8] == V[(OPCODE & 0x00F0) >> 4]) {
                    NEXT_PC += 2;
                }
                break;
            case (0x6000):
                V[(OPCODE & 0x0F00) >> 8] = (byte) (OPCODE & 0x00FF);
                break;
            case (0x7000):
                V[(OPCODE & 0x0F00) >> 8] += (byte) (OPCODE & 0x00FF);
                break;
            case (0x8000): {
                int X = (OPCODE & 0x0F00) >> 8;
                int Y = (OPCODE & 0x00F0) >> 4;
                switch (OPCODE & 0x000F) {
                    case (0x0): {
                        V[X] = V[Y];
                    }
                    break;
                    case (0x1): {
                        V[X] = (byte) (V[X] | V[Y]);
                    }
                    break;
                    case (0x2): {
                        V[X] = (byte) (V[X] & V[Y]);
                    }
                    break;
                    case (0x3): {
                        V[X] = (byte) (V[X] ^ V[Y]);
                    }
                    break;
                    case (0x4): {
                        int sum = V[X] + V[Y];
                        V[FLAG_REGISTER] = (byte) (((~0xFFFF & sum) != 0) ? 1 : 0);
                        V[X] = (byte) sum;
                    }
                    break;
                    case (0x5): {
                        int l = V[X] & 0xFFFF;
                        int r = V[Y] & 0xFFFF;
                        V[FLAG_REGISTER] = (byte) ((l < r) ? 1 : 0);
                        V[X] = (byte) (l - r);
                    }
                    break;
                    case (0x6): {
                        int value = V[Y] & 0xFFFF;
                        V[FLAG_REGISTER] = (byte) (((value & 0x0001) == 0x0001) ? 1 : 0);
                        V[Y] = (byte) (value >>> 1);
                        V[X] = V[Y];
                    }
                    break;
                    case (0x7): {
                        int l = V[Y] & 0xFFFF;
                        int r = V[X] & 0xFFFF;
                        V[FLAG_REGISTER] = (byte) ((l < r) ? 1 : 0);
                        V[X] = (byte) (l - r);
                    }
                    break;
                    case (0xE): {
                        int value = V[Y] & 0xFFFF;
                        V[FLAG_REGISTER] = (byte) (((value & 0x8000) == 0x8000) ? 1 : 0);
                        V[Y] = (byte) (value >>> 1);
                        V[X] = V[Y];
                    }
                    break;
                    default:
                        System.out.printf("[Decode] Unimplemented opcode=%04x\n", OPCODE);

                }
            }
            break;
            case (0x9000): {
                int X = (OPCODE & 0x0F00) >> 8;
                int Y = (OPCODE & 0x00F0) >> 4;
                switch (OPCODE & 0x000F) {
                    case (0x0): {
                        if (V[X] != V[Y]) {
                            NEXT_PC += 2;
                        }
                    }
                    break;
                    default:
                        System.out.printf("[Decode] Unimplemented opcode=%04x\n", OPCODE);

                }
            }
            break;
            case (0xA000):
                I = (short) (OPCODE & 0x0FFF);
                break;
            case (0xB000): {
                int base = V[0] & 0xFFFF;
                int offset = OPCODE & 0xFFF;
                NEXT_PC = (short) (base + offset);
            }
            break;
            case (0xC000): {
                int X = (OPCODE & 0x0F00) >> 8;
                int NN = OPCODE & 0x00FF;
                int randomValue = random.nextInt() % 255;
                V[X] = (byte) (randomValue & NN);
            }
            break;
            case (0xD000): {
                int X = (OPCODE & 0x0F00) >> 8;
                int Y = (OPCODE & 0x00F0) >> 4;
                int N = OPCODE & 0x000F;
                V[0xF] = draw(V[X], V[Y], N);
            }
            break;
            case (0xE000): {
                int X = (OPCODE & 0x0F00) >> 8;
                switch (OPCODE & 0x00FF) {
                    case (0x009E): {
                        if (KEY[X]) {
                            NEXT_PC += 2;
                        }
                    }
                    break;
                    case (0x00A1): {
                        if (!KEY[X]) {
                            NEXT_PC += 2;
                        }
                    }
                    break;
                    default:
                        System.out.printf("[Decode] Unimplemented opcode=%04x\n", OPCODE);

                }
            }
            break;
            case (0xF000): {
                int X = (OPCODE & 0x0F00) >> 8;
                switch (OPCODE & 0x00FF) {
                    case (0x0007): {
                        V[X] = delayTimer;
                    }
                    break;
                    case (0x000A): {
                        boolean anyKeyPressed = false;

                        for (int i = 0; i < NUM_KEYS; i++) {
                            if (KEY[i]) {
                                V[X] = (byte) i;
                                anyKeyPressed = true;
                                break;
                            }
                        }
                        if (!anyKeyPressed) {
                            // Block until key is pressed.
                            runner.blockForKey();
                            for (int i = 0; i < NUM_KEYS; i++) {
                                if (KEY[i]) {
                                    V[X] = (byte) i;
                                    break;
                                }
                            }
                        }
                        // Could we just call the gl wait here and change the Runner code to update us?
                    }
                    break;
                    case (0x0015): {
                        delayTimer = V[X];
                    }
                    break;
                    case (0x0018): {
                        soundTimer = V[X];
                    }
                    break;
                    case (0x001E): {
                        I += V[X];
                    }
                    break;
                    case (0x0029): {
                        I = (short) (FONT_HEIGHT * V[X]);
                    }
                    break;
                    case (0x0033): {
                        int number = V[X] & 0xFFFF;
                        for (int d = 100; d >= 1; d /= 10) {
                            MEMORY[I++] = (byte) ((number / d) % 10);
                        }
                    }
                    break;
                    case (0x0055): {
                        for (int i = 0; i < NUMBER_REGISTERS; i++) {
                            MEMORY[I++] = V[i];
                        }
                    }
                    break;
                    case (0x0065): {
                        for (int i = 0; i < NUMBER_REGISTERS; i++) {
                            V[i] = MEMORY[I++];
                        }
                    }
                    break;
                    default:
                        System.out.printf("[Decode] Unimplemented opcode=%04x\n", OPCODE);
                }
            }
            break;
            default:
                System.out.printf("[Decode] Unimplemented opcode=%04x\n", OPCODE);
        }
        PC = NEXT_PC;

        cycleCount++;

        if (soundTimer > 0) {
            soundTimer--;
            if (soundTimer == 0) {
                // TODO: Hook into some audio layer APIs
                System.out.printf("[Sound] Beep!\n");
                java.awt.Toolkit.getDefaultToolkit().beep();
            }
        }
        if (delayTimer > 0) {
            delayTimer--;
        }
    }

    public boolean[] getScreenBuffer() {
        return SCREEN_BUFFER;
    }

    public boolean shouldDraw() {
        return drawPending;
    }

    public void clearDraw() {
        drawPending = false;
    }

    public void setKeys(boolean[] fromKeys) {
        for (int i = 0; i < fromKeys.length; i++) {
            KEY[i] = fromKeys[i];
        }
    }

    public void setRunner(Runner runner) {
        this.runner = runner;
    }
}
