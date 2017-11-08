package emulator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.Files.readAllBytes;

/**
 * The encapsulation of the Chip8 machine state, registers, etc.
 *
 */
public class VirtualMachine {
    // Memory layout
    private static final int MEMORY_SIZE = 4096; // 4096/4K bytes
    // Memory region used to store fonts, 
    private static final int MEMORY_PROGRAM_START = 512; // 512 Bytes
    // Rest of memory is working memory of ROM and the ROM itself.
    private byte[] memory = new byte[MEMORY_SIZE];

    // State of the 16 possible key strokes
    private byte key[] = new byte[16];

    // Timers for sound and delay
    private byte soundTimer;
    private byte delayTimer;

    // Screen dimensions in pixels
    private static final int SCREEN_WIDTH = 64;
    private static final int SCREEN_HEIGHT = 32;
    // Monochrome frame buffer for the screen
    private byte screenBuffer[] = new byte[SCREEN_WIDTH * SCREEN_HEIGHT];

    // 16 registers from V0 to VF, 8-bit data registers, VF serves as flag register for some instructions
    private byte V[] = new byte[16];
    // Single 16-bit address register, for memory I/O
    private short I;
    // Single 16-bit program counter, for fetching instructions
    private short PC = 512; // Starts executing first instruction of rom
    // Single 16-bit opcode, set each fetch cycle
    private short OPCODE;

    public VirtualMachine() {
    }

    public void loadROM(String filepath) throws Exception {
        Path path = Paths.get(filepath);
        byte[] romBytes = Files.readAllBytes(path);
        if ((MEMORY_SIZE - MEMORY_PROGRAM_START) < romBytes.length) {
            System.out.printf("ROM is too big to fit into memory, %s=%d\n", path.toString(), romBytes.length);
        } else {
            System.arraycopy(romBytes, 0, memory, MEMORY_PROGRAM_START, romBytes.length);
            System.out.printf("ROM copied successfully, %s=%d\n", path.toString(), romBytes.length);
        }

    }

    public void cycle() {

    }
}
