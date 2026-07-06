package com.fushu.mmceoneblock.common.config;

public class MachineConfigException extends RuntimeException {
    public MachineConfigException(String message) {
        super(message);
    }

    public MachineConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
