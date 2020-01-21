# rocket-playground

## Introduction
TODO

## Environment
1. Arch Linux
`pacman -S mill`
`pakku -S riscv-openocd-git riscv64-linux-gnu-binutils riscv64-linux-gnu-gcc riscv64-linux-gnu-gdb`

2. Others
TODO

## Flow
1. RTL build:
```
mill -i playground.tests.test "playground.tests.play.arty"
```

2. Vivado Flow
This flow didn't use FPGA shell, thus need vivado GUI for integration.

2.1 RocketChip IP Generation

2.2 IP Integration

2.3 Bitstream Generation

3. JTAG debug flow

3.1 openocd

3.2 riscv64-linux-gnu-gdb

4. Run OpenSBI 