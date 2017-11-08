import emulator.VirtualMachine;

public class Main {

    public static void main(String[] args) throws Exception {
        VirtualMachine vm = new VirtualMachine();
        vm.loadROM("./roms/TETRIS");
        vm.cycle();
    }
}
