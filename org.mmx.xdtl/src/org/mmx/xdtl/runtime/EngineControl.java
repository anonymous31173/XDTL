package org.mmx.xdtl.runtime;

import java.util.Map;

import org.mmx.xdtl.model.CommandList;

public interface EngineControl {
    void call(String ref, ArgumentMap args);
    void callExtension(String nsUri, String name, Map<String, Object> args);
    void execute(CommandList commands);
    void exit();

    /**
     * Exits XDTL runtime with given code
     * @param code
     */
    void exit(int code);
}
