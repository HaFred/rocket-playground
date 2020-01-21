#!/bin/bash

cd opensbi
git clean -fdx
make CROSS_COMPILE=riscv64-linux-gnu- PLATFORM=../../opensbi_platform
cp opensbi_platform/firmware/fw_jump.elf ../