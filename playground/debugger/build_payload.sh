#!/bin/bash

cd opensbi
git clean -fdx
make CROSS_COMPILE=riscv64-linux-gnu- PLATFORM=../../opensbi_platform FW_PAYLOAD=y FW_PAYLOAD_PATH=../Image
cp opensbi_platform/firmware/fw_payload.elf ../
