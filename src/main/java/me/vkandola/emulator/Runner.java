package me.vkandola.emulator;

import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Runner {
    private static final String ROM_PATH = "./roms/PUZZLE";
    private static final int WORLD_PIXELS_PER_LOCAL = 16;
    private int width = WORLD_PIXELS_PER_LOCAL * VirtualMachine.SCREEN_WIDTH;
    private int height = WORLD_PIXELS_PER_LOCAL * VirtualMachine.SCREEN_HEIGHT;

    private VirtualMachine vm = new VirtualMachine();
    private long windowHandle;
    private static final boolean KEYS[] = new boolean[16];

    public void run() throws Exception {
        System.out.println("Hello LWJGL " + Version.getVersion() + "!");

        init();
        loop();

        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);

        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() throws Exception {

        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        windowHandle = glfwCreateWindow(width, height, "Chip 8 Emulator", NULL, NULL);
        if (windowHandle == NULL)
            throw new RuntimeException("Failed to create the GLFW windowHandle");

        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            Integer keyNum = KeyMap.convertKey(key);
            if (keyNum != null) {
                if (action == GLFW_RELEASE) {
                    KEYS[keyNum] = false;
                } else if (action == GLFW_PRESS) {
                    KEYS[keyNum] = true;
                }
            }
        });

        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);

            glfwGetWindowSize(windowHandle, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            glfwSetWindowPos(
                    windowHandle,
                    (vidmode.width() - pWidth.get(0)) / 2,
                    (vidmode.height() - pHeight.get(0)) / 2
            );
        }

        glfwMakeContextCurrent(windowHandle);
        glfwSwapInterval(1);

        glfwShowWindow(windowHandle);

        vm.loadROM(ROM_PATH);

        vm.setRunner(this);
    }

    private void loop() throws Exception {
        GL.createCapabilities();

        glClearColor(1.0f, 0.0f, 0.0f, 0.0f);


        glMatrixMode(GL_PROJECTION);
        glOrtho(0, width, height, 0, 1, -1);
        glMatrixMode(GL_MODELVIEW);


        // Run the rendering loop until the user has attempted to close
        // the windowHandle or has pressed the ESCAPE key.
        int runs = 0;
        while (!glfwWindowShouldClose(windowHandle)) {
            if (vm.shouldDraw()) {
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

                // Set black background
                glBegin(GL_QUADS);
                glColor3ub((byte) 155, (byte) 155, (byte) 155);
                glVertex2i(width, 0);
                glVertex2i(width, height);
                glVertex2i(0, height);
                glVertex2i(0, 0);
                glEnd();

                // Draw pixels from the screen buffer, colored white if set else black
                glBegin(GL_QUADS);
                boolean[] sbuf = vm.getScreenBuffer();
                for (int i = 0; i < vm.SCREEN_WIDTH; i++) {
                    for (int j = 0; j < vm.SCREEN_HEIGHT; j++) {
                        int x = i * WORLD_PIXELS_PER_LOCAL;
                        int y = j * WORLD_PIXELS_PER_LOCAL;
                        boolean flipped = sbuf[i + (j * vm.SCREEN_WIDTH)];
                        if (flipped) {
                            glColor3ub((byte) 255, (byte) 255, (byte) 255);
                        } else {
                            glColor3ub((byte) 0, (byte) 0, (byte) 0);
                        }
                        glVertex2i(WORLD_PIXELS_PER_LOCAL + x, y);
                        glVertex2i(WORLD_PIXELS_PER_LOCAL + x, WORLD_PIXELS_PER_LOCAL + y);
                        glVertex2i(x, WORLD_PIXELS_PER_LOCAL + y);
                        glVertex2i(x, y);
                    }
                }
                glEnd();

                glfwSwapBuffers(windowHandle);

                vm.clearDraw();
            }

            glfwPollEvents();
            //glfwWaitEvents();
            vm.setKeys(KEYS);
            // TODO wait events glfwWaitEvents();
            vm.cycle();
        }
    }

    private boolean anyKeyPressed() {
        for (int i = 0; i < 16 ; i++) {
            if (KEYS[i]) {
                return true;
            }
        }
        return false;
    }

    public void blockForKey() {
        while(!anyKeyPressed()) {
            glfwWaitEvents();
        }
        vm.setKeys(KEYS);
    }

    public static void main(String[] args) {
        Runner runner = new Runner();
        try {
            runner.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
