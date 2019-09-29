package me.vkandola.emulator;

import java.util.HashMap;

import static org.lwjgl.glfw.GLFW.*;

public class KeyMap {
    private static final HashMap<Integer, Integer> GLFWKeyMap = new HashMap<>();

    {
        GLFWKeyMap.put(GLFW_KEY_1, 0x1);
        GLFWKeyMap.put(GLFW_KEY_2, 0x2);
        GLFWKeyMap.put(GLFW_KEY_3, 0x3);
        GLFWKeyMap.put(GLFW_KEY_4, 0xC);

        GLFWKeyMap.put(GLFW_KEY_Q, 0x4);
        GLFWKeyMap.put(GLFW_KEY_W, 0x5);
        GLFWKeyMap.put(GLFW_KEY_E, 0x6);
        GLFWKeyMap.put(GLFW_KEY_R, 0xD);

        GLFWKeyMap.put(GLFW_KEY_A, 0x7);
        GLFWKeyMap.put(GLFW_KEY_S, 0x8);
        GLFWKeyMap.put(GLFW_KEY_D, 0x9);
        GLFWKeyMap.put(GLFW_KEY_F, 0xE);

        GLFWKeyMap.put(GLFW_KEY_Z, 0xA);
        GLFWKeyMap.put(GLFW_KEY_X, 0x0);
        GLFWKeyMap.put(GLFW_KEY_C, 0xB);
        GLFWKeyMap.put(GLFW_KEY_V, 0xF);
    }


    public static Integer convertKey(Integer GLFWKey) {
        return GLFWKeyMap.get(GLFWKey);
    }
}
